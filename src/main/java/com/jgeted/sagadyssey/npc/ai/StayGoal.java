package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 待命行为：当 NPC 命令为 STAY 时，阻止所有移动类目标执行。
 * 优先级高于 RandomStroll，但低于 Float（溺水时仍可上浮）。
 */
public class StayGoal extends Goal {

    private final NpcBase npc;

    public StayGoal(NpcBase npc) {
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        return npc.getCommand() == NpcCommand.STAY;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        npc.setDeltaMovement(npc.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
    }
}
