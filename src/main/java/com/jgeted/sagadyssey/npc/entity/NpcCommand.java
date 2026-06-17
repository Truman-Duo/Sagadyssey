package com.jgeted.sagadyssey.npc.entity;

/**
 * NPC 行为指令状态。
 */
public enum NpcCommand {
    /** 自由行动（默认） */
    IDLE,
    /** 跟随主人，保持固定距离 */
    FOLLOW,
    /** 原地待命，不移动 */
    STAY
}
