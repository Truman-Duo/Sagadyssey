package com.jgeted.sagadyssey.core.research;

import java.util.HashSet;
import java.util.Set;

/**
 * 客户端研究数据缓存。由服务端 ResearchSyncPayload 推送更新。
 */
public class ClientResearchCache {
    private static int points = 0;
    private static final Set<String> unlocked = new HashSet<>();

    public static int getPoints() { return points; }
    public static Set<String> getUnlocked() { return new HashSet<>(unlocked); }
    public static boolean isUnlocked(String id) { return unlocked.contains(id); }

    public static void set(int p, Set<String> u) {
        points = p;
        unlocked.clear();
        unlocked.addAll(u);
    }
}
