package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

/**
 * 农民工作 AI：在 WORK 命令下自动种地。
 * 工作优先级：收获成熟作物 → 在空耕地上播种作物 → 在水边开垦耕地。
 * 找不到工作时随机游荡探索，形成「走 → 看 → 干活」循环。
 */
public class FarmerWorkGoal extends Goal {

    private static final int KIND_TILL = 1;
    private static final int KIND_PLANT = 2;
    private static final int KIND_HARVEST = 3;

    private static final int SEARCH_RADIUS = 20;
    private static final double WORK_REACH_SQ = 9.0; // 3 格内可作业

    private final NpcBase npc;
    private BlockPos workPos;
    private int workKind;
    private int actionCooldown;
    private int stuckTicks;
    private BlockPos explorePos; // 找不到工作时随机游荡的目标
    private int lastSeedSlot = -1; // 轮作：记住上次播种的格子，下次换一种作物

    /** 一个待做的工作点（位置 + 类型） */
    private record Work(BlockPos pos, int kind) {}

    public FarmerWorkGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!npc.isAlive() || npc.level().isClientSide) return false;
        if (npc.getProfession() != NpcProfession.FARMER) return false;
        if (npc.getCommand() != NpcCommand.WORK) return false;
        if (npc.getTarget() != null || npc.isPassenger()) return false;
        if (npc.tickCount % 20 != 0) return false; // 每 20 tick 扫描一次，降低开销

        Work work = findWork();
        if (work != null) {
            if (!npc.requestMutex(AiMutex.MOVE)) return false;
            this.workPos = work.pos();
            this.workKind = work.kind();
            this.explorePos = null;
            return true;
        }

        // 找不到工作：随机游荡探索，走出去找活干（同样持有 MOVE，保证 stop() 释放配平）
        if (!npc.requestMutex(AiMutex.MOVE)) return false;
        this.workPos = null;
        this.explorePos = randomExplorePos();
        return this.explorePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getTarget() != null || npc.getCommand() != NpcCommand.WORK || npc.isPassenger()) {
            return false;
        }
        return workPos != null || explorePos != null;
    }

    @Override
    public void start() {
        actionCooldown = 0;
        stuckTicks = 0;
        if (workPos != null) {
            npc.getNavigation().moveTo(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5, 1.0);
        } else if (explorePos != null) {
            npc.getNavigation().moveTo(explorePos.getX() + 0.5, explorePos.getY(), explorePos.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        workPos = null;
        explorePos = null;
        npc.releaseMutex(AiMutex.MOVE);
    }

    @Override
    public void tick() {
        if (workPos != null) {
            tickWork();
        } else if (explorePos != null) {
            tickExplore();
        }
    }

    /** 走向工作点并作业 */
    private void tickWork() {
        npc.getLookControl().setLookAt(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5);
        double distSq = npc.distanceToSqr(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5);
        if (distSq <= WORK_REACH_SQ) {
            npc.getNavigation().stop();
            if (--actionCooldown <= 0) {
                actionCooldown = 20;
                doWork();
                workPos = null; // 完成一个工作点，释放 MOVE，等待下次扫描
            }
        } else if (npc.getNavigation().isDone()) {
            if (++stuckTicks > 10) {
                workPos = null;
            } else {
                npc.getNavigation().moveTo(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5, 1.0);
            }
        }
    }

    /** 无工作可做时游荡探索 */
    private void tickExplore() {
        if (npc.getNavigation().isDone()
                || npc.distanceToSqr(explorePos.getX() + 0.5, explorePos.getY(), explorePos.getZ() + 0.5) <= 4.0) {
            explorePos = null; // 到达后停下，下次 canUse 重新找活
        }
    }

    /** 扫描附近可做的工作 */
    private Work findWork() {
        BlockPos center = npc.blockPosition();
        Work plant = null;
        Work till = null;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!npc.isWorkAllowedAt(p)) continue;
                    BlockState state = npc.level().getBlockState(p);

                    // 收获：成熟作物（优先级最高，找到即返回）
                    if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                        return new Work(p, KIND_HARVEST);
                    }
                    // 种植：空耕地 + 有种子（小麦/胡萝卜/马铃薯/甜菜根）
                    if (state.is(Blocks.FARMLAND)
                            && npc.level().getBlockState(p.above()).isAir()
                            && hasSeeds()) {
                        if (plant == null) plant = new Work(p, KIND_PLANT);
                    }
                    // 开垦：近水泥土/草方块 + 手里有锄头
                    if ((state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))
                            && npc.level().getBlockState(p.above()).isAir()
                            && nearWater(p)
                            && npc.getMainHandItem().getItem() instanceof HoeItem) {
                        if (till == null) till = new Work(p, KIND_TILL);
                    }
                }
            }
        }
        if (plant != null) return plant;
        return till;
    }

    private void doWork() {
        switch (workKind) {
            case KIND_HARVEST -> harvest();
            case KIND_PLANT -> plant();
            case KIND_TILL -> till();
        }
    }

    private void harvest() {
        BlockState state = npc.level().getBlockState(workPos);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            List<ItemStack> drops = Block.getDrops(state, (ServerLevel) npc.level(), workPos,
                    npc.level().getBlockEntity(workPos), npc, npc.getMainHandItem());
            for (ItemStack drop : drops) {
                if (!npc.addToBag(drop)) {
                    npc.spawnAtLocation(drop);
                }
            }
            npc.level().destroyBlock(workPos, false, npc);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void plant() {
        BlockPos cropPos = workPos.above();
        if (npc.level().getBlockState(workPos).is(Blocks.FARMLAND)
                && npc.level().getBlockState(cropPos).isAir()) {
            int slot = findSeedSlot();
            if (slot < 0) return;
            lastSeedSlot = slot; // 记住这次，下次换一种
            ItemStack seed = npc.getEquipmentInventory().getItem(slot);
            Block crop = cropForSeed(seed);
            if (crop == null) return;
            seed.shrink(1);
            npc.level().setBlock(cropPos, crop.defaultBlockState(), Block.UPDATE_ALL);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void till() {
        BlockState state = npc.level().getBlockState(workPos);
        if ((state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))
                && npc.level().getBlockState(workPos.above()).isAir()
                && npc.getMainHandItem().getItem() instanceof HoeItem) {
            npc.level().setBlock(workPos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean hasSeeds() {
        return findSeedSlot() >= 0;
    }

    /** 轮作：从上次播种的下一个格子开始找种子，让农民轮流种不同作物 */
    private int findSeedSlot() {
        int size = npc.getEquipmentInventory().getContainerSize();
        for (int offset = 0; offset < size; offset++) {
            int i = (lastSeedSlot + 1 + offset) % size;
            if (cropForSeed(npc.getEquipmentInventory().getItem(i)) != null) return i;
        }
        return -1;
    }

    /** 种子物品 → 对应作物方块 */
    private Block cropForSeed(ItemStack seed) {
        if (seed.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT;
        if (seed.is(Items.CARROT)) return Blocks.CARROTS;
        if (seed.is(Items.POTATO)) return Blocks.POTATOES;
        if (seed.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS;
        return null;
    }

    /** 水平 4 格内是否有水（耕地保湿） */
    private boolean nearWater(BlockPos pos) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (npc.level().getBlockState(pos.offset(dx, 0, dz)).getFluidState().is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 随机选一个更远的探索目标，走出去找活（有工作范围则在范围内随机） */
    private BlockPos randomExplorePos() {
        RandomSource random = npc.getRandom();
        if (npc.hasWorkZone()) {
            BlockPos min = npc.getWorkZoneMin();
            BlockPos max = npc.getWorkZoneMax();
            int x = min.getX() + random.nextInt(Math.max(1, max.getX() - min.getX() + 1));
            int z = min.getZ() + random.nextInt(Math.max(1, max.getZ() - min.getZ() + 1));
            return new BlockPos(x, npc.getBlockY(), z);
        }
        int x = npc.getBlockX() + random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
        int z = npc.getBlockZ() + random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
        return new BlockPos(x, npc.getBlockY(), z);
    }
}
