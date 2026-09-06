package com.jgeted.sagadyssey.npc.faction;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.faction.network.FactionStandingsUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 声望变化计算引擎。
 * <p>
 * 实现设计文档 §4.3 的八步流程，处理声望修改、涟漪传播、
 * 每日上限检查、以及 NeoForge 事件的触发。
 * <p>
 * 同时包含误伤宽容机制（§6.3）：首次非致命攻击在 5 秒窗口内仅扣 1 点。
 */
public final class StandingModifier {

    private static final int MIN_STANDING = -100;
    private static final int MAX_STANDING = 100;
    private static final int DEFAULT_RIPPLE_CAP = 25;

    // === 误伤宽容机制 ===
    /** 玩家UUID → (阵营ID → 上次攻击tick) */
    private static final Map<UUID, Map<String, Long>> mercyWindow = new HashMap<>();
    /** 宽容窗口时长（tick），5 秒 */
    private static final long MERCY_WINDOW_TICKS = 100L;
    /** 非致命伤害阈值：伤害 < 目标最大生命值 20% */
    private static final float MERCY_DAMAGE_THRESHOLD = 0.20f;

    private StandingModifier() {}

    /**
     * 应用声望修改（公开入口）。
     *
     * @param player     目标玩家
     * @param faction    目标阵营
     * @param rawDelta   原始变化量（整数）
     * @param reasonKey  变化原因的可翻译 key
     */
    public static void applyModification(ServerPlayer player, Faction faction,
                                          int rawDelta, String reasonKey) {
        if (player == null || faction == null || rawDelta == 0) return;

        // 步骤 1-2：应用 faction 的 reputationMultiplier
        float multiplier = faction.reputationMultiplier();
        int delta = Math.round(rawDelta * multiplier);

        // 步骤 3：触发 StandingModifyEvent（可取消）
        StandingModifyEvent modifyEvent = new StandingModifyEvent(player, faction, delta, reasonKey);
        NeoForge.EVENT_BUS.post(modifyEvent);
        if (modifyEvent.isCanceled()) return;
        delta = modifyEvent.getDelta();

        // 步骤 4-6 委托给内部方法
        applyInternal(player, faction, delta, reasonKey, false);
    }

    /**
     * 内部应用逻辑（即 §4.3 的步骤 4-8）。
     * isRipple=true 时不触发涟漪传播，避免无限递归。
     */
    private static void applyInternal(ServerPlayer player, Faction faction,
                                       int delta, String reasonKey, boolean isRipple) {
        String factionId = faction.id();
        FactionStandings standings = FactionAttachments.getStandings(player);
        int oldValue = standings.getValue(factionId);
        int newValue = Math.max(MIN_STANDING, Math.min(MAX_STANDING, oldValue + delta));

        if (oldValue == newValue) return;

        standings.setValue(faction, newValue);

        // 步骤 5-6：判定 level 变化，触发 StandingLevelChangeEvent
        StandingLevel oldLevel = StandingLevel.fromValue(oldValue);
        StandingLevel newLevel = StandingLevel.fromValue(newValue);
        if (oldLevel != newLevel) {
            NeoForge.EVENT_BUS.post(new StandingLevelChangeEvent(player, faction, oldLevel, newLevel));
        }

        // 步骤 8：触发 StandingChangedEvent + 同步
        NeoForge.EVENT_BUS.post(new StandingChangedEvent(player, faction, oldValue, newValue, reasonKey));

        // 增量同步到客户端（含涟漪变化列表）
        List<FactionStandingsUpdatePayload.AffectedFaction> rippleList = isRipple ? List.of() : applyRipple(player, faction, delta);
        PacketDistributor.sendToPlayer(player,
                new FactionStandingsUpdatePayload(factionId, newValue, newLevel, reasonKey, rippleList));
    }

    /**
     * 应用涟漪传播。每日上限 25 点。
     * @return 受涟漪影响的阵营变化列表（供网络同步）
     */
    public static List<FactionStandingsUpdatePayload.AffectedFaction> applyRipple(
            ServerPlayer player, Faction sourceFaction, int sourceDelta) {
        List<FactionStandingsUpdatePayload.AffectedFaction> results = new ArrayList<>();

        if (player == null || sourceFaction == null || sourceDelta == 0) return results;

        FactionRelationMatrix matrix = FactionRelationMatrix.getInstance();
        List<String> affectedIds = matrix.getAffectedFactionIds(sourceFaction.id());
        if (affectedIds.isEmpty()) return results;

        FactionStandings standings = FactionAttachments.getStandings(player);

        for (String targetId : affectedIds) {
            if (standings.isRippleCapped(targetId, DEFAULT_RIPPLE_CAP)) continue;

            Faction targetFaction = FactionRegistry.get(targetId);
            if (targetFaction == null) continue;

            InterFactionRelation relation = matrix.getRelation(sourceFaction.id(), targetId);
            int rippleDelta = Math.round(sourceDelta * relation.getTransferRate());
            if (rippleDelta == 0) continue;

            // 限制不超过当日上限
            int alreadyReceived = standings.getDailyRippleReceived(targetId);
            int remaining = DEFAULT_RIPPLE_CAP - alreadyReceived;
            if (rippleDelta > 0 && rippleDelta > remaining) {
                rippleDelta = remaining;
            }
            if (rippleDelta == 0) continue;

            int oldValue = standings.getValue(targetId);
            // isRipple=true，不触发二级传播
            applyInternal(player, targetFaction, rippleDelta, "standing.reason.ripple", true);
            standings.addDailyRippleReceived(targetId, rippleDelta);
            int newValue = standings.getValue(targetId);

            results.add(new FactionStandingsUpdatePayload.AffectedFaction(
                    targetId, rippleDelta, newValue, StandingLevel.fromValue(newValue)));
        }

        return results;
    }

