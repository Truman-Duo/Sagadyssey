package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.faction.FactionAttachments;
import com.jgeted.sagadyssey.npc.faction.FactionRegistry;
import com.jgeted.sagadyssey.npc.faction.StandingModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * 敌对 NPC 攻击目标选择器。
 * <p>
 * 基于新 Faction 系统：当玩家对 NPC 所属阵营的声望为 HATED 时，
 * NPC 主动攻击该玩家。同时保留对旧 HOSTILE 阵营 NPC 的扫描。
 */
public class NpcHostileGoal extends Goal {

    private static final double DETECT_RANGE = 16.0D;
    private static final double DETECT_RANGE_MOUNTED = 24.0D;
    private final NpcBase npc;
    private LivingEntity pendingTarget;
    private int scanCooldown;

    public NpcHostileGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // 新系统：如果 NPC 的阵营不是 canBeHostile，则不主动攻击
        var npcFaction = npc.getFaction();
        if (npcFaction == null || !npcFaction.canBeHostile()) {
            return false;
        }

        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = 20;

        double range = npc.isPassenger() ? DETECT_RANGE_MOUNTED : DETECT_RANGE;
        AABB box = npc.getBoundingBox().inflate(range);
        List<Player> players = npc.level().getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : players) {
            if (npc.isOwnedBy(p.getUUID())) continue;

            // 新系统：检查玩家对该阵营的声望
            var standings = FactionAttachments.getStandings(p);
            if (!standings.isHostile(npcFaction)) continue;

            double dist = npc.distanceToSqr(p);
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }

        // 扫描附近的已招募 NPC（通过 isHostileBetween 判定是否敌对）
        List<NpcBase> nearbyNpcs = npc.level().getEntitiesOfClass(NpcBase.class, box,
                n -> n.isAlive() && n != npc
                        && StandingModifier.isHostileBetween(
                                npc, n, npc.getOwnerUUID(), n.getOwnerUUID()));

        for (NpcBase n : nearbyNpcs) {
            double dist = npc.distanceToSqr(n);
            if (dist < bestDist) {
                bestDist = dist;
                best = n;
            }
        }

        if (best != null) {
            this.pendingTarget = best;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        npc.setTarget(pendingTarget);
        pendingTarget = null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        if (npc.isOwnedBy(target.getUUID())) return false;
        double range = npc.isPassenger() ? DETECT_RANGE_MOUNTED : DETECT_RANGE;
        return npc.distanceToSqr(target) <= range * range;
    }

    @Override
    public void stop() {
        npc.setTarget(null);
    }
}
