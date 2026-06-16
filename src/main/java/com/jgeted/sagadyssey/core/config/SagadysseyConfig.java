package com.jgeted.sagadyssey.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Sagadyssey 配置文件。
 * 玩家可以在游戏内 Mods 菜单或 config 文件夹中修改。
 */
public class SagadysseyConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue RESEARCH_POINTS_PER_ACHIEVEMENT;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOG;
    public static final ModConfigSpec.DoubleValue NPC_SPAWN_CHANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // ===== 研究系统 =====
        builder.comment("研究系统设置").push("research");
        RESEARCH_POINTS_PER_ACHIEVEMENT = builder
                .comment("每个成就完成后获得的默认研究点数（成就分级未实现前使用此值）")
                .defineInRange("pointsPerAchievement", 10, 1, 100);
        builder.pop();

        // ===== 调试 =====
        builder.comment("调试选项").push("debug");
        ENABLE_DEBUG_LOG = builder
                .comment("开启详细日志输出，开发阶段建议开启")
                .define("enableDebugLog", true);
        builder.pop();

        // ===== NPC =====
        builder.comment("NPC 生成设置").push("npc");
        NPC_SPAWN_CHANCE = builder
                .comment("NPC 在世界中自然生成的概率 (0.0 = 不生成, 1.0 = 最高)")
                .defineInRange("spawnChance", 0.05, 0.0, 1.0);
        builder.pop();

        SPEC = builder.build();
    }
}
