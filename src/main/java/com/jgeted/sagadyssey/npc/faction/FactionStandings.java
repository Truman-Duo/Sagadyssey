package com.jgeted.sagadyssey.npc.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 玩家的阵营声望状态容器。
 * <p>
 * 作为 Attachment 挂载在 ServerPlayer 上，存储玩家与每个阵营的声望值。
 * 使用 String（阵营 ID）作为 key 而非 Faction 对象，避免 /reload 数据包重载后
 * Registry 中的 Faction 对象被替换导致 HashMap 无法匹配旧 key 而丢失所有声望数据。
 *
 * <pre>
 * 核心存储：Map&lt;String, Integer&gt; standings     — 阵营ID → 声望值 [-100, 100]
 * 衰减计时：Map&lt;String, Long&gt; lastInteractionTick — 最后互动 gameTime（不持久化）
 * 涟漪统计：Map&lt;String, Integer&gt; dailyRippleReceived — 今日涟漪累计（不持久化）
 * 等级缓存：Map&lt;String, StandingLevel&gt; levelCache — 衍生缓存（不持久化）
 * </pre>
 */
public class FactionStandings {

    private static final int MIN_STANDING = -100;
    private static final int MAX_STANDING = 100;
    public static final int DEFAULT_RIPPLE_CAP = 25;

    // === 持久化字段 ===
    private final Map<String, Integer> standings = new HashMap<>();

    // === 瞬态字段（不持久化，服务端运行时使用） ===
    private final Map<String, Long> lastInteractionTick = new HashMap<>();
    private final Map<String, Integer> dailyRippleReceived = new HashMap<>();

    /** 已触发劫掠者忠诚惩罚的文明阵营ID集合（不持久化） */
    private final Set<String> loyaltyPenaltyApplied = new HashSet<>();

    public FactionStandings() {}

    // === 查询方法 ===

    /** 获取玩家对某阵营的声望值 */
    public int getValue(Faction faction) {
        if (faction == null) return 0;
        return getValue(faction.id());
    }

    /** 获取玩家对某阵营的声望值（按 ID 字符串） */
    public int getValue(String factionId) {
        if (standings.containsKey(factionId)) {
            return standings.get(factionId);
        }
        // 未知阵营：回退到该阵营的 defaultStanding
        Faction faction = FactionRegistry.get(factionId);
        return faction != null ? faction.defaultStanding() : 0;
    }

    /** 获取声望等级 */
    public StandingLevel getLevel(Faction faction) {
        if (faction == null) return StandingLevel.NEUTRAL;
        return getLevel(faction.id());
    }

    /** 获取声望等级（按 ID，带缓存） */
    public StandingLevel getLevel(String factionId) {
        int value = getValue(factionId);
        return StandingLevel.fromValue(value);
    }

    /** 是否敌对（HATED 等级） */
    public boolean isHostile(Faction faction) {
        return getLevel(faction) == StandingLevel.HATED;
    }

    /** 是否可以与该阵营交易 */
    public boolean canTradeWith(Faction faction) {
        return getLevel(faction).canTrade();
    }

    /** 是否可以从该阵营招募 NPC（需要 REVERED） */
    public boolean canRecruitFrom(Faction faction) {
        return getLevel(faction) == StandingLevel.REVERED;
    }

    /** 获取所有敌对阵营（HATED） */
    public Set<Faction> getHostileFactions() {
        Set<Faction> result = new HashSet<>();
        for (var entry : standings.entrySet()) {
            if (StandingLevel.fromValue(entry.getValue()) == StandingLevel.HATED) {
                Faction f = FactionRegistry.get(entry.getKey());
                if (f != null) result.add(f);
            }
        }
        return result;
    }

    /** 获取所有友善阵营（HONORED 及以上） */
    public Set<Faction> getAlliedFactions() {
        Set<Faction> result = new HashSet<>();
        for (var entry : standings.entrySet()) {
            StandingLevel level = StandingLevel.fromValue(entry.getValue());
            if (level == StandingLevel.HONORED || level == StandingLevel.REVERED) {
                Faction f = FactionRegistry.get(entry.getKey());
                if (f != null) result.add(f);
            }
        }
        return result;
    }

    // === 修改方法 ===

