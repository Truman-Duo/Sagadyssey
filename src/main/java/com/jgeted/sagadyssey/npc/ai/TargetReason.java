package com.jgeted.sagadyssey.npc.ai;

/**
 * 目标选择原因，按优先级从高到低排列。
 */
public enum TargetReason {
    /** 主人在被攻击 */
    OWNER_ATTACKED,
    /** 主人在攻击的目标 */
    OWNER_ATTACKING,
    /** 自卫反击 */
    SELF_DEFENSE,
    /** 仇恨候选（faction 事件 / hurt 写入） */
    REVENGE,
    /** 附近敌对生物扫描 */
    NEARBY_HOSTILE
}
