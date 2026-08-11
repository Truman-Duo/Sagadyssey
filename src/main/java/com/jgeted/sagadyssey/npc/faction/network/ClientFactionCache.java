package com.jgeted.sagadyssey.npc.faction.network;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端阵营声望缓存。
 * <p>
 * 供 GUI 渲染使用，数据由服务端 Payload 更新。
 * 客户端不做任何计算，仅缓存和读取。
 */
public final class ClientFactionCache {

    /** 阵营ID → 声望值 */
    private static final Map<String, Integer> standings = new HashMap<>();

    /** 关系摘要（"kingdom→church" → "ALLY"） */
    private static final Map<String, String> relationSummary = new HashMap<>();

    /** 玩家自定义阵营名称（null = 使用默认值 "玩家阵营"） */
    private static String playerFactionName = null;

    private ClientFactionCache() {}

    /** 从全量同步更新 */
    public static void updateFromSync(Map<String, Integer> data) {
        standings.clear();
        standings.putAll(data);
    }

    /** 从增量更新单个阵营的声望 */
    public static void updateFromUpdate(String factionId, int newValue) {
        standings.put(factionId, newValue);
    }

    /** 更新关系摘要 */
    public static void updateRelationSummary(Map<String, String> summary) {
        relationSummary.clear();
        relationSummary.putAll(summary);
    }

    /** 获取某阵营的声望值 */
    public static int getStanding(String factionId) {
        if ("sagadyssey:player".equals(factionId)) return 100;
        return standings.getOrDefault(factionId, 0);
    }

    /** 获取某阵营的声望等级 */
    public static com.jgeted.sagadyssey.npc.faction.StandingLevel getLevel(String factionId) {
        if ("sagadyssey:player".equals(factionId)) return com.jgeted.sagadyssey.npc.faction.StandingLevel.REVERED;
        return com.jgeted.sagadyssey.npc.faction.StandingLevel.fromValue(getStanding(factionId));
    }

    /** 更新玩家自定义阵营名称 */
    public static void setPlayerFactionName(String name) {
        playerFactionName = (name != null && !name.isEmpty()) ? name : null;
    }

    /** 获取玩家阵营显示名称（自定义名称或默认 "玩家阵营"） */
    public static String getPlayerFactionDisplayName() {
        return playerFactionName != null ? playerFactionName : "玩家阵营";
    }

    /** 获取全部声望数据 */
    public static Map<String, Integer> getAllStandings() {
        return Map.copyOf(standings);
    }

    /** 获取关系摘要 */
    public static Map<String, String> getRelationSummary() {
        return Map.copyOf(relationSummary);
    }
}