    /**
     * 公开入口：修改玩家对某阵营的声望。
     * <p><b>注意：</b>此方法直接写入 standings map，不触发事件、涟漪传播或网络同步。
     * 正常声望修改应使用 {@link StandingModifier#applyModification}。
     * 此方法仅用于需要绕过事件系统的特殊场景（如内部涟漪传播）。</p>
     *
     * @deprecated 请使用 {@link StandingModifier#applyModification} 进行声望修改，
     *             以确保事件触发、涟漪传播和客户端同步。
     */
    @Deprecated
    public void modify(Faction faction, int delta) {
        modifyInternal(faction, delta, false);
    }

    /**
     * 内部修改方法。仅 StandingModifier 应调用此方法。
     *
     * @param faction  目标阵营
     * @param delta    变化量（正数为增加）
     * @param isRipple 是否为涟漪传播触发（true 时跳过涟漪传播步骤，避免无限递归）
     */
    void modifyInternal(Faction faction, int delta, boolean isRipple) {
        if (faction == null || delta == 0) return;
        String factionId = faction.id();

        // 步骤 1-2（multiplier 应用）由 StandingModifier 处理，此处直接写入最终 delta
        int oldValue = getValue(factionId);
        int newValue = Math.max(MIN_STANDING, Math.min(MAX_STANDING, oldValue + delta));
        standings.put(factionId, newValue);

        // 步骤 5-6：判定 level 是否变化
        StandingLevel oldLevel = StandingLevel.fromValue(oldValue);
        StandingLevel newLevel = StandingLevel.fromValue(newValue);

        // 注意：此方法不触发 StandingLevelChangeEvent（没有 Player 引用）。
        // 需要事件的调用方应使用 StandingModifier.applyModification()。
    }

    /** 强制设置声望值（指令用） */
    public void setValue(Faction faction, int value) {
        if (faction == null) return;
        standings.put(faction.id(), Math.max(MIN_STANDING, Math.min(MAX_STANDING, value)));
    }

    // === 涟漪计数器 ===

    /** 获取某阵营今日已获得的涟漪声望量 */
    public int getDailyRippleReceived(String factionId) {
        return dailyRippleReceived.getOrDefault(factionId, 0);
    }

    /** 累加涟漪声望量 */
    public void addDailyRippleReceived(String factionId, int amount) {
        dailyRippleReceived.merge(factionId, amount, Integer::sum);
    }

    /** 重置所有涟漪计数器（日出时调用） */
    public void resetDailyRippleCounters() {
        dailyRippleReceived.clear();
    }

    /** 检查某阵营是否已超出每日涟漪上限 */
    public boolean isRippleCapped(String factionId, int cap) {
        return getDailyRippleReceived(factionId) >= cap;
    }

    // === 衰减计时 ===

    /** 记录与某阵营的互动时刻（重置衰减计时器） */
    public void recordInteraction(String factionId, long gameTime) {
        lastInteractionTick.put(factionId, gameTime);
    }

    /** 获取与某阵营的最后互动时刻，未记录则返回 0 */
    public long getLastInteractionTick(String factionId) {
        return lastInteractionTick.getOrDefault(factionId, 0L);
    }

    /** 获取所有已记录过互动的阵营 ID 集合 */
    public Set<String> getTrackedFactionIds() {
        return standings.keySet();
    }

    /** 是否已对该文明阵营触发过劫掠者忠诚惩罚 */
    public boolean hasAppliedLoyaltyPenalty(String factionId) {
        return loyaltyPenaltyApplied.contains(factionId);
    }

    /** 标记已对该文明阵营触发劫掠者忠诚惩罚 */
    public void markLoyaltyPenaltyApplied(String factionId) {
        loyaltyPenaltyApplied.add(factionId);
    }

    // === 内部数据访问 ===

    /** 获取底层声望 Map（只读视图） */
    public Map<String, Integer> getStandingsMap() {
        return Map.copyOf(standings);
    }

    // === Codec 序列化 ===

    /** Codec：只序列化 standings Map，其他字段为瞬态 */
    public static final Codec<FactionStandings> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, Codec.INT)
                            .optionalFieldOf("standings", new HashMap<>())
                            .forGetter(fs -> fs.standings)
            ).apply(instance, standings -> {
                FactionStandings fs = new FactionStandings();
                fs.standings.putAll(standings);
                return fs;
            })
    );
}
