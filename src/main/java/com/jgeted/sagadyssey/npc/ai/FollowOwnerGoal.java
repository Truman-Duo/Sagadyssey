package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.EnumSet;

/**
 * 跟随主人行为：NPC 以固定速度跟随主人，保持目标距离。
 * 超出最大距离时开始追赶，到达目标距离时停下。
 */
public class FollowOwnerGoal extends Goal {

    private final NpcBase npc;
    private final double speed;
    private final float followDistance;
    private final float stopDistance;
    private final PathNavigation navigation;
    private LivingEntity owner;
    private int timeToRecalcPath;

    public FollowOwnerGoal(NpcBase npc, double speed, float stopDistance, float followDistance) {
        this.npc = npc;
        this.speed = speed;
        this.stopDistance = stopDistance;
        this.followDistance = followDistance;
        this.navigation = npc.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.getCommand() != NpcCommand.FOLLOW) {
            return false;
        }

        if (!(npc.level().getPlayerByUUID(npc.getOwnerUUID()) instanceof LivingEntity player)) {
            return false;
        }

        if (!player.level().equals(npc.level())) {
            return false;
        }

        double distSqr = npc.distanceToSqr(player);

        if (distSqr < (double) (stopDistance * stopDistance)) {
            return false;
        }

        if (distSqr > (double) (followDistance * followDistance)) {
            return false;
        }

        this.owner = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getCommand() != NpcCommand.FOLLOW) {
            return false;
        }
        if (owner == null || !owner.isAlive()) {
            return false;
        }
        if (!owner.level().equals(npc.level())) {
            return false;
        }

        double distSqr = npc.distanceToSqr(owner);

        if (distSqr < (double) (stopDistance * stopDistance)) {
            return false;
        }
        return distSqr <= (double) (followDistance * followDistance);
    }

    @Override
    public void start() {
        timeToRecalcPath = 0;
        npc.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        owner = null;
        navigation.stop();
        npc.setPathfindingMalus(PathType.WATER, -1.0F);
    }

    @Override
    public void tick() {
        npc.getLookControl().setLookAt(owner, 10.0F, npc.getMaxHeadXRot());

        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = adjustedTickDelay(10);
            navigation.moveTo(owner, speed);
        }
    }
}
