package com.jgeted.sagadyssey.npc.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 阵营定义 record。
 * <p>
 * 每个阵营由数据包 JSON 定义（data/sagadyssey/sagadyssey/faction/<name>.json）。
 * 阵营对象通过 {@link FactionRegistry} 的 DataPackRegistry 统一管理。
 *
 * @param id                   阵营唯一标识符（与文件名对应），如 "sagadyssey:kingdom"
 * @param displayName          可翻译的显示名称 key
 * @param color                GUI 显示颜色（ARGB int）
 * @param defaultStanding      新玩家的初始声望值
 * @param canBeHostile         该阵营 NPC 是否可能主动攻击玩家
 * @param canRecruit           是否允许玩家招募该阵营 NPC
 * @param iconTexture          GUI 中显示的阵营纹章纹理路径
 * @param guiNodePosition      关系网络图中的显示坐标 [x, y]（可选）
 * @param reputationMultiplier 声望变化倍率
 * @param decayEnabled         是否启用声望衰减
 * @param decayTarget          衰减目标声望值，默认等于 defaultStanding
 * @param decayRatePerDay      每日衰减点数
 */
public record Faction(
        String id,
        String displayName,
        int color,
        int defaultStanding,
        boolean canBeHostile,
        boolean canRecruit,
        ResourceLocation iconTexture,
        Optional<ResourceLocation> bannerPattern,
        Optional<int[]> guiNodePosition,
        float reputationMultiplier,
        boolean decayEnabled,
        int decayTarget,
        int decayRatePerDay
) {
    public static final Codec<Faction> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(Faction::id),
                    Codec.STRING.fieldOf("display_name").forGetter(Faction::displayName),
                    Codec.INT.fieldOf("color").forGetter(Faction::color),
                    Codec.INT.fieldOf("default_standing").forGetter(Faction::defaultStanding),
                    Codec.BOOL.fieldOf("can_be_hostile").forGetter(Faction::canBeHostile),
                    Codec.BOOL.fieldOf("can_recruit").forGetter(Faction::canRecruit),
                    ResourceLocation.CODEC.fieldOf("icon").forGetter(Faction::iconTexture),
                    ResourceLocation.CODEC.optionalFieldOf("banner_pattern").forGetter(Faction::bannerPattern),
                    Codec.INT.listOf()
                            .xmap(l -> l.stream().mapToInt(Integer::intValue).toArray(),
                                  a -> java.util.Arrays.stream(a).boxed().toList())
                            .optionalFieldOf("gui_node_position")
                            .forGetter(Faction::guiNodePosition),
                    Codec.FLOAT.optionalFieldOf("reputation_multiplier", 1.0f)
                            .forGetter(Faction::reputationMultiplier),
                    Codec.BOOL.optionalFieldOf("decay_enabled", true)
                            .forGetter(Faction::decayEnabled),
                    Codec.INT.optionalFieldOf("decay_target")
                            .forGetter(f -> Optional.of(f.decayTarget)),
                    Codec.INT.optionalFieldOf("decay_rate_per_day", 1)
                            .forGetter(Faction::decayRatePerDay)
            ).apply(instance, Faction::create)
    );

    /** Codec factory：处理 decay_target 默认值（未填时使用 defaultStanding） */
    private static Faction create(
            String id, String displayName, int color, int defaultStanding,
            boolean canBeHostile, boolean canRecruit, ResourceLocation iconTexture,
            Optional<ResourceLocation> bannerPattern, Optional<int[]> guiNodePos,
            float reputationMultiplier, boolean decayEnabled,
            Optional<Integer> decayTargetOpt, int decayRatePerDay
    ) {
        int decayTarget = decayTargetOpt
                .orElse(defaultStanding);
        return new Faction(id, displayName, color, defaultStanding,
                canBeHostile, canRecruit, iconTexture, bannerPattern,
                guiNodePos, reputationMultiplier, decayEnabled,
                decayTarget, decayRatePerDay);
    }

    /** 紧凑构造器 */
    public Faction {
        if (guiNodePosition == null) guiNodePosition = Optional.empty();
        if (bannerPattern == null) bannerPattern = Optional.empty();
    }
}
