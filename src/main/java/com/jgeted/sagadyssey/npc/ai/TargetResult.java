package com.jgeted.sagadyssey.npc.ai;

import net.minecraft.world.entity.LivingEntity;

/**
 * 目标选择结果，包含目标实体和选择原因。
 */
public record TargetResult(LivingEntity target, TargetReason reason) {}