    /**
     * 检查误伤宽容。
     * 首次非致命攻击（伤害 < 目标最大生命值 20%）在 5 秒内仅扣 1 点作为警告。
     *
     * @param playerUUID    攻击者 UUID
     * @param factionId     目标 NPC 阵营
     * @param fullDelta     正常应扣声望值（负数）
     * @param damageAmount  实际造成的伤害量（不是攻击者血量）
     * @param targetMaxHealth 目标 NPC 最大生命值
     * @param gameTime      当前游戏时间（tick）
     * @return 实际应扣的声望值（1 或 fullDelta）
     */
    public static int applyMercyTolerance(UUID playerUUID, String factionId,
                                           int fullDelta, float damageAmount,
                                           float targetMaxHealth, long gameTime) {
        if (fullDelta >= 0) return fullDelta; // 正向变化不需要宽容

        // 致命一击无视宽容
        if (damageAmount >= targetMaxHealth * MERCY_DAMAGE_THRESHOLD) return fullDelta;

        mercyWindow.putIfAbsent(playerUUID, new HashMap<>());
        Map<String, Long> playerWindow = mercyWindow.get(playerUUID);
        Long lastAttack = playerWindow.get(factionId);

        if (lastAttack == null || gameTime - lastAttack > MERCY_WINDOW_TICKS) {
            // 首次攻击，窗口内扣 1 点
            playerWindow.put(factionId, gameTime);
            return -1;
        }

        // 窗口内再次攻击，宽容失效
        playerWindow.remove(factionId);
        return fullDelta;
    }

    public static int clampStanding(int value) {
        return Math.max(MIN_STANDING, Math.min(MAX_STANDING, value));
    }

    public static int getRippleCap() {
        return DEFAULT_RIPPLE_CAP;
    }

    /**
     * 判定 source 实体是否应视 target 实体为敌。
     * 任一方属于 player 阵营时，以其 owner 的 FactionStandings 代替直接阵营判定。
     *
     * @param source          判定发起方实体
     * @param target          被判定实体
     * @param sourceOwnerUuid source 的 owner（如果 source 是已招募 NPC）
     * @param targetOwnerUuid target 的 owner（如果 target 是已招募 NPC）
     * @return true 如果 source 应该攻击 target
     */
    public static boolean isHostileBetween(
            LivingEntity source, LivingEntity target,
            @Nullable UUID sourceOwnerUuid, @Nullable UUID targetOwnerUuid
    ) {
        // 获取 source 和 target 的阵营
        Faction sourceFaction = (source instanceof NpcBase npc) ? npc.getFaction() : null;
        Faction targetFaction = (target instanceof NpcBase npc) ? npc.getFaction() : null;

        boolean sourceIsPlayer = sourceFaction != null && "sagadyssey:player".equals(sourceFaction.id());
        boolean targetIsPlayer = targetFaction != null && "sagadyssey:player".equals(targetFaction.id());

        // 情况1：双方都是 player 阵营 → v1.0 返回 false（玩家间 NPC 不互相敌对）
        if (sourceIsPlayer && targetIsPlayer) {
            return false;
        }

        // 情况2：仅 source 是 player 阵营 → 查 source.owner 对 target 阵营的声望
        if (sourceIsPlayer) {
            if (sourceOwnerUuid == null || targetFaction == null) return false;
            ServerPlayer owner = getPlayerByUUID(sourceOwnerUuid);
            if (owner == null) return false;
            return FactionAttachments.getStandings(owner).isHostile(targetFaction);
        }

        // 情况3：仅 target 是 player 阵营 → 查 source 阵营 canBeHostile + target.owner 对 source 声望
        if (targetIsPlayer) {
            if (targetOwnerUuid == null || sourceFaction == null) return false;
            if (!sourceFaction.canBeHostile()) return false;
            ServerPlayer targetOwner = getPlayerByUUID(targetOwnerUuid);
            if (targetOwner == null) return false;
            return FactionAttachments.getStandings(targetOwner).isHostile(sourceFaction);
        }

        // 情况4：双方都是普通阵营 → 查关系矩阵
        if (sourceFaction != null && targetFaction != null) {
            FactionRelationMatrix matrix = FactionRelationMatrix.getInstance();
            InterFactionRelation relation = matrix.getRelation(sourceFaction, targetFaction);
            return relation == InterFactionRelation.ENEMY;
        }

        // 非 NpcBase 实体（原版生物等）：不通过阵营判定
        return false;
    }

    /** 通过 UUID 获取在线玩家 */
    @Nullable
    private static ServerPlayer getPlayerByUUID(UUID uuid) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }
}
