package com.jgeted.sagadyssey.npc.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.jgeted.sagadyssey.Sagadyssey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 阵营间关系矩阵（单例）。
 * <p>
 * 维护一个双向索引的关系表：Map&lt;factionId, Map&lt;targetFactionId, InterFactionRelation&gt;&gt;。
 * 查询 A→B 关系时，如果关系未定义则默认返回 NEUTRAL。
 * <p>
 * 数据来源：data/sagadyssey/faction_relations.json
 */
public final class FactionRelationMatrix {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sagadyssey.MOD_ID);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String RELATIONS_PATH = "data/sagadyssey/faction_relations.json";

    /** factionId → (targetFactionId → relation) */
    private final Map<String, Map<String, InterFactionRelation>> matrix = new HashMap<>();

    private static final FactionRelationMatrix INSTANCE = new FactionRelationMatrix();

    private FactionRelationMatrix() {}

    public static FactionRelationMatrix getInstance() {
        return INSTANCE;
    }

    /**
     * 查询阵营 A 对阵营 B 的关系。
     * 未定义时默认返回 NEUTRAL。
     */
    public InterFactionRelation getRelation(Faction a, Faction b) {
        if (a == null || b == null) return InterFactionRelation.NEUTRAL;
        return getRelation(a.id(), b.id());
    }

    /** 按 ID 字符串查询关系 */
    public InterFactionRelation getRelation(String factionIdA, String factionIdB) {
        if (factionIdA.equals(factionIdB)) return InterFactionRelation.NEUTRAL;
        return matrix.getOrDefault(factionIdA, Map.of())
                .getOrDefault(factionIdB, InterFactionRelation.NEUTRAL);
    }

    /**
     * 获取受 source 阵营声望变化影响的所有阵营。
     * 只返回关系不为 NEUTRAL 的阵营 ID 列表。
     */
    public List<String> getAffectedFactionIds(String sourceFactionId) {
        Map<String, InterFactionRelation> targets = matrix.get(sourceFactionId);
        if (targets == null) return List.of();
        return targets.entrySet().stream()
                .filter(e -> e.getValue().hasEffect())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 获取受 source 阵营声望变化影响的所有 Faction 对象。
     */
    public List<Faction> getAffectedFactions(Faction source) {
        if (source == null) return List.of();
        return getAffectedFactionIds(source.id()).stream()
                .map(FactionRegistry::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 从类路径加载 faction_relations.json 并构建关系矩阵。
     * 在模组初始化时调用。
     */
    public static void loadFromJson() {
        ClassLoader cl = FactionRelationMatrix.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(RELATIONS_PATH)) {
            if (in == null) {
                LOGGER.warn("未找到阵营关系配置文件: {}", RELATIONS_PATH);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                RelationsFile data = GSON.fromJson(reader, RelationsFile.class);
                if (data == null || data.relations == null) {
                    LOGGER.warn("阵营关系配置文件为空或格式错误");
                    return;
                }
                for (RelationEntry entry : data.relations) {
                    INSTANCE.matrix
                            .computeIfAbsent(entry.from, k -> new HashMap<>())
                            .put(entry.to, entry.relation);
                }
                LOGGER.info("已加载阵营关系矩阵（{} 个阵营，{} 条关系）",
                        INSTANCE.matrix.size(),
                        INSTANCE.matrix.values().stream().mapToInt(Map::size).sum());
            }
        } catch (Exception e) {
            LOGGER.warn("加载阵营关系配置失败: {}", e.getMessage());
        }
    }

    // === JSON 反序列化辅助类型 ===

    /** faction_relations.json 的顶层结构 */
    private static class RelationsFile {
        List<RelationEntry> relations;
    }

    /** 单条关系条目 */
    private static class RelationEntry {
        String from;
        String to;
        InterFactionRelation relation;
    }
}
