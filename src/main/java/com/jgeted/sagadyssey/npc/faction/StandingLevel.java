package com.jgeted.sagadyssey.npc.faction;

/**
 * 声望五级阶梯枚举。
 * <p>
 * 内部声望值范围 [-100, 100]，对外暴露五个离散等级。
 * 每个等级有充足的缓冲区（25~50 点），避免"差 1 点就跨级"的脆弱边界。
 *
 * <pre>
 *   REVERED: 崇拜  ( 75 ~ 100)  ★★★★★
 *   HONORED: 尊敬  ( 25 ~  74)  ★★★★☆
 *   NEUTRAL: 中立  (-24 ~  24)  ★★★☆☆
 *   COLD:    冷淡  (-74 ~ -25)  ★★☆☆☆
 *   HATED:   仇恨  (-100 ~ -75)  ★☆☆☆☆
 * </pre>
 */
public enum StandingLevel {
    REVERED(75, "standing.sagadyssey.revered"),
    HONORED(25, "standing.sagadyssey.honored"),
    NEUTRAL(-24, "standing.sagadyssey.neutral"),
    COLD(-74, "standing.sagadyssey.cold"),
    HATED(-100, "standing.sagadyssey.hated");

    /** 当前等级的最低声望值（闭区间） */
    private final int minValue;

    /** 可翻译的本地化 key */
    private final String translationKey;

    StandingLevel(int minValue, String translationKey) {
        this.minValue = minValue;
        this.translationKey = translationKey;
    }

    public int getMinValue() {
        return minValue;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * 根据声望值确定对应等级。
     * <p>
     * 判定规则（从高到低）：
     * <ul>
     *   <li>≥ 75 → REVERED（崇拜）</li>
     *   <li>≥ 25 → HONORED（尊敬）</li>
     *   <li>≥ -24 → NEUTRAL（中立）</li>
     *   <li>≥ -74 → COLD（冷淡）</li>
     *   <li>其余 → HATED（仇恨）</li>
     * </ul>
     *
     * @param standing 声望值，范围 [-100, 100]
     * @return 对应的声望等级，永不为 null
     */
    public static StandingLevel fromValue(int standing) {
        if (standing >= 75) return REVERED;
        if (standing >= 25) return HONORED;
        if (standing >= -24) return NEUTRAL;
        if (standing >= -74) return COLD;
        return HATED;
    }

    /**
     * 获取该等级的星标数（1~5）。
     * ★ = HATED, ★★ = COLD, ★★★ = NEUTRAL, ★★★★ = HONORED, ★★★★★ = REVERED
     */
    public int getStarCount() {
        return ordinal() + 1;
    }

    /** 是否可以在该等级进行交易（仅 HATED 不可交易） */
    public boolean canTrade() {
        return this != HATED;
    }

    /** 该等级的 NPC 是否会主动攻击玩家 */
    public boolean isHostile() {
        return this == HATED;
    }
}
