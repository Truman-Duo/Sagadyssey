package com.jgeted.sagadyssey.npc.faction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 声望衰减处理器。
 * <p>
 * 如果玩家连续 N 个游戏日（默认 20 日）没有与某阵营产生任何互动，
 * 声望逐渐向 defaultStanding 方向漂移，每日 1 点。
 * <p>
 * 互动定义（任一种重置衰减计时器）：
 * <ul>
 *   <li>与该阵营 NPC 交易</li>
 *   <li>击杀该阵营 NPC 或其敌对阵营 NPC</li>
 *   <li>完成该阵营的结构/营地任务</li>
 *   <li>赎罪捐赠（针对该阵营）</li>
 *   <li>解救该阵营 NPC</li>
 *   <li>在该阵营 NPC 32 格范围内停留超过 30 秒</li>
 * </ul>
 * <p>
 * 衰减终点为该阵营的 defaultStanding，但不会跨越 NEUTRAL 的远端边界：
 * 对 defaultStanding≥0 的阵营不漂入 COLD；对 defaultStanding≤0 的阵营继续漂移。
 */
public class StandingDecayHandler {

    /** 不互动多少个游戏日后开始衰减（默认 20 日） */
    private static final int DECAY_GRACE_DAYS = 20;
    private static final long DECAY_GRACE_TICKS = DECAY_GRACE_DAYS * 24000L;

    /** 每 24000 tick（一个游戏日）检查一次 */
    private long lastProcessedDay = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long dayTime = server.overworld().getDayTime();
        long currentDay = dayTime / 24000;

        // 每个游戏日仅处理一次
        if (currentDay == lastProcessedDay) return;
        lastProcessedDay = currentDay;

        // 遍历所有在线玩家
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            processPlayerDecay(player, dayTime);
        }
    }

    private void processPlayerDecay(ServerPlayer player, long gameTime) {
        FactionStandings standings = FactionAttachments.getStandings(player);

        // 遍历所有阵营（而非仅 tracked），确保新玩家的默认声望也会衰减
        for (Faction faction : FactionRegistry.getAllFactions()) {
            if (faction == null || !faction.decayEnabled()) continue;

            String factionId = faction.id();
            long lastInteraction = standings.getLastInteractionTick(factionId);
            if (lastInteraction == 0) {
                // 从未互动过，使用当前时间作为起点
                standings.recordInteraction(factionId, gameTime);
                continue;
            }

            // 检查是否已超过宽限期
            long elapsed = gameTime - lastInteraction;
            if (elapsed < DECAY_GRACE_TICKS) continue;

            int currentValue = standings.getValue(factionId);
            int targetValue = faction.decayTarget();

            if (currentValue == targetValue) continue;

            // 计算漂移方向：向 defaultStanding 靠近
            int driftDir = targetValue > currentValue ? 1 : -1;
            int newValue = currentValue + driftDir * faction.decayRatePerDay();

            // NEUTRAL 边界保护：对 defaultStanding≥0 的阵营，不漂入 COLD
            if (faction.defaultStanding() >= 0 && newValue <= StandingLevel.COLD.getMinValue()) {
                continue;
            }

            // 不漂过 targetValue
            if (driftDir > 0 && newValue > targetValue) newValue = targetValue;
            if (driftDir < 0 && newValue < targetValue) newValue = targetValue;

            newValue = StandingModifier.clampStanding(newValue);

            if (newValue != currentValue) {
                StandingLevel oldLevel = StandingLevel.fromValue(currentValue);
                StandingLevel newLevel = StandingLevel.fromValue(newValue);

                standings.setValue(faction, newValue);

                // 触发等级变化事件
                if (oldLevel != newLevel) {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new StandingLevelChangeEvent(player, faction, oldLevel, newLevel));
                }
            }
        }
    }
}
