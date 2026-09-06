package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

/**
 * 工人工作 AI：在 WORK 命令下自动砍树、挖矿。
 * 优先级：先砍树（原木），再清树叶找树干，再挖露天石头/矿石，最后挖开覆盖层找矿。
 * 找不到工作时随机游荡探索，形成「走 → 看 → 干活」循环。
 */
public class WorkerWorkGoal extends Goal {

    private static final int SEARCH_RADIUS = 20;
    private static final double WORK_REACH_SQ = 9.0;
    private static final int MAX_TREE_HEIGHT = 20; // 整列砍伐的最大树干高度
    private static final int MAX_LEAF_CLEAR = 24;  // 一次最多清多少片树叶

    private final NpcBase npc;
    private BlockPos workPos;
    private int actionCooldown;
    private int stuckTicks;
    private BlockPos explorePos; // 找不到工作时随机游荡的目标

    public WorkerWorkGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!npc.isAlive() || npc.level().isClientSide) return false;
        if (npc.getProfession() != NpcProfession.WORKER) return false;
        if (npc.getCommand() != NpcCommand.WORK) return false;
        if (npc.getTarget() != null || npc.isPassenger()) return false;
        // 每 20 tick 扫描一次，降低开销。相位按实体 id 对齐：
        // 原版 goalSelector 只在 (tickCount+实体id) 为偶数的 tick 轮询 canUse，
        // 奇数 id 的 NPC 用 tickCount%20==0（偶数 tick）永远轮询不到
        if ((npc.tickCount - npc.getId()) % 20 != 0) return false;

        BlockPos found = findWork();
        if (found != null) {
            if (!npc.requestMutex(AiMutex.MOVE)) return false;
            this.workPos = found;
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
                mineBlock();
                workPos = null;
            }
        } else if (npc.getNavigation().isDone()) {
            if (++stuckTicks > 10) {
                // 卡住：目标是原木却被树叶挡路 → 改去清掉离工人最近、挨着树干的树叶，清出路
                if (npc.level().getBlockState(workPos).is(BlockTags.LOGS)) {
                    BlockPos leaf = findLeafNearLog(workPos);
                    if (leaf != null) {
                        workPos = leaf;
                        stuckTicks = 0;
                        npc.getNavigation().moveTo(leaf.getX() + 0.5, leaf.getY(), leaf.getZ() + 0.5, 1.0);
                        return;
                    }
                }
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
            explorePos = null;
        }
    }

    /** 扫描附近可挖的方块：先砍树（原木），再清树叶找树干，再挖露天石头/矿，最后挖开覆盖层找矿 */
    private BlockPos findWork() {
        BlockPos center = npc.blockPosition();
        BlockPos fallback = null;
        BlockPos leafFallback = null;
        BlockPos digTarget = null;
        double bestDigDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                // 可达层：砍树 / 挖露天石头矿石 / 清树叶（向上扩到 +4，能扫到高处树干/树枝）
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!npc.isWorkAllowedAt(p)) continue;
                    BlockState state = npc.level().getBlockState(p);
                    if (state.is(BlockTags.LOGS)) return p; // 优先砍树
                    if (fallback == null && isMineTarget(state)) fallback = p;
                    if (leafFallback == null && state.is(BlockTags.LEAVES) && isLeafNearLog(p)) {
                        leafFallback = p;
                    }
                }
                // 挖矿兜底：找被土/草/砂砾盖住、下方有矿的地表，就近挖开
                BlockPos surface = findSurface(center.getX() + dx, center.getZ() + dz, center.getY() + 3);
                if (surface != null && npc.isWorkAllowedAt(surface)
                        && isCover(npc.level().getBlockState(surface)) && stoneBelow(surface)) {
                    double d = center.distSqr(surface);
                    if (d < bestDigDist) {
                        bestDigDist = d;
                        digTarget = surface;
                    }
                }
            }
        }
        if (leafFallback != null) return leafFallback;
        if (fallback != null) return fallback;
        return digTarget;
    }

    /** 树叶附近 3 格内是否有原木（清树叶以露出树干） */
    private boolean isLeafNearLog(BlockPos pos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (npc.level().getBlockState(pos.offset(dx, dy, dz)).is(BlockTags.LOGS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 找一根挨着树干、且离工人最近的树叶，用于卡住时清出通往树的路 */
    private BlockPos findLeafNearLog(BlockPos logPos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos center = npc.blockPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = logPos.offset(dx, dy, dz);
                    if (npc.level().getBlockState(p).is(BlockTags.LEAVES)
                            && npc.isWorkAllowedAt(p)) {
                        double d = center.distSqr(p);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** 从 (x, z) 往下 8 格找第一个非空气方块（地表），找不到返回 null */
    private BlockPos findSurface(int x, int z, int startY) {
        for (int y = startY; y >= startY - 8; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!npc.level().getBlockState(p).isAir()) return p;
        }
        return null;
    }

    /** 地表覆盖层（土/草/砂砾/沙），可挖开 */
    private static boolean isCover(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND);
    }

    /** 该方块正下方 5 格内是否有石头/矿石 */
    private boolean stoneBelow(BlockPos surface) {
        for (int dy = 1; dy <= 5; dy++) {
            if (isMineTarget(npc.level().getBlockState(surface.below(dy)))) return true;
        }
        return false;
    }

    private void mineBlock() {
        BlockState state = npc.level().getBlockState(workPos);
        if (state.isAir()) return;

        if (state.is(BlockTags.LOGS)) {
            // 整列砍伐：从树顶到树根一次砍掉整条树干，站在树根也能砍完高树
            chopTrunk(workPos);
        } else if (state.is(BlockTags.LEAVES)) {
            clearLeafCluster(workPos); // 一次清掉一片树叶，快速清出通往树的路
        } else {
            harvestBlock(workPos);
        }
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 收集并破坏单个方块（原木/矿石/树叶通用） */
    private void harvestBlock(BlockPos pos) {
        BlockState state = npc.level().getBlockState(pos);
        if (state.isAir()) return;
        List<ItemStack> drops = Block.getDrops(state, (ServerLevel) npc.level(), pos,
                npc.level().getBlockEntity(pos), npc, npc.getMainHandItem());
        for (ItemStack drop : drops) {
            if (!npc.addToBag(drop)) {
                npc.spawnAtLocation(drop);
            }
        }
        npc.level().destroyBlock(pos, false, npc);
    }

    /** 一次清掉目标周围一片树叶（含目标），最多 MAX_LEAF_CLEAR 片，用于快速清出通往树的路 */
    private void clearLeafCluster(BlockPos start) {
        int cleared = 0;
        for (int dx = -2; dx <= 2 && cleared < MAX_LEAF_CLEAR; dx++) {
            for (int dy = -1; dy <= 1 && cleared < MAX_LEAF_CLEAR; dy++) {
                for (int dz = -2; dz <= 2 && cleared < MAX_LEAF_CLEAR; dz++) {
                    BlockPos p = start.offset(dx, dy, dz);
                    if (npc.level().getBlockState(p).is(BlockTags.LEAVES)
                            && npc.isWorkAllowedAt(p)) {
                        harvestBlock(p);
                        cleared++;
                    }
                }
            }
        }
    }

    /** 整条树干砍伐：从起点向上找树顶、向下找树根，再从顶砍到底 */
    private void chopTrunk(BlockPos start) {
        // 向上找树顶
        BlockPos top = start;
        int height = 0;
        while (npc.level().getBlockState(top).is(BlockTags.LOGS) && height < MAX_TREE_HEIGHT) {
            top = top.above();
            height++;
        }
        // 向下找树根（start 以下的部分）
        BlockPos bottom = start.below();
        int depth = 0;
        while (npc.level().getBlockState(bottom).is(BlockTags.LOGS) && depth < MAX_TREE_HEIGHT) {
            bottom = bottom.below();
            depth++;
        }
        // 从树顶往下逐根砍掉
        BlockPos cursor = top.below();
        for (int i = 0; i < height + depth; i++) {
            if (npc.level().getBlockState(cursor).is(BlockTags.LOGS)) {
                harvestBlock(cursor);
            }
            cursor = cursor.below();
        }
    }

    /** 石头/矿石是否可挖 */
    private static boolean isMineTarget(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.EMERALD_ORES);
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
