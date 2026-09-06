package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

import java.util.EnumSet;

/**
 * 上马目标：NPC 主动走向坐骑并骑上去。
 * 优先级高于 FollowOwnerGoal，确保"上马"指令不会被跟随行为打断。
 */
public class MountGoal extends Goal {

    private final NpcBase npc;

    public MountGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return npc.isPendingMount() && npc.hasMount() && !npc.isPassenger();
    }

    @Override
    public boolean canContinueToUse() {
        if (!npc.isPendingMount() || !npc.hasMount() || npc.isPassenger()) {
            return false;
        }
        Entity mount = npc.getMount();
        return mount != null && mount.isAlive();
    }

    @Override
    public void start() {
        Entity mount = npc.getMount();
        if (mount != null && mount.isAlive()) {
            npc.getNavigation().moveTo(mount, 1.2D);
        }
    }

    @Override
    public void tick() {
        Entity mount = npc.getMount();
        if (mount == null || !mount.isAlive()) {
            npc.clearPendingMount();
            return;
        }

        double distSq = npc.distanceToSqr(mount);
        if (distSq <= 9.0D) { // 3 格以内：上马
            npc.getNavigation().stop();
            // 如果马还被拴着（刚从栅栏解下就可能还有 leash），先解
            npc.untetherHorse();
            if (mount instanceof AbstractHorse h && h.isSaddled()) {
                npc.startRiding(mount);
            }
            npc.setLeadMountMode(false);
            npc.clearPendingMount();
        } else if (npc.getNavigation().isDone()) {
            // 路径走完还没到，重新寻路
            npc.getNavigation().moveTo(mount, 1.2D);
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }
}
