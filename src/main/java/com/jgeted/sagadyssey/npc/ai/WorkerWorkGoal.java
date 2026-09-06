package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工人工作 AI：在 WORK 命令下自动砍树、挖矿。
 * 优先级：先砍树（原木），再清树叶找树干，再挖露天石头/矿石，最后挖开覆盖层找矿。
 * 找不到工作时随机游荡探索，形成「走 → 看 → 干活」循环。
 */
public class WorkerWorkGoal extends Goal {

    private static final int SEARCH_RADIUS = 20;
    private static final double WORK_REACH_SQ = 20.25; // 以眼睛为起点，4.5 格工具触及距离
    private static final double STAND_REACHED_SQ = 2.25;
    private static final long UNREACHABLE_COOLDOWN_TICKS = 600L;
    private static final int MAX_TREE_HEIGHT = 20; // 整列砍伐的最大树干高度
    private static final int MAX_LEAF_CLEAR = 24;  // 一次最多清多少片树叶

    private final NpcBase npc;
    private BlockPos workPos;
    private BlockPos workStandPos;
    private BlockPos workApproachPos;
    private BlockPos workScaffoldPos;
    private Block placedScaffoldBlock;
    private boolean scaffoldBuilt;
    private int actionCooldown;
    private int stuckTicks;
    private BlockPos explorePos; // 找不到工作时随机游荡的目标
    private final Map<BlockPos, Long> unreachableUntil = new HashMap<>();

    /** 方块目标和 NPC 实际应该走到的作业站位必须分开。 */
    private record WorkTarget(BlockPos blockPos, BlockPos standPos,
                              BlockPos scaffoldPos, BlockPos approachPos) {
        private WorkTarget(BlockPos blockPos, BlockPos standPos) {
            this(blockPos, standPos, null, null);
        }
    }

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

