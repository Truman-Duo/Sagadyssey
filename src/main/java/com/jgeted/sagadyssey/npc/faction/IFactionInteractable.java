package com.jgeted.sagadyssey.npc.faction;

/**
 * NPC 可交互标记接口。
 * <p>
 * 实现此接口的 NPC 可以被阵营系统识别为精英或 Boss，
 * 击杀时给予不同的声望惩罚。
 *
 * @see NpcFactionEvents#onLivingDeath
 */
public interface IFactionInteractable {

    /**
     * NPC 类型。
     */
    enum NpcTier {
        /** 普通 NPC，击杀扣 -10 声望 */
        NORMAL(10),
        /** 精英 NPC，击杀扣 -15 声望 */
        ELITE(15),
        /** Boss NPC，击杀扣 -20 声望 */
        BOSS(20);

        private final int standingPenalty;

        NpcTier(int standingPenalty) {
            this.standingPenalty = standingPenalty;
        }

        /** 击杀该类型 NPC 时扣除的声望值（负数） */
        public int getStandingPenalty() {
            return -standingPenalty;
        }
    }

    /**
     * 获取该 NPC 的类型等级。
     *
     * @return NPC 类型，默认 {@link NpcTier#NORMAL}
     */
    default NpcTier getNpcTier() {
        return NpcTier.NORMAL;
    }
}
