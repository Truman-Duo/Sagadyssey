package com.jgeted.sagadyssey.npc.faction;

/**
 * 阵营间关系类型枚举。
 * <p>
 * 定义两个阵营之间的关系，以及声望变化通过该关系传递时的传递率。
 * 传递率表示：玩家与阵营 A 的声望变化 delta，会有多少比例"涟漪传递"给阵营 B。
 *
 * <pre>
 *   ALLY:        盟友   +50%  绿色
 *   FRIENDLY:    友好   +30%  绿色
 *   NEUTRAL:     中立    0%   灰色
 *   DISTRUSTFUL: 猜忌   -20%  橘色
 *   ENEMY:       敌对   -50%  红色
 * </pre>
 */
public enum InterFactionRelation {
    /** 盟友：声望传递率 +50%（如王国 ↔ 教会） */
    ALLY(0.5f),

    /** 友好：声望传递率 +30%（如王国 ↔ 商会） */
    FRIENDLY(0.3f),

    /** 中立：无传递 */
    NEUTRAL(0.0f),

    /** 猜忌：负向传递 -20%（A 的盟友是 B 的敌人） */
    DISTRUSTFUL(-0.2f),

    /** 敌对：负向传递 -50%（如劫掠者 ↔ 所有文明阵营） */
    ENEMY(-0.5f);

    private final float transferRate;

    InterFactionRelation(float transferRate) {
        this.transferRate = transferRate;
    }

    /** 声望传递率。正数表示同向传递，负数表示反向传递。 */
    public float getTransferRate() {
        return transferRate;
    }

    /** 是否存在有效传递（即关系不是 NEUTRAL） */
    public boolean hasEffect() {
        return this != NEUTRAL;
    }
}
