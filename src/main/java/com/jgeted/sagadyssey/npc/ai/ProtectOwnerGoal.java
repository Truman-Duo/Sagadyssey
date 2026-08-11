package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.faction.FactionAttachments;
import com.jgeted.sagadyssey.npc.faction.StandingLevel;
import com.jgeted.sagadyssey.npc.faction.StandingModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * 保护主人 AI：跟随模式下，自动攻击主人周围的威胁。
 * <p>
 * 新系统：当主人对 NPC 所属阵营声望为 HONORED+ 时，该 NPC 会保护主人。
 * 威胁判定：敌对阵营 NPC（canBeHostile=true）。
 */
public class ProtectOwnerGoal extends Goal {

    private final NpcBase npc;
    private int scanCooldown;

    private static final double SCAN_RANGE = 16.0D;
    private static final double SCAN_RANGE_MOUNTED = 24.0D;

    public ProtectOwnerGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (npc.getCommand() != NpcCommand.FOLLOW) return false;
        if (npc.getOwnerUUID() == null) return false;

        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = 3;

        Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        if (owner == null || !owner.isAlive()) return false;

        // 已被该玩家招募的 NPC 无条件保护主人（忠诚 > 阵营政治）
        // 未招募的 NPC 才需要 HONORED+ 声望
        if (!npc.isOwnedBy(owner.getUUID())) {
            var npcFaction = npc.getFaction();
            if (npcFaction != null) {
                var standings = FactionAttachments.getStandings(owner);
                StandingLevel level = standings.getLevel(npcFaction);
                if (level != StandingLevel.HONORED && level != StandingLevel.REVERED) {
                    return false;
                }
            }
        }

        // 扫描 NPC 自身和主人周围的威胁
        double range = npc.isPassenger() ? SCAN_RANGE_MOUNTED : SCAN_RANGE;
        AABB npcBox = npc.getBoundingBox().inflate(range);
        List<LivingEntity> nearby = npc.level().getEntitiesOfClass(LivingEntity.class, npcBox,
                e -> e.isAlive() && e != owner && e != npc && isThreat(e, owner));

        AABB ownerBox = owner.getBoundingBox().inflate(range);
        List<LivingEntity> ownerNearby = npc.level().getEntitiesOfClass(LivingEntity.class, ownerBox,
                e -> e.isAlive() && e != owner && e != npc && isThreat(e, owner));
        for (LivingEntity e : ownerNearby) {
            if (!nearby.contains(e)) nearby.add(e);
        }

        if (!nearby.isEmpty()) {
            LivingEntity target = nearby.get(0);
            if (npc.getTarget() != target) {
                npc.setTarget(target);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getCommand() != NpcCommand.FOLLOW) return false;
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) return false;

        Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        if (owner == null || !owner.isAlive()) return false;

        if (npc.distanceToSqr(owner) > 256.0D) return false;
        double range = npc.isPassenger() ? SCAN_RANGE_MOUNTED : SCAN_RANGE;
        return npc.distanceToSqr(target) <= range * range && isThreat(target, owner);
    }

    @Override
    public void start() {
        // target already set in canUse
    }

    @Override
    public void stop() {
        LivingEntity target = npc.getTarget();
        if (target != null && !(target instanceof NpcBase)) {
            npc.setTarget(null);
        }
    }

    private boolean isThreat(LivingEntity entity, Player owner) {
        // 1. 正在攻击这个 NPC → 自卫
        if (entity instanceof Mob mob && mob.getTarget() == npc) return true;
        // 2a. 主人在攻击的目标（玩家主动打怪）
        if (entity == owner.getLastHurtMob() && npc.distanceToSqr(owner) < 100.0D) return true;
        // 2b. 正在攻击主人的目标（怪打玩家，玩家没还手也保护）
        if (entity == owner.getLastHurtByMob() && npc.distanceToSqr(owner) < 100.0D) return true;
        // 3. 基于阵营的威胁判定
        if (entity instanceof NpcBase otherNpc && otherNpc.getFaction() != null) {
            // player 阵营 NPC：同 owner → 不威胁，不同 owner → v1.0 不威胁
            if ("sagadyssey:player".equals(otherNpc.getFaction().id())) {
                return !otherNpc.isOwnedBy(owner.getUUID());
            }
            // 普通阵营 NPC：通过 isHostileBetween 统一判定
            return StandingModifier.isHostileBetween(
                    npc, otherNpc, npc.getOwnerUUID(), otherNpc.getOwnerUUID());
        }
        // 4. 原版敌对生物（zombie 等）：始终视为威胁
        if (entity instanceof Mob && entity.getType().getCategory().isFriendly()) return false;
        if (entity instanceof Mob) return true;
        return false;
    }
}
