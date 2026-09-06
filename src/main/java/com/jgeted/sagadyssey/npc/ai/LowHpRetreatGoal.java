package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * 低血量撤退+回血。
 * HP < 30% → 向主人撤退，停止攻击，缓慢回血。
 * HP ≥ 60% → 恢复正常行为。
 */
public class LowHpRetreatGoal extends Goal {

    private final NpcBase npc;
    private int regenTimer;

    private static final float RETREAT_THRESHOLD = 0.30F;
    private static final float RECOVER_THRESHOLD = 0.60F;
    private static final double OWNER_TOO_FAR = 256.0D;

    public LowHpRetreatGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (npc.getCommand() == NpcCommand.STAY) return false;
        if (npc.getOwnerUUID() == null) return false;
        float hpRatio = npc.getHealth() / npc.getMaxHealth();
        if (hpRatio > RETREAT_THRESHOLD) return false;

        Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        if (owner == null || !owner.isAlive()
                || npc.distanceToSqr(owner) >= OWNER_TOO_FAR) {
            return false;
        }

        if (!npc.requestMutex(AiMutex.MOVE)) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getCommand() == NpcCommand.STAY) return false;
        if (npc.getOwnerUUID() == null) return false;
        float hpRatio = npc.getHealth() / npc.getMaxHealth();
        if (hpRatio >= RECOVER_THRESHOLD) return false;

        Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        return owner != null && owner.isAlive();
    }

    @Override
    public void start() {
        npc.setTarget(null);
        npc.getNavigation().stop();
        regenTimer = 0;
    }

    @Override
    public void tick() {
        Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        if (owner == null) return;

        if (npc.isPassenger()) {
            // 骑乘撤退：直接设置移动方向，让马跑
            double dx = owner.getX() - npc.getX();
            double dz = owner.getZ() - npc.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 2.0) {
                float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                npc.setYRot(targetYaw);
                npc.yHeadRot = targetYaw;
                npc.zza = 1.0F;
                npc.xxa = 0.0F;
            } else {
                npc.zza = 0.0F;
                npc.xxa = 0.0F;
            }
        } else {
            npc.getNavigation().moveTo(owner, 1.3D);
        }

        if (++regenTimer >= 20) {
            regenTimer = 0;
            npc.heal(1.0F);
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        npc.releaseMutex(AiMutex.MOVE);
    }
}
