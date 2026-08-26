package com.jgeted.sagadyssey.npc.ai;

/**
 * AI 行为互斥标记。
 * 每个 goal 在执行前申请所需的 mutex 位，冲突时跳过，防止多个 AI 同时操控 NPC。
 */
public enum AiMutex {
    /** 移动控制（巡逻、回家、跟随、撤退） */
    MOVE,
    /** 攻击行为（近战、远程、盾挡） */
    ATTACK,
    /** 水中行为 */
    SWIM,
    /** 饥饿/进食行为 */
    HUNGRY,
    /** 社交行为（恐慌、求救、指挥官） */
    SOCIAL
}