        clearExpiredUnreachableTargets();
        WorkTarget found = findWork();
        if (found != null) {
            if (!npc.requestMutex(AiMutex.MOVE)) return false;
            applyWorkTarget(found);
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
        scaffoldBuilt = false;
        placedScaffoldBlock = null;
        if (workPos != null) {
            moveToNextWorkPosition();
        } else if (explorePos != null) {
            npc.getNavigation().moveTo(explorePos.getX() + 0.5, explorePos.getY(), explorePos.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        recoverScaffold();
        workPos = null;
        workStandPos = null;
        workApproachPos = null;
        workScaffoldPos = null;
        placedScaffoldBlock = null;
        scaffoldBuilt = false;
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
        if (workScaffoldPos != null && !scaffoldBuilt) {
            tickBuildScaffold();
            return;
        }

        npc.getLookControl().setLookAt(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5);
        double standDistSq = npc.distanceToSqr(
                workStandPos.getX() + 0.5, workStandPos.getY(), workStandPos.getZ() + 0.5);
        double reachDistSq = npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(workPos));
        if (standDistSq <= STAND_REACHED_SQ && reachDistSq <= WORK_REACH_SQ) {
            npc.getNavigation().stop();
            if (--actionCooldown <= 0) {
                actionCooldown = 20;
                mineBlock();
                recoverScaffold();
                workPos = null;
            }
        } else if (npc.getNavigation().isDone()) {
            if (++stuckTicks > 10) {
                // 卡住：目标是原木却被树叶挡路 → 改去清掉离工人最近、挨着树干的树叶，清出路
                if (npc.level().getBlockState(workPos).is(BlockTags.LOGS)) {
                    BlockPos leaf = findLeafNearLog(workPos);
                    if (leaf != null) {
                        WorkTarget leafTarget = createWorkTarget(leaf);
                        if (leafTarget != null) {
                            recoverScaffold();
                            applyWorkTarget(leafTarget);
                            stuckTicks = 0;
                            moveToNextWorkPosition();
                            return;
                        }
                    }
                }
                markTemporarilyUnreachable(workPos);
                recoverScaffold();
                workPos = null;
                workStandPos = null;
            } else {
                moveToWorkStand();
            }
        }
    }

    private void applyWorkTarget(WorkTarget target) {
        this.workPos = target.blockPos();
        this.workStandPos = target.standPos();
        this.workScaffoldPos = target.scaffoldPos();
        this.workApproachPos = target.approachPos();
        this.scaffoldBuilt = false;
        this.placedScaffoldBlock = null;
    }

    private void moveToNextWorkPosition() {
        if (workScaffoldPos != null && !scaffoldBuilt && workApproachPos != null) {
            npc.getNavigation().moveTo(
                    workApproachPos.getX() + 0.5, workApproachPos.getY(), workApproachPos.getZ() + 0.5, 1.0);
        } else {
            moveToWorkStand();
        }
    }

    /** 先站到垫脚台旁边放置材料，再走上高一格的作业站位。 */
    private void tickBuildScaffold() {
        npc.getLookControl().setLookAt(
                workScaffoldPos.getX() + 0.5, workScaffoldPos.getY() + 0.5, workScaffoldPos.getZ() + 0.5);
        double approachDistSq = npc.distanceToSqr(
                workApproachPos.getX() + 0.5, workApproachPos.getY(), workApproachPos.getZ() + 0.5);
        if (approachDistSq <= STAND_REACHED_SQ) {
            npc.getNavigation().stop();
            if (!placeScaffold()) {
                markTemporarilyUnreachable(workPos);
                workPos = null;
                workStandPos = null;
                return;
            }
            scaffoldBuilt = true;
            stuckTicks = 0;
            npc.swing(InteractionHand.MAIN_HAND);
            moveToWorkStand();
        } else if (npc.getNavigation().isDone()) {
            if (++stuckTicks > 10) {
                markTemporarilyUnreachable(workPos);
                workPos = null;
                workStandPos = null;
            } else {
                moveToNextWorkPosition();
            }
        }
    }

    private void moveToWorkStand() {
        if (workStandPos != null) {
            npc.getNavigation().moveTo(
                    workStandPos.getX() + 0.5, workStandPos.getY(), workStandPos.getZ() + 0.5, 1.0);
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
    private WorkTarget findWork() {
        BlockPos center = npc.blockPosition();
        BlockPos fallback = null;
        BlockPos leafFallback = null;
        BlockPos digTarget = null;
        double bestMineDist = Double.MAX_VALUE;
        double bestDigDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                // 可达层：向上扩到 +6；普通站位够不到时允许搭一格临时垫脚台。
                for (int dy = -2; dy <= 6; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!npc.isWorkAllowedAt(p)) continue;
                    if (isTemporarilyUnreachable(p)) continue;
                    BlockState state = npc.level().getBlockState(p);
                    if (state.is(BlockTags.LOGS)) {
                        WorkTarget logTarget = createWorkTarget(p);
                        if (logTarget != null) return logTarget; // 优先砍树
                    }
                    if (isMineTarget(state)) {
                        double d = center.distSqr(p);
                        if (d < bestMineDist) {
                            bestMineDist = d;
                            fallback = p;
                        }
                    }
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
        WorkTarget target = createWorkTarget(leafFallback);
        if (target != null) return target;
        target = createWorkTarget(fallback);
        if (target != null) return target;
        return createWorkTarget(digTarget);
    }

    /**
     * 为目标寻找真正可站立、可触及的作业位置。高处目标会优先使用附近已有的平台，
     * 而不是要求寻路器走进实体方块中心。
     */
    private WorkTarget createWorkTarget(BlockPos target) {
        if (target == null || isTemporarilyUnreachable(target)) return null;

        BlockPos current = npc.blockPosition();
        if (isValidStandPosition(current, target)) {
            return new WorkTarget(target.immutable(), current.immutable());
        }

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -4; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos stand = target.offset(dx, dy, dz);
                    if (!isValidStandPosition(stand, target)) continue;
                    // createPath(null) 代表原版寻路器无法到达该站位。
                    if (npc.getNavigation().createPath(stand, 0) == null) continue;
                    double dist = current.distSqr(stand);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = stand;
                    }
                }
            }
        }

        if (best != null) return new WorkTarget(target.immutable(), best.immutable());

        WorkTarget scaffoldTarget = createScaffoldTarget(target);
        if (scaffoldTarget != null) return scaffoldTarget;

