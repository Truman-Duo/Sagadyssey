package com.jgeted.sagadyssey.npc.event;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 将玩家实际造成/受到的伤害明确传播给附近的己方 NPC。
 * 不依赖客户端交互，也不依赖玩家对象的短暂 lastHurt 状态，适用于多人服务器。
 */
public final class NpcOwnerCombatEvents {

    private static final double NOTIFY_RANGE = 32.0D;
    private static final long COMBAT_MEMORY_TICKS = 100L;

    private NpcOwnerCombatEvents() {}

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0.0F) return;

        if (victim instanceof Player owner
                && event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != owner
                && isAttackablePlayer(attacker)) {
            notifyOwnedNpcs(owner, attacker, true);
        }

        if (event.getSource().getEntity() instanceof Player owner
                && victim != owner
                && isAttackablePlayer(victim)) {
            notifyOwnedNpcs(owner, victim, false);
        }
    }

    private static void notifyOwnedNpcs(Player owner, LivingEntity target, boolean defense) {
        owner.level().getEntitiesOfClass(NpcBase.class,
                        owner.getBoundingBox().inflate(NOTIFY_RANGE),
                        npc -> npc.isAlive()
                                && npc.isOwnedBy(owner.getUUID())
                                && !npc.isCombatAlly(target))
                .forEach(npc -> {
                    if (defense) {
                        npc.rememberOwnerDefenseTarget(target, COMBAT_MEMORY_TICKS);
                    } else {
                        npc.rememberOwnerAssistTarget(target, COMBAT_MEMORY_TICKS);
                    }
                });
    }

    private static boolean isAttackablePlayer(LivingEntity entity) {
        return !(entity instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }
}
