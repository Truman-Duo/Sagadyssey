package com.jgeted.sagadyssey.npc.faction.api;

import com.jgeted.sagadyssey.npc.faction.Faction;

/**
 * 可交互实体的阵营接口。
 * <p>
 * 任何具有阵营归属的实体可以实现此接口，
 * 让阵营系统识别其阵营信息。
 */
public interface IFactionInteractable {

    /** 获取所属阵营 */
    Faction getFaction();

    /** 是否为精英（影响声望值：精英 -15） */
    default boolean isElite() {
        return false;
    }

    /** 获取击杀时的声望变化量（普通=-10, 精英=-15, Boss=-20） */
    default int getStandingValueOnKill() {
        if (isElite()) return -15;
        return -10;
    }
}
