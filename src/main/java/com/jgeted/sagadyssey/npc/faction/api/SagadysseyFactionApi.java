package com.jgeted.sagadyssey.npc.faction.api;

import com.jgeted.sagadyssey.npc.faction.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.Set;

/**
 * Sagadyssey 阵营系统的公开 API。
 * <p>
 * <b>线程约束：所有修改方法必须在服务端主线程调用。</b>
 * 在异步线程调用会抛出 {@link IllegalStateException}。
 * 查询方法可在任意线程调用（只读）。
 */
public interface SagadysseyFactionApi {

    /** 根据 ResourceLocation 获取阵营 */
    Faction getFaction(ResourceLocation id);

    /** 获取所有已注册阵营 */
    Collection<Faction> getAllFactions();

    /** 获取玩家对某阵营的声望值 */
    int getStanding(Player player, Faction faction);

    /** 获取玩家对某阵营的声望等级 */
    StandingLevel getStandingLevel(Player player, Faction faction);

    /** 玩家对某阵营是否敌对 */
    boolean isHostileTo(Player player, Faction faction);

    /**
     * 修改玩家对某阵营的声望值。
     * <b>线程约束：必须在服务端主线程调用。</b>
     *
     * @param player  目标玩家（仅 ServerPlayer）
     * @param faction 目标阵营
     * @param delta   变化量（正数为增加）
     * @param reason  变化原因的可翻译 key
     */
    void modifyStanding(Player player, Faction faction, int delta, String reason);

    /** 强制设置声望值（指令用） */
    void setStanding(Player player, Faction faction, int value, String reason);

    /** 获取两个阵营之间的关系 */
    InterFactionRelation getRelation(Faction a, Faction b);

    /** 获取玩家敌对的所有阵营 */
    Set<Faction> getHostileFactions(Player player);

    /** 获取玩家友善的所有阵营（HONORED+） */
    Set<Faction> getAlliedFactions(Player player);
}
