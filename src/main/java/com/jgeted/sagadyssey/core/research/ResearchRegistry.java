package com.jgeted.sagadyssey.core.research;

import java.util.*;

public class ResearchRegistry {
    private static final Map<String, ResearchNode> NODES = new LinkedHashMap<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ===== Tier 1（5 个，各 4 点，合计 20）=====
        register(new ResearchNode("basic_recruitment", "初级招募",
                "可以右键付费招募野外商人和散兵", 4, Collections.emptyList(), true));
        register(new ResearchNode("smithing_craft", "锻造工艺",
                "解锁铁匠职业——铁匠 NPC 可以修理你的装备", 4,
                List.of("basic_recruitment"), false));
        register(new ResearchNode("basic_blueprint", "基础图纸",
                "解锁小型营地图纸合成（猎人小屋、隐士居所）", 4, Collections.emptyList(), true));
        register(new ResearchNode("pack_mule", "驮马训练",
                "驴和骡的箱子格数 +3", 4, Collections.emptyList(), true));
        register(new ResearchNode("cartography", "制图学",
                "手持地图时显示最近 500 格内的聚落位置", 4, Collections.emptyList(), true));

        // ===== Tier 2（8 个，合计 61）=====
        register(new ResearchNode("ranged_specialist", "远程专精",
                "解锁弓箭手职业——远程攻击，自动保持距离", 7,
                List.of("basic_recruitment"), false));
        register(new ResearchNode("self_preservation", "自保本能",
                "所有 NPC 血量低于 20% 时自动撤退", 8,
                List.of("basic_recruitment"), false));
        register(new ResearchNode("heavy_training", "重装训练",
                "解锁重甲兵职业——高血量、移动慢、吸引仇恨", 8,
                List.of("smithing_craft"), false));
        register(new ResearchNode("reputation_boost", "声望提升",
                "定居点招募和交易费用打 7 折", 7,
                List.of("basic_recruitment"), false));
        register(new ResearchNode("intermediate_blueprint", "中级图纸",
                "解锁中型营地图纸（盗匪营地、瞭望哨）", 7,
                List.of("basic_blueprint"), false));
        register(new ResearchNode("structure_recycling", "建筑回收",
                "手持锤子拆结构返还 40% 建造材料", 8,
                List.of("basic_blueprint"), false));
        register(new ResearchNode("water_crossing", "水面航行",
                "马可以涉过 1 格深的水而不把你甩下来", 7,
                List.of("pack_mule"), false));
        register(new ResearchNode("warhorse_training", "战马训练",
                "骑在马上时 NPC 跟随速度自动匹配马速", 9,
                List.of("pack_mule"), false));

        // ===== Tier 3（7 个，合计 97）=====
        register(new ResearchNode("mass_recruitment", "批量招募",
                "一次可同时招募 2 个 NPC（冷却 5 分钟）", 12,
                List.of("reputation_boost"), false));
        register(new ResearchNode("population_boom", "人口振兴",
                "大型定居点 NPC 冷却补位从 5 分钟缩短到 2 分钟", 13,
                List.of("reputation_boost", "intermediate_blueprint"), false));
        register(new ResearchNode("coordinated_attack", "协同作战",
                "2 个以上 NPC 攻击同一目标时每人伤害 +30%", 16,
                List.of("ranged_specialist", "heavy_training"), false));
        register(new ResearchNode("hound_upgrade", "猎犬升级",
                "驯服的狼伤害和血量翻倍", 12,
                List.of("warhorse_training"), false));
        register(new ResearchNode("advanced_blueprint", "高级图纸",
                "解锁大型定居点图纸（围墙村庄、修道院）", 14,
                List.of("intermediate_blueprint"), false));
        register(new ResearchNode("fortress_construction", "要塞建造",
                "解锁边境要塞和领主庄园图纸", 15,
                List.of("advanced_blueprint"), false));
        register(new ResearchNode("trade_routes", "商路网络",
                "定居点之间出现商队 NPC 来回运输稀有交易品", 15,
                List.of("cartography", "reputation_boost"), false));
    }

    private static void register(ResearchNode node) { NODES.put(node.getId(), node); }

    public static Collection<ResearchNode> getAllNodes() { init(); return NODES.values(); }
    public static ResearchNode getNode(String id) { init(); return NODES.get(id); }

    public static List<ResearchNode> getUnlockableNodes(Set<String> unlocked, int availablePoints) {
        init();
        List<ResearchNode> result = new ArrayList<>();
        for (ResearchNode node : NODES.values()) {
            if (!unlocked.contains(node.getId())
                    && node.arePrerequisitesMet(unlocked)
                    && availablePoints >= node.getCost()) {
                result.add(node);
            }
        }
        return result;
    }

    public static List<ResearchNode> getVisibleNodes(Set<String> unlocked) {
        init();
        List<ResearchNode> result = new ArrayList<>();
        for (ResearchNode node : NODES.values()) {
            if (unlocked.contains(node.getId()) || node.isRoot()
                    || node.arePrerequisitesMet(unlocked)) {
                result.add(node);
            }
        }
        return result;
    }
}
