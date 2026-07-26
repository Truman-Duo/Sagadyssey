package com.jgeted.sagadyssey.npc.event;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.faction.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阵营声望获取/扣除事件处理器。
 * <p>
 * 处理击杀 NPC 的声望变化、交易声望、阵营专属途径、
 * COLD 恢复路径（赎罪捐赠、解救 NPC）、涟漪每日上限重置。
 */
public class NpcFactionEvents {

    private long lastDayCheckTick = 0;

    /** 玩家 UUID → (目标实体 UUID → 最后造成的伤害量) */
    private static final Map<UUID, Map<UUID, Float>> recentDamage = new ConcurrentHashMap<>();

    /**
     * 缓存玩家对实体造成的实际伤害量（供死亡事件中的误伤宽容使用）。
     */
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity().level().isClientSide) return;

        recentDamage.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(event.getEntity().getUUID(), event.getNewDamage());
    }

    /**
     * 当 NPC（NpcBase）被玩家杀死时，扣除玩家对该 NPC 阵营的声望。
     * 涟漪机制自动处理敌对阵营的正向声望。
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof NpcBase npc)) return;
        if (entity.level().isClientSide) return;

        var source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;

        var faction = npc.getFaction();
        if (faction == null) return;

        // 计算声望变化量：普通=-10, 精英=-15, Boss=-20
        int delta = npc.getNpcTier().getStandingPenalty();

        // 应用误伤宽容（如果适用）
        // 使用缓存的伤害量而非攻击者血量
        long gameTime = entity.level().getGameTime();
        float damageAmount = recentDamage.getOrDefault(player.getUUID(), Map.of())
                .getOrDefault(entity.getUUID(), 0f);
        delta = StandingModifier.applyMercyTolerance(
                player.getUUID(), faction.id(), delta,
                damageAmount,
                entity.getMaxHealth(), gameTime
        );

        // 清理缓存（避免内存泄漏）
        recentDamage.getOrDefault(player.getUUID(), Map.of()).remove(entity.getUUID());

        // 记录互动（重置衰减计时器）
        var standings = FactionAttachments.getStandings(player);
        standings.recordInteraction(faction.id(), gameTime);

        // 应用声望修改（涟漪自动处理敌对阵营正向声望）
        StandingModifier.applyModification(player, faction, delta,
                "standing.reason.killed_npc");
    }

    /**
     * 每日涟漪上限重置：在每个游戏日日出时重置所有在线玩家的涟漪计数器。
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long dayTime = server.overworld().getDayTime();

        // 每个游戏日检查一次（dayTime % 24000 == 0，即日出时刻）
        if (dayTime % 24000 != 0) return;
        if (dayTime == lastDayCheckTick) return;
        lastDayCheckTick = dayTime;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FactionStandings standings = FactionAttachments.getStandings(player);
            standings.resetDailyRippleCounters();
        }
    }

    /**
     * 荒野流民专属：使用床 +5/夜。
     * TODO: PlayerSleepInBedEvent 在本 NeoForge 版本不可用，改用替代方案。
     */
    // @SubscribeEvent
    // public void onPlayerSleep(PlayerSleepInBedEvent event) { ... }

    /**
     * 解救 NPC：右键被拘禁的友好阵营 NPC（在敌对营地中）。
     * TODO: Structure 模块实现后补全——检查 NPC 是否在敌对营地中被拘禁。
     */
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof NpcBase npc)) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // TODO: 检查 NPC 是否处于"被拘禁"状态（需要 Structure 模块提供判定）
        // 如果被拘禁且玩家解救，给予 +15 声望
    }

    /**
     * 劫掠者专属：对文明阵营达到 HONORED 时自动扣 bandit 声望。
     * 通过监听 StandingLevelChangeEvent 实现。
     */
    @SubscribeEvent
    public void onStandingLevelChange(StandingLevelChangeEvent event) {
        // 仅关注到达 HONORED 或更高等级的情况
        if (event.getNewLevel().ordinal() < StandingLevel.HONORED.ordinal()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // 检查目标阵营是否为文明阵营（非 bandit）
        var targetFaction = event.getFaction();
        if (targetFaction == null || "sagadyssey:bandit".equals(targetFaction.id())) return;

        var bandit = FactionRegistry.get("sagadyssey:bandit");
        if (bandit == null) return;

        var standings = FactionAttachments.getStandings(player);
        // 已对该阵营应用过惩罚则跳过（防止降级后再次升级重复触发）
        if (standings.hasAppliedLoyaltyPenalty(targetFaction.id())) return;

        // 标记已应用，无论新旧等级
        standings.markLoyaltyPenaltyApplied(targetFaction.id());

        StandingModifier.applyModification(player, bandit, -15,
                "standing.reason.showed_loyalty_to_civilized");
    }
}