        markTemporarilyUnreachable(target);
        return null;
    }

    private boolean isValidStandPosition(BlockPos stand, BlockPos target) {
        return isWalkableStandPosition(stand) && canReachTargetFromStand(stand, target);
    }

    private boolean isWalkableStandPosition(BlockPos stand) {
        if (!npc.level().isLoaded(stand)) return false;
        BlockState feet = npc.level().getBlockState(stand);
        BlockState head = npc.level().getBlockState(stand.above());
        BlockPos floorPos = stand.below();
        BlockState floor = npc.level().getBlockState(floorPos);
        if (!feet.getCollisionShape(npc.level(), stand).isEmpty()) return false;
        if (!head.getCollisionShape(npc.level(), stand.above()).isEmpty()) return false;
        return floor.isFaceSturdy(npc.level(), floorPos, Direction.UP);
    }

    private boolean canReachTargetFromStand(BlockPos stand, BlockPos target) {
        if (!npc.level().isLoaded(target)) return false;
        Vec3 standEye = new Vec3(
                stand.getX() + 0.5,
                stand.getY() + npc.getEyeHeight(),
                stand.getZ() + 0.5);
        Vec3 targetCenter = Vec3.atCenterOf(target);
        if (standEye.distanceToSqr(targetCenter) > WORK_REACH_SQ) return false;

        BlockHitResult hit = npc.level().clip(new ClipContext(
                standEye, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    /** 为高处矿物规划一个一格高的临时垫脚台。 */
    private WorkTarget createScaffoldTarget(BlockPos target) {
        if (!isMineTarget(npc.level().getBlockState(target)) || findScaffoldSlot() < 0) return null;

        BlockPos current = npc.blockPosition();
        WorkTarget best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -5; dy <= -3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos stand = target.offset(dx, dy, dz);
                    BlockPos scaffold = stand.below();
                    if (!isScaffoldGeometryValid(scaffold, stand, target)) continue;
                    BlockPos approach = findScaffoldApproach(scaffold);
                    if (approach == null) continue;
                    double dist = current.distSqr(approach);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = new WorkTarget(target.immutable(), stand.immutable(),
                                scaffold.immutable(), approach.immutable());
                    }
                }
            }
        }
        return best;
    }

    private boolean isScaffoldGeometryValid(BlockPos scaffold, BlockPos stand, BlockPos target) {
        if (!npc.isWorkAllowedAt(scaffold) || !npc.level().isLoaded(scaffold)) return false;
        if (!npc.level().getBlockState(scaffold).isAir()) return false;
        if (!npc.level().getBlockState(stand).getCollisionShape(npc.level(), stand).isEmpty()) return false;
        if (!npc.level().getBlockState(stand.above()).getCollisionShape(npc.level(), stand.above()).isEmpty()) {
            return false;
        }
        BlockPos supportPos = scaffold.below();
        if (!npc.level().getBlockState(supportPos).isFaceSturdy(npc.level(), supportPos, Direction.UP)) {
            return false;
        }
        return canReachTargetFromStand(stand, target);
    }

    private BlockPos findScaffoldApproach(BlockPos scaffold) {
        BlockPos current = npc.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos approach = scaffold.relative(direction);
            if (!isWalkableStandPosition(approach)) continue;
            if (!approach.equals(current) && npc.getNavigation().createPath(approach, 0) == null) continue;
            double dist = current.distSqr(approach);
            if (dist < bestDist) {
                bestDist = dist;
                best = approach;
            }
        }
        return best;
    }

    private int findScaffoldSlot() {
        var inventory = npc.getEquipmentInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (scaffoldBlockFor(inventory.getItem(i)) != null) return i;
        }
        return -1;
    }

    private static Block scaffoldBlockFor(ItemStack stack) {
        if (stack.is(Items.COBBLESTONE)) return Blocks.COBBLESTONE;
        if (stack.is(Items.COBBLED_DEEPSLATE)) return Blocks.COBBLED_DEEPSLATE;
        if (stack.is(Items.DIRT)) return Blocks.DIRT;
        return null;
    }

    private boolean placeScaffold() {
        if (workScaffoldPos == null || !npc.level().getBlockState(workScaffoldPos).isAir()) return false;
        int slot = findScaffoldSlot();
        if (slot < 0) return false;
        ItemStack stack = npc.getEquipmentInventory().getItem(slot);
        Block block = scaffoldBlockFor(stack);
        if (block == null || !npc.level().setBlock(
                workScaffoldPos, block.defaultBlockState(), Block.UPDATE_ALL)) return false;
        stack.shrink(1);
        placedScaffoldBlock = block;
        return true;
    }

    private void recoverScaffold() {
        if (!scaffoldBuilt || workScaffoldPos == null || placedScaffoldBlock == null) return;
        BlockState state = npc.level().getBlockState(workScaffoldPos);
        if (!state.is(placedScaffoldBlock)) return;
        npc.level().destroyBlock(workScaffoldPos, false, npc);
        ItemStack recovered = new ItemStack(placedScaffoldBlock);
        if (!npc.addToBag(recovered)) npc.spawnAtLocation(recovered);
        scaffoldBuilt = false;
        placedScaffoldBlock = null;
    }

    private boolean isTemporarilyUnreachable(BlockPos pos) {
        Long until = unreachableUntil.get(pos);
        return until != null && until > npc.level().getGameTime();
    }

    private void markTemporarilyUnreachable(BlockPos pos) {
        if (pos != null) {
            unreachableUntil.put(pos.immutable(), npc.level().getGameTime() + UNREACHABLE_COOLDOWN_TICKS);
        }
    }

    private void clearExpiredUnreachableTargets() {
        long now = npc.level().getGameTime();
        unreachableUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
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
