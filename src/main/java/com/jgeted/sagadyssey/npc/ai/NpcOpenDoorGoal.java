package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * NPC 开门 AI（村民式简单设计）。
 * <p>
 * 逻辑：路径前方有门阻挡就开，NPC 走远且门开了够久就关。
 * 一次只处理一扇门，不追踪连续门、不记冷却——关掉后自然由下一次 canUse 接管下一扇门。
 */
public class NpcOpenDoorGoal extends Goal {

    private final NpcBase npc;
    private BlockPos doorPos;
    private int openTicks;

    /** 门最少保持开启的时间（tick），防止刚开就关 */
    private static final int MIN_OPEN_TICKS = 30;
    /** NPC 离门多远算"已通过"（距离平方，约 3 格） */
    private static final double PASSED_DIST_SQR = 9.0D;

    public NpcOpenDoorGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!npc.getNavigation().isInProgress()) return false;
        if (npc.getTarget() != null) return false;
        if (npc.isPassenger()) return false;

        BlockPos door = findBlockingDoor();
        if (door == null) return false;

        this.doorPos = door;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (doorPos == null) return false;
        // 门还开着就继续
        BlockState state = npc.level().getBlockState(doorPos);
        return isOpenableDoor(state) && isDoorOpen(state);
    }

    @Override
    public void start() {
        BlockState state = npc.level().getBlockState(doorPos);
        if (isOpenableDoor(state) && !isDoorOpen(state)) {
            setDoorOpen(doorPos, true);
        }
        openTicks = 0;
    }

    @Override
    public void tick() {
        if (doorPos == null) return;
        openTicks++;

        // 门开够久了、且 NPC 已经走远 → 关门
        if (openTicks >= MIN_OPEN_TICKS) {
            double distSqr = npc.distanceToSqr(Vec3.atCenterOf(doorPos));
            if (distSqr > PASSED_DIST_SQR) {
                BlockState state = npc.level().getBlockState(doorPos);
                if (isOpenableDoor(state) && isDoorOpen(state)) {
                    setDoorOpen(doorPos, false);
                }
            }
        }
    }

    @Override
    public void stop() {
        if (doorPos != null) {
            BlockState state = npc.level().getBlockState(doorPos);
            if (isOpenableDoor(state) && isDoorOpen(state)) {
                setDoorOpen(doorPos, false);
            }
        }
        doorPos = null;
        openTicks = 0;
    }

    /**
     * 找到路径前方阻挡 NPC 的门。
     * 只检测导航路径节点上的门——不会乱开旁边的门。
     */
    private BlockPos findBlockingDoor() {
        Path path = npc.getNavigation().getPath();
        if (path == null) return null;

        int nextIdx = path.getNextNodeIndex();
        int endIdx = Math.min(nextIdx + 3, path.getNodeCount());
        for (int i = nextIdx; i < endIdx; i++) {
            BlockPos node = path.getNodePos(i);
            // 检查节点周围 3×3×3 范围——路径节点不一定精确落在门方块上
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos check = node.offset(dx, dy, dz);
                        BlockState state = npc.level().getBlockState(check);
                        if (isOpenableDoor(state) && !isDoorOpen(state)) {
                            return check;
                        }
                    }
                }
            }
        }
        return null;
    }

    // === 方块检测工具方法 ===

    private boolean isOpenableDoor(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return state.getBlock().defaultDestroyTime() <= 3.0F;
        }
        return state.getBlock() instanceof FenceGateBlock;
    }

    private boolean isDoorOpen(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return state.getValue(FenceGateBlock.OPEN);
        }
        return false;
    }

    private void setDoorOpen(BlockPos pos, boolean open) {
        BlockState state = npc.level().getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            npc.level().setBlock(pos, state.setValue(DoorBlock.OPEN, open), 10);
            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = npc.level().getBlockState(otherPos);
            if (otherState.getBlock() instanceof DoorBlock) {
                npc.level().setBlock(otherPos, otherState.setValue(DoorBlock.OPEN, open), 10);
            }
        } else if (state.getBlock() instanceof FenceGateBlock) {
            npc.level().setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), 10);
        }
    }
}
