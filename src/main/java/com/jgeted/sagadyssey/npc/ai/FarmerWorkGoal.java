package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.entity.FarmMode;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 农民工作 AI：在 WORK 命令下自动种地。
 * 【测试版：挖沟 + 放水 + 种甘蔗 + 种可可豆原木】挖沟（挖一空二）→ 灌水（满灌）→ 种甘蔗；可可豆模式先种 2 格高原木柱。
 * 挖沟模式：挖一空二，循环（ROW_GAP = 3）。
 */
public class FarmerWorkGoal extends Goal {

    // work kind
    private static final int KIND_TILL = 1;
    private static final int KIND_PLANT = 2;
    private static final int KIND_HARVEST = 3;
    private static final int KIND_DIG = 4;
    private static final int KIND_FLOOD = 5;
    private static final int KIND_REFILL = 6;
    private static final int KIND_PLANT_CANE = 7;
    private static final int KIND_HARVEST_CANE = 8;
    private static final int KIND_PLANT_LOG = 9;
    private static final int KIND_PLANT_COCOA = 10;
    private static final int KIND_HARVEST_COCOA = 11;
    private static final int KIND_HARVEST_PLANT = 12; // 收+原位补种（综合模式）
    private static final int KIND_BONEMEAL = 13;       // 骨粉催熟
    private static final int KIND_CRAFT_BONEMEAL = 14; // 分解骨块/骨头为骨粉
    private static final int KIND_CREATE_IRRIGATION = 15; // 挖一格水坑并放置水源
    private static final int KIND_CREATE_FARM_WELL = 16;  // 两桶水建立 2×2 无限水井

    // 灌溉阶段
    private static final int PHASE_BUILD_WELL = 0;
    private static final int PHASE_DIG_TRENCHES = 1;
    private static final int PHASE_FLOOD_TRENCHES = 2;
    private static final int PHASE_PLANT = 3;

    private static final int SEARCH_RADIUS = 20;
    private static final double WORK_REACH_SQ = 9.0; // 3 格内可作业
    private static final int WELL_LEN = 3;   // 井：3 格长
    private static final int TRENCH_LEN = 9; // 沟：9 格长
    private static final int ROW_GAP = 3;    // 相邻两条沟的垂直间距 = 挖一空二
    private static final int COCOA_LOG_GAP = 4;    // 可可豆原木柱间距：x、z 各空 3 格
    private static final int COCOA_LOG_HEIGHT = 2; // 每根原木柱 2 格高

    private final NpcBase npc;
    private BlockPos workPos;
    private int workKind;
    private int actionCooldown;
    private int stuckTicks;
    private BlockPos explorePos;
    private int lastSeedSlot = -1;

    // === 灌溉状态 ===
    private int phase = PHASE_DIG_TRENCHES; // 【测试版：只挖坑】跳过建井，直接挖沟
    private BlockPos wellPos = null;
    // 井的挖掘进度
    private BlockPos wellStart = null;
    private Direction wellDir = null;
    private int wellDug = 0;
    // 沟的布局与已挖好的沟
    private final List<Trench> trenches = new ArrayList<>();
    private Direction farmDir = null;
    private BlockPos farmOrigin = null;
    private int farmRow = 0;
    private int trenchLen = TRENCH_LEN; // 每条沟的长度（有范围时 = 范围该边整长）
    private int digIndex = -1;      // 正在挖第几条沟
    private int digProgress = 0;    // 当前沟挖了几格
    private int floodIndex = 0;     // 正在灌第几条沟
    private int floodProgress = 0;  // 当前沟灌了几格
    // 可可豆：原木柱种植网格状态
    private BlockPos logOrigin = null; // 网格起点（范围 min 角，地面层）
    private int logCols = 0;           // X 方向能放几根柱子
    private int logRows = 0;           // Z 方向能放几根柱子
    private int logPillar = 0;         // 当前处理到第几根柱子（线性序号）
    private int logStep = 0;           // 当前柱子放到第几格（0=底, 1=顶）
    private boolean logsDone = false;  // 原木柱就绪后转去种可可豆

    /** 一个待做的工作点 */
    private record Work(BlockPos pos, int kind) {}

    /** 一条已挖好的沟 */
    private record Trench(BlockPos start, Direction dir, int len) {}

    public FarmerWorkGoal(NpcBase npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!npc.isAlive() || npc.level().isClientSide) return false;
        if (npc.getProfession() != NpcProfession.FARMER) return false;

        // 调试：每 5 秒打印一次状态。取模相位跟实体 id 对齐，
        // 否则奇数 id 的 NPC 永远打不出日志
        if ((npc.tickCount - npc.getId()) % 100 == 0) {
            debugLogState();
        }

        if (npc.getCommand() != NpcCommand.WORK) return false;
        if (npc.getTarget() != null || npc.isPassenger()) return false;
        // 每 20 tick 扫描一次，相位同样按实体 id 对齐
        if ((npc.tickCount - npc.getId()) % 20 != 0) return false;

        Work work = findWork();
        if ((npc.tickCount - npc.getId()) % 100 == 0) {
            debugLogResult(work);
        }
        if (work != null) {
            if (!npc.requestMutex(AiMutex.MOVE)) return false;
            this.workPos = work.pos();
            this.workKind = work.kind();
            this.explorePos = null;
            return true;
        }

        // 【测试版：只挖坑】挖完就停下，不做游荡探索
        /*
        if (!npc.requestMutex(AiMutex.MOVE)) return false;
        this.workPos = null;
        this.explorePos = randomExplorePos();
        return this.explorePos != null;
        */
        return false;
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

    private void tickWork() {
        npc.getLookControl().setLookAt(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5);
        double distSq = npc.distanceToSqr(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5);
        if (distSq <= WORK_REACH_SQ) {
            npc.getNavigation().stop();
            if (--actionCooldown <= 0) {
                actionCooldown = 20;
                doWork();
                workPos = null;
            }
        } else if (npc.getNavigation().isDone()) {
            if (++stuckTicks > 10) {
                workPos = null;
            } else {
                npc.getNavigation().moveTo(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5, 1.0);
            }
        }
    }

    private void tickExplore() {
        if (npc.getNavigation().isDone()
                || npc.distanceToSqr(explorePos.getX() + 0.5, explorePos.getY(), explorePos.getZ() + 0.5) <= 4.0) {
            explorePos = null;
        }
    }

    /** 按务农模式分流：可可豆/瓜类/农作物/综合各自独立，甘蔗走灌溉管线；正事干完统一兜底用骨粉催熟 */
    private Work findWork() {
        Work work;
        if (npc.getFarmMode() == FarmMode.COCOA) {
            work = findCocoaWork();
        } else if (npc.getFarmMode() == FarmMode.MELON) {
            work = findMelonWork();
        } else if (npc.getFarmMode() == FarmMode.CROP) {
            work = findCropWork();
        } else if (npc.getFarmMode() == FarmMode.AUTO) {
            work = findAutoWork();
        } else {
            work = switch (phase) {
                case PHASE_DIG_TRENCHES -> digTrenches();
                case PHASE_FLOOD_TRENCHES -> floodTrenches();
                case PHASE_PLANT -> findPlantCane();
                default -> null;
            };
        }
        // 各模式正事做完后，用骨粉催熟；没骨粉但有骨头/骨块就先分解
        return work != null ? work : findBonemealWork();
    }

    /** CROP 模式：只种简单农作物（小麦/土豆/胡萝卜/甜菜）。收获→播种→翻地 */
    private Work findCropWork() {
        BlockPos min = npc.hasWorkZone() ? npc.getWorkZoneMin() : npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
        BlockPos max = npc.hasWorkZone() ? npc.getWorkZoneMax() : npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        int groundY = npc.hasWorkZone() ? npc.getWorkZoneMin().getY() : npc.blockPosition().below().getY();

        Work plant = null;
        Work till = null;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                // 耕地在地面层，作物在上一层
                for (int y = groundY; y <= groundY + 1; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = npc.level().getBlockState(p);

                    // 收成熟的简单作物（明确列 4 种，避免把瓜类茎也算进来）
                    if ((s.is(Blocks.WHEAT) || s.is(Blocks.CARROTS) || s.is(Blocks.POTATOES) || s.is(Blocks.BEETROOTS))
                            && s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) {
                        return new Work(p, KIND_HARVEST);
                    }
                    // 播种：耕地 + 上方空 + 瓜茎旁不种（留结果位）+ 有简单作物种子
                    if (s.is(Blocks.FARMLAND) && npc.level().getBlockState(p.above()).isAir()
                            && !isNearMelonStem(p.above())
                            && findSeedSlot() >= 0) {
                        if (plant == null) plant = new Work(p, KIND_PLANT);
                    }
                    // 翻地：近水泥土/草方块 + 手里有锄头
                    if ((s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK))
                            && npc.level().getBlockState(p.above()).isAir()
                            && nearWater(p)
                            && npc.getMainHandItem().getItem() instanceof HoeItem) {
                        if (till == null) till = new Work(p, KIND_TILL);
                    }
                }
            }
        }
        Work irrigation = findIrrigationWork(min, max, groundY);
        if (irrigation != null) return irrigation;
        if (plant != null) return plant;
        if (till != null) return till;
        return null;
    }

    /** MELON 模式：只种瓜类（西瓜/南瓜）。收获果实→种茎（种一空一）→翻地；茎留在原地继续结瓜 */
    private Work findMelonWork() {
        BlockPos min = npc.hasWorkZone() ? npc.getWorkZoneMin() : npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
        BlockPos max = npc.hasWorkZone() ? npc.getWorkZoneMax() : npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        int groundY = npc.hasWorkZone() ? npc.getWorkZoneMin().getY() : npc.blockPosition().below().getY();

        Work plant = null;
        Work till = null;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = groundY; y <= groundY + 1; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = npc.level().getBlockState(p);

                    // 收果实：只收西瓜/南瓜本体，茎留在原地继续结瓜
                    if (s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)) {
                        return new Work(p, KIND_HARVEST);
                    }
                    // 种茎：耕地 + 上方空 + 种一空一（棋盘格，茎四周留空位结果实）
                    //      + 茎旁有能结果实的空位 + 有瓜种
                    if (s.is(Blocks.FARMLAND) && npc.level().getBlockState(p.above()).isAir()
                            && ((x - min.getX() + z - min.getZ()) % 2 == 0)
                            && hasMelonSpot(p.above())
                            && findMelonSeedSlot() >= 0) {
                        if (plant == null) plant = new Work(p, KIND_PLANT);
                    }
                    // 翻地
                    if ((s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK))
                            && npc.level().getBlockState(p.above()).isAir()
                            && nearWater(p)
                            && npc.getMainHandItem().getItem() instanceof HoeItem) {
                        if (till == null) till = new Work(p, KIND_TILL);
                    }
                }
            }
        }
        Work irrigation = findIrrigationWork(min, max, groundY);
        if (irrigation != null) return irrigation;
        if (plant != null) return plant;
        if (till != null) return till;
        return null;
    }

    /** AUTO 模式：全自动田间管理。收甘蔗/可可豆/瓜/成熟作物（收+原位补种）→ 播种 → 翻地 */
    private Work findAutoWork() {
        BlockPos min = npc.hasWorkZone() ? npc.getWorkZoneMin() : npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
        BlockPos max = npc.hasWorkZone() ? npc.getWorkZoneMax() : npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        int groundY = npc.hasWorkZone() ? npc.getWorkZoneMin().getY() : npc.blockPosition().below().getY();

        // 先收甘蔗：3 格高才收，砍上两节留底节继续长
        Work cane = findCaneHarvest(min, max, groundY);
        if (cane != null) return cane;

        // 收/补种可可豆：收成熟豆 + 在已有柱子空侧面补种，不新搭柱子
        Work cocoa = findCocoaPodWork();
        if (cocoa != null) return cocoa;

        Work plant = null;
        Work till = null;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                // 耕地在地面层，作物在上一层
                for (int y = groundY; y <= groundY + 1; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = npc.level().getBlockState(p);

                    // 收瓜果实：只收果实，茎留原地继续结瓜
                    if (s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)) {
                        return new Work(p, KIND_HARVEST);
                    }
                    // 收+补种简单作物：成熟的小麦/土豆/胡萝卜/甜菜
                    if ((s.is(Blocks.WHEAT) || s.is(Blocks.CARROTS) || s.is(Blocks.POTATOES) || s.is(Blocks.BEETROOTS))
                            && s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) {
                        return new Work(p, KIND_HARVEST_PLANT);
                    }
                    // 播种：空耕地（补种失败兜底 + 开垦后补种）；瓜茎旁一格不种，留空位结果实
                    if (s.is(Blocks.FARMLAND) && npc.level().getBlockState(p.above()).isAir()
                            && !isNearMelonStem(p.above())
                            && findSeedSlot() >= 0) {
                        if (plant == null) plant = new Work(p, KIND_PLANT);
                    }
                    // 翻地：近水泥土/草方块（踩坏耕地 + 新开垦）
                    if ((s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK))
                            && npc.level().getBlockState(p.above()).isAir()
                            && nearWater(p)
                            && npc.getMainHandItem().getItem() instanceof HoeItem) {
                        if (till == null) till = new Work(p, KIND_TILL);
                    }
                }
            }
        }
        Work irrigation = findIrrigationWork(min, max, groundY);
        if (irrigation != null) return irrigation;
        if (plant != null) return plant;
        if (till != null) return till;
        return null;
    }

    /**
     * 普通农田灌溉：一个水源负责同层 9×9 的区域。农民有水桶时会自己挖坑放水；
     * 只有空桶时，会先去附近的无限水源补水，再回来继续规划。
     */
    private Work findIrrigationWork(BlockPos min, BlockPos max, int groundY) {
        BlockPos dryGround = findDryFarmGround(min, max, groundY);
        if (dryGround == null) return null;

        BlockPos irrigationPos = planIrrigationPos(dryGround, min, max, groundY);
        if (irrigationPos == null) return null;

        BlockPos infiniteSource = findInfiniteWaterNear();
        if (infiniteSource == null && hasTwoWaterBuckets()) {
            BlockPos wellCorner = findFarmWellCorner(irrigationPos, min, max, groundY);
            if (wellCorner != null) return new Work(wellCorner, KIND_CREATE_FARM_WELL);
        }
        if (hasWaterBucket()) {
            return new Work(irrigationPos, KIND_CREATE_IRRIGATION);
        }
        if (hasEmptyBucket() && infiniteSource != null) {
            return new Work(infiniteSource, KIND_REFILL);
        }
        // 没有可用水源或水桶时保持原地等待，玩家只需提供水桶，不必亲自摆水。
        return null;
    }

    /** 找到需要灌溉的耕地或可开垦地。 */
    private BlockPos findDryFarmGround(BlockPos min, BlockPos max, int groundY) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = groundY; y <= groundY + 1; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!npc.isWorkAllowedAt(p) || nearWater(p)) continue;
                    BlockState ground = npc.level().getBlockState(p);
                    BlockState above = npc.level().getBlockState(p.above());
                    boolean farmable = ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK)
                            || ground.is(Blocks.FARMLAND);
                    boolean usableAbove = above.isAir() || above.getBlock() instanceof CropBlock;
                    if (farmable && usableAbove) return p;
                }
            }
        }
        return null;
    }

    /**
     * 以工作区起点切成 9×9 单元并优先选单元中心；中心不能挖时，在本单元中找最近的空地。
     * 没有工作区时使用世界坐标网格，避免 NPC 走动导致规划点不断漂移。
     */
    private BlockPos planIrrigationPos(BlockPos dryGround, BlockPos min, BlockPos max, int groundY) {
        int tileStartX = npc.hasWorkZone()
                ? min.getX() + ((dryGround.getX() - min.getX()) / 9) * 9
                : Math.floorDiv(dryGround.getX(), 9) * 9;
        int tileStartZ = npc.hasWorkZone()
                ? min.getZ() + ((dryGround.getZ() - min.getZ()) / 9) * 9
                : Math.floorDiv(dryGround.getZ(), 9) * 9;
        int tileEndX = Math.min(tileStartX + 8, max.getX());
        int tileEndZ = Math.min(tileStartZ + 8, max.getZ());
        int centerX = (tileStartX + tileEndX) / 2;
        int centerZ = (tileStartZ + tileEndZ) / 2;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = tileStartX; x <= tileEndX; x++) {
            for (int z = tileStartZ; z <= tileEndZ; z++) {
                // 这个水源必须能够覆盖触发规划的那格土地。
                if (Math.abs(x - dryGround.getX()) > 4 || Math.abs(z - dryGround.getZ()) > 4) continue;
                BlockPos candidate = new BlockPos(x, groundY, z);
                if (!isIrrigationCell(candidate)) continue;
                double dist = candidate.distSqr(new BlockPos(centerX, groundY, centerZ));
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private boolean isIrrigationCell(BlockPos pos) {
        if (!npc.isWorkAllowedAt(pos) || !npc.level().isLoaded(pos)) return false;
        BlockState state = npc.level().getBlockState(pos);
        return (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.FARMLAND))
                && npc.level().getBlockState(pos.above()).isAir()
                && npc.level().getBlockState(pos.below()).isSolid();
    }

    /** 在规划灌溉点附近找一块完整的 2×2 空地，用两桶水建立可重复打水的水井。 */
    private BlockPos findFarmWellCorner(BlockPos irrigationPos, BlockPos min, BlockPos max, int groundY) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int ox = -1; ox <= 0; ox++) {
            for (int oz = -1; oz <= 0; oz++) {
                BlockPos corner = new BlockPos(irrigationPos.getX() + ox, groundY, irrigationPos.getZ() + oz);
                if (corner.getX() < min.getX() || corner.getZ() < min.getZ()
                        || corner.getX() + 1 > max.getX() || corner.getZ() + 1 > max.getZ()) continue;
                if (!isIrrigationCell(corner)
                        || !isIrrigationCell(corner.east())
                        || !isIrrigationCell(corner.south())
                        || !isIrrigationCell(corner.east().south())) continue;
                double dist = corner.distSqr(irrigationPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = corner;
                }
            }
        }
        return best;
    }

    /** 找一根 3 格高的甘蔗（底节 + 上面两节），返回底节位置或 null */
    private Work findCaneHarvest(BlockPos min, BlockPos max, int groundY) {
        int caneY = groundY + 1; // 甘蔗底节所在层
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                BlockPos base = new BlockPos(x, caneY, z);
                if (!npc.level().getBlockState(base).is(Blocks.SUGAR_CANE)) continue;
                if (npc.level().getBlockState(base.below()).is(Blocks.SUGAR_CANE)) continue; // 不是底节
                if (!npc.level().getBlockState(base.above()).is(Blocks.SUGAR_CANE)) continue;   // 没第二节
                if (!npc.level().getBlockState(base.above(2)).is(Blocks.SUGAR_CANE)) continue; // 没第三节
                return new Work(base, KIND_HARVEST_CANE);
            }
        }
        return null;
    }

    /** 找可催熟的农田植物（作物/茎/甘蔗/可可豆）；没骨粉但有骨头/骨块则先分解 */
    private Work findBonemealWork() {
        BlockPos min = npc.hasWorkZone() ? npc.getWorkZoneMin() : npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
        BlockPos max = npc.hasWorkZone() ? npc.getWorkZoneMax() : npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        int groundY = npc.hasWorkZone() ? npc.getWorkZoneMin().getY() : npc.blockPosition().below().getY();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                // 植物长在地面层往上（作物/茎/甘蔗在 +1，可可豆贴在柱子上可达 +2）
                for (int y = groundY + 1; y <= groundY + 2; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = npc.level().getBlockState(p);
                    // 只催农田植物，别浪费在草地/树苗上
                    if (!(s.getBlock() instanceof CropBlock)
                            && s.getBlock() != Blocks.SUGAR_CANE
                            && s.getBlock() != Blocks.COCOA) {
                        continue;
                    }
                    if (s.getBlock() instanceof BonemealableBlock bm
                            && bm.isValidBonemealTarget(npc.level(), p, s)) {
                        if (hasBoneMeal()) return new Work(p, KIND_BONEMEAL);
                        if (hasBoneMaterial()) return new Work(p, KIND_CRAFT_BONEMEAL);
                        return null; // 有目标但没材料，等玩家给
                    }
                }
            }
        }
        return null;
    }

    /** 用骨粉催熟 workPos 上的植物（复刻原版骨粉效果），催熟成功才消耗 1 个骨粉 */
    private void useBoneMeal() {
        BlockPos pos = workPos;
        BlockState state = npc.level().getBlockState(pos);
        if (!(state.getBlock() instanceof BonemealableBlock bm)) return;
        if (!(npc.level() instanceof ServerLevel serverLevel)) return;
        if (!bm.isValidBonemealTarget(serverLevel, pos, state)) return;
        int slot = findItemSlot(Items.BONE_MEAL);
        if (slot < 0) return;
        if (bm.isBonemealSuccess(serverLevel, npc.getRandom(), pos, state)) {
            bm.performBonemeal(serverLevel, npc.getRandom(), pos, state);
            npc.getEquipmentInventory().getItem(slot).shrink(1);
        }
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 分解一个骨块（→9 骨粉）或一根骨头（→3 骨粉），优先骨块 */
    private void craftBoneMeal() {
        int blockSlot = findItemSlot(Items.BONE_BLOCK);
        if (blockSlot >= 0) {
            npc.getEquipmentInventory().getItem(blockSlot).shrink(1);
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 9);
            if (!npc.addToBag(meal)) npc.spawnAtLocation(meal);
            npc.swing(InteractionHand.MAIN_HAND);
            return;
        }
        int boneSlot = findItemSlot(Items.BONE);
        if (boneSlot >= 0) {
            npc.getEquipmentInventory().getItem(boneSlot).shrink(1);
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 3);
            if (!npc.addToBag(meal)) npc.spawnAtLocation(meal);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean hasBoneMeal() { return findItemSlot(Items.BONE_MEAL) >= 0; }
    private boolean hasBoneMaterial() {
        return findItemSlot(Items.BONE) >= 0 || findItemSlot(Items.BONE_BLOCK) >= 0;
    }

    /** 可可豆阶段：先确保原木柱就绪，再在柱侧面种可可豆 */
    private Work findCocoaWork() {
        if (!logsDone) {
            Work log = ensureCocoaLogs();
            if (log != null) return log;
            logsDone = true;
        }
        return findCocoaPodWork();
    }

    /** 确保范围内有原木柱（没有就用背包里的原木/木种 2 格高竖柱） */
    private Work ensureCocoaLogs() {
        // 还没开始种：先看范围内是否已有载体（丛林原木/丛林木，有就不种），再看背包有没有材料
        if (logOrigin == null) {
            if (findJungleLogInZone() != null) return null; // 已有载体，无需补种
            if (findJungleLogSlot() < 0) return null; // 没原木/木，等玩家给

            BlockPos min;
            BlockPos max;
            int groundY;
            if (npc.hasWorkZone()) {
                min = npc.getWorkZoneMin();
                max = npc.getWorkZoneMax();
                groundY = min.getY();
            } else {
                min = npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
                max = npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
                groundY = npc.blockPosition().below().getY();
            }
            logOrigin = new BlockPos(min.getX(), groundY, min.getZ());
            logCols = (max.getX() - min.getX()) / COCOA_LOG_GAP + 1;
            logRows = (max.getZ() - min.getZ()) / COCOA_LOG_GAP + 1;
            logPillar = 0;
            logStep = 0;
        }

        // 逐根柱子放：x、z 各空 3 格（间距 COCOA_LOG_GAP=4），每柱 2 格高、竖着放
        while (true) {
            if (logPillar >= logCols * logRows) return null; // 全部种完
            int col = logPillar % logCols;
            int row = logPillar / logCols;
            // 柱子底部 = 地面层 +1（柱子坐在泥土上）
            BlockPos base = new BlockPos(
                    logOrigin.getX() + col * COCOA_LOG_GAP,
                    logOrigin.getY() + 1,
                    logOrigin.getZ() + row * COCOA_LOG_GAP);
            BlockPos target = base.above(logStep);
            if (!npc.level().getBlockState(target).isAir()) {
                logPillar++; // 这个格子被占了，换下一根柱子
                logStep = 0;
                continue;
            }
            if (++logStep >= COCOA_LOG_HEIGHT) {
                logPillar++;
                logStep = 0;
            }
            return new Work(target, KIND_PLANT_LOG);
        }
    }

    /** 可可豆收/种：先收成熟（age=2）的可可豆，再找空侧面贴新的 */
    private Work findCocoaPodWork() {
        BlockPos min = npc.hasWorkZone() ? npc.getWorkZoneMin() : npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
        BlockPos max = npc.hasWorkZone() ? npc.getWorkZoneMax() : npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        int baseY = npc.hasWorkZone() ? npc.getWorkZoneMin().getY() : npc.blockPosition().below().getY();

        // 先扫收获：成熟（age=2）的可可豆
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = baseY; y <= baseY + 3; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = npc.level().getBlockState(p);
                    if (s.is(Blocks.COCOA) && s.getValue(CocoaBlock.AGE) >= 2) {
                        return new Work(p, KIND_HARVEST_COCOA);
                    }
                }
            }
        }

        // 再扫种植：柱子侧面空位贴新的
        if (!hasCocoaBeans()) return null; // 没可可豆，等玩家给
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = baseY; y <= baseY + 3; y++) {
                    BlockPos logPos = new BlockPos(x, y, z);
                    if (!isJungleLogBlock(npc.level().getBlockState(logPos))) continue;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos podPos = logPos.relative(dir);
                        // 只判 X/Z：可可豆贴在柱子那一层，Y 比圈定的地面层高，
                        // 不能用 3D 的 isWorkAllowedAt（会把所有贴位都判成出界）
                        if (podPos.getX() < min.getX() || podPos.getX() > max.getX()
                                || podPos.getZ() < min.getZ() || podPos.getZ() > max.getZ()) continue;
                        if (!npc.level().getBlockState(podPos).isAir()) continue;
                        if (!cocoaCanSurviveAt(podPos, dir.getOpposite())) continue; // 原版只认丛林原木
                        return new Work(podPos, KIND_PLANT_COCOA);
                    }
                }
            }
        }
        return null;
    }

    /** 判断可可豆能否贴在该位置（朝向贴回原木那一面）；原版只认丛林原木，丛林木返回 false */
    private boolean cocoaCanSurviveAt(BlockPos podPos, Direction facing) {
        BlockState cocoa = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, facing);
        return cocoa.canSurvive(npc.level(), podPos);
    }

    /** 在范围内找任意一个已存在的可可豆载体（丛林原木/丛林木，找到说明不用补种） */
    private BlockPos findJungleLogInZone() {
        BlockPos min = npc.hasWorkZone() ? npc.getWorkZoneMin() : npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
        BlockPos max = npc.hasWorkZone() ? npc.getWorkZoneMax() : npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        int baseY = npc.hasWorkZone() ? npc.getWorkZoneMin().getY() : npc.blockPosition().below().getY();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = baseY; y <= baseY + 3; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (isJungleLogBlock(npc.level().getBlockState(p))) return p;
                }
            }
        }
        return null;
    }

    /** 丛林原木/丛林木及其去皮变体都能作可可豆载体 */
    private boolean isJungleLogBlock(BlockState state) {
        return state.is(Blocks.JUNGLE_LOG) || state.is(Blocks.JUNGLE_WOOD)
                || state.is(Blocks.STRIPPED_JUNGLE_LOG) || state.is(Blocks.STRIPPED_JUNGLE_WOOD);
    }

    /** 种植/收获阶段：先收 3 格高的甘蔗（砍第二节+第三节、留底节继续长），再找空位种甘蔗 */
    private Work findPlantCane() {
        BlockPos min = npc.hasWorkZone() ? npc.getWorkZoneMin() : npc.blockPosition().offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS);
        BlockPos max = npc.hasWorkZone() ? npc.getWorkZoneMax() : npc.blockPosition().offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        int groundY = npc.hasWorkZone() ? npc.getWorkZoneMin().getY() : npc.blockPosition().below().getY();
        int caneY = groundY + 1; // 甘蔗底节所在层

        // 先扫收获：底节 + 上面两节都是甘蔗（即 3 格高）才收
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                BlockPos base = new BlockPos(x, caneY, z);
                if (!npc.level().getBlockState(base).is(Blocks.SUGAR_CANE)) continue;
                if (npc.level().getBlockState(base.below()).is(Blocks.SUGAR_CANE)) continue; // 不是底节，跳过
                if (!npc.level().getBlockState(base.above()).is(Blocks.SUGAR_CANE)) continue;   // 没第二节
                if (!npc.level().getBlockState(base.above(2)).is(Blocks.SUGAR_CANE)) continue; // 没第三节（不到 3 格）
                return new Work(base, KIND_HARVEST_CANE);
            }
        }

        // 再扫种植
        if (!hasSugarcane()) return null; // 没甘蔗苗就不种

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                BlockPos soil = new BlockPos(x, groundY, z);
                BlockState state = npc.level().getBlockState(soil);
                if (state.is(Blocks.SUGAR_CANE)) continue; // 已经种了
                if (state.is(Blocks.WATER)) continue;       // 沟里的水，跳过
                if (!npc.level().getBlockState(soil.above()).isAir()) continue; // 上方要有空位
                if (!Blocks.SUGAR_CANE.defaultBlockState().canSurvive(npc.level(), soil.above())) continue; // 检测能否活
                return new Work(soil, KIND_PLANT_CANE);
            }
        }
        return null; // 范围内种完了（或没有可种位置）
    }


    /** 【测试版：只挖坑】灌溉状态机整段注释（建井 + 灌水不再参与） */
    /*
    private Work irrigationWork(boolean hasCane) {
        if (!hasCane) return null; // 没甘蔗就不折腾灌溉
        return switch (phase) {
            case PHASE_BUILD_WELL -> buildWell();
            case PHASE_DIG_TRENCHES -> digTrenches();
            case PHASE_FLOOD_TRENCHES -> floodTrenches();
            default -> null; // PHASE_PLANT
        };
    }
    */

    /** 【测试版：只挖坑】建井整段注释 */
    /*
    private Work buildWell() {
        if (wellPos != null) {
            phase = PHASE_DIG_TRENCHES;
            return null;
        }
        if (!hasTwoWaterBuckets()) return null; // 等玩家给 2 桶水

        if (wellStart == null) {
            wellStart = findLineNear(Direction.EAST, npc.blockPosition().below(), WELL_LEN);
            wellDir = Direction.EAST;
            if (wellStart == null) {
                wellDir = Direction.SOUTH;
                wellStart = findLineNear(wellDir, npc.blockPosition().below(), WELL_LEN);
            }
            if (wellStart == null) return null;
        }
        if (wellDug < WELL_LEN) {
            return new Work(wellStart.relative(wellDir, wellDug++), KIND_DIG);
        }
        BlockPos endA = wellStart;
        BlockPos endB = wellStart.relative(wellDir, WELL_LEN - 1);
        if (!npc.level().getFluidState(endA).is(FluidTags.WATER)) {
            return new Work(endA, KIND_FLOOD);
        }
        if (!npc.level().getFluidState(endB).is(FluidTags.WATER)) {
            return new Work(endB, KIND_FLOOD);
        }
        wellPos = wellStart.relative(wellDir, WELL_LEN / 2);
        return null; // 井完成，下轮进入挖沟
    }
    */

    /** 挖沟阶段：一条接一条挖（挖一空二，循环），直到范围内再也找不到可挖直线 */
    private Work digTrenches() {
        if (digIndex >= 0) {
            Trench t = trenches.get(digIndex);
            if (digProgress < t.len()) {
                return new Work(t.start().relative(t.dir(), digProgress++), KIND_DIG);
            }
            digIndex = -1; // 这条挖完，找下一条
        }
        Trench next = findNextTrench();
        if (next == null) {
            // 全部挖完，进入灌水阶段
            phase = PHASE_FLOOD_TRENCHES;
            floodIndex = 0;
            floodProgress = 0;
            return null;
        }
        trenches.add(next);
        digIndex = trenches.size() - 1;
        digProgress = 0;
        return new Work(next.start().relative(next.dir(), digProgress++), KIND_DIG);
    }

    /** 灌水阶段：逐条沟、每格都放成静止水源（满灌）；没桶了就去附近无限水打水 */
    private Work floodTrenches() {
        while (floodIndex < trenches.size()) {
            Trench t = trenches.get(floodIndex);
            if (floodProgress >= t.len()) {
                floodIndex++;
                floodProgress = 0;
                continue;
            }
            BlockPos p = t.start().relative(t.dir(), floodProgress);
            // 只要还不是静止水源（空气或流动水），就放一桶补成水源
            var fluid = npc.level().getFluidState(p);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                floodProgress++; // 已经是静止水源，跳过
                continue;
            }
            if (!hasWaterBucket()) {
                BlockPos water = findInfiniteWaterNear();
                if (water == null) return null; // 附近没有无限水，先停下等玩家补
                return new Work(water, KIND_REFILL); // 去打水，进度不动
            }
            floodProgress++;
            return new Work(p, KIND_FLOOD);
        }
        phase = PHASE_PLANT; // 全部灌完，进入种植（暂未实现，先闲着）
        return null;
    }

    /** 在 NPC 附近找一处无限水源（水源块 + 至少 2 个相邻水源块） */
    private BlockPos findInfiniteWaterNear() {
        BlockPos center = npc.blockPosition();
        for (int r = 0; r <= 20; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = center.offset(dx, dy, dz);
                        if (isInfiniteWater(p)) return p;
                    }
                }
            }
        }
        return null;
    }

    private boolean isInfiniteWater(BlockPos p) {
        var fluid = npc.level().getFluidState(p);
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) return false;
        int sources = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var f = npc.level().getFluidState(p.relative(side));
            if (f.is(FluidTags.WATER) && f.isSource()) sources++;
        }
        return sources >= 2;
    }

    /** 找下一条可挖的沟：锚定范围 min 角，沿较长边一排挖到底，垂直隔 ROW_GAP（挖一空二）推进，出范围即停 */
    private Trench findNextTrench() {
        if (farmDir == null) {
            if (npc.hasWorkZone()) {
                // 从范围 min 角起，长度 = 该边整长，方向取较长边
                BlockPos min = npc.getWorkZoneMin();
                BlockPos max = npc.getWorkZoneMax();
                int widthX = max.getX() - min.getX() + 1;
                int depthZ = max.getZ() - min.getZ() + 1;
                if (widthX >= depthZ) {
                    farmDir = Direction.EAST;
                    trenchLen = widthX;
                } else {
                    farmDir = Direction.SOUTH;
                    trenchLen = depthZ;
                }
                farmOrigin = min;
                farmRow = 0;
            } else {
                // 没设范围：退回旧逻辑，从 NPC 脚下就近找一条固定长直线
                BlockPos start = findLineNear(Direction.EAST, npc.blockPosition().below(), TRENCH_LEN);
                Direction dir = Direction.EAST;
                if (start == null) {
                    dir = Direction.SOUTH;
                    start = findLineNear(dir, npc.blockPosition().below(), TRENCH_LEN);
                }
                if (start == null) return null;
                farmDir = dir;
                farmOrigin = start;
                farmRow = 0;
                trenchLen = TRENCH_LEN;
            }
        }
        BlockPos start = farmOrigin.relative(farmDir.getClockWise(), farmRow * ROW_GAP);
        // 垂直方向越界 → 整片挖完，停下
        if (!npc.isWorkAllowedAt(start)) return null;
        farmRow++;
        return new Trench(start, farmDir, trenchLen);
    }

    /** 在 pos 附近（同一层）找一条沿 dir、len 格连续可挖的直线，返回起点或 null */
    private BlockPos findLineNear(Direction dir, BlockPos pos, int len) {
        int radius = 20;
        for (int r = 0; r < radius; r++) {
            for (int d = -radius; d <= radius; d++) {
                BlockPos base = pos.relative(dir, d).relative(dir.getClockWise(), r);
                boolean ok = true;
                for (int i = 0; i < len; i++) {
                    BlockPos p = base.relative(dir, i);
                    if (!npc.isWorkAllowedAt(p)) { ok = false; break; }
                    if (!isDiggableGround(p)) { ok = false; break; }
                    // 附近有水的就跳过（防止挖进已放水的沟）
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        if (npc.level().getFluidState(p.relative(side)).is(FluidTags.WATER)) { ok = false; break; }
                    }
                    if (!ok) break;
                }
                if (ok) return base;
            }
        }
        return null;
    }

    private void doWork() {
        switch (workKind) {
            case KIND_DIG -> {
                digGround(workPos);
                npc.swing(InteractionHand.MAIN_HAND);
            }
            case KIND_FLOOD -> {
                placeWater(workPos);
                npc.swing(InteractionHand.MAIN_HAND);
            }
            case KIND_REFILL -> refillBucket(workPos);
            case KIND_PLANT_CANE -> plantCane();
            case KIND_HARVEST_CANE -> harvestCane(workPos);
            case KIND_PLANT_LOG -> plantLog();
            case KIND_PLANT_COCOA -> plantCocoa();
            case KIND_HARVEST_COCOA -> harvestCocoa();
            case KIND_HARVEST_PLANT -> harvestAndReplant();
            case KIND_BONEMEAL -> useBoneMeal();
            case KIND_CRAFT_BONEMEAL -> craftBoneMeal();
            case KIND_CREATE_IRRIGATION -> createIrrigationSource();
            case KIND_CREATE_FARM_WELL -> createFarmWell();
            case KIND_HARVEST -> harvest();
            case KIND_PLANT -> plant();
            case KIND_TILL -> till();
        }
    }

    /** 收获：打掉 workPos 上的方块，掉落进背包（简单作物成熟时收；瓜只收果实，茎留在原地） */
    private void harvest() {
        harvestBlock(workPos);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 播种：在 workPos（耕地）上方种一格（CROP：农作物；MELON：瓜茎；AUTO：优先邻居作物） */
    private void plant() {
        BlockPos cropPos = workPos.above();
        if (!npc.level().getBlockState(workPos).is(Blocks.FARMLAND)) return;
        if (!npc.level().getBlockState(cropPos).isAir()) return;

        int slot;
        Block crop;
        if (npc.getFarmMode() == FarmMode.MELON) {
            slot = findMelonSeedSlot();
            crop = slot >= 0 ? melonStemForSeed(npc.getEquipmentInventory().getItem(slot)) : null;
        } else if (npc.getFarmMode() == FarmMode.AUTO) {
            Block neighbor = cropForNeighbor(workPos);
            slot = neighbor != null ? findItemSlot(seedForCrop(neighbor)) : -1;
            if (slot >= 0) {
                crop = neighbor;
            } else {
                slot = findSeedSlot();
                crop = slot >= 0 ? cropForSeed(npc.getEquipmentInventory().getItem(slot)) : null;
            }
        } else {
            slot = findSeedSlot();
            crop = slot >= 0 ? cropForSeed(npc.getEquipmentInventory().getItem(slot)) : null;
        }
        if (crop == null) return;
        lastSeedSlot = slot;
        npc.getEquipmentInventory().getItem(slot).shrink(1);
        npc.level().setBlock(cropPos, crop.defaultBlockState(), Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 翻地：把近水的泥土/草方块锄成耕地 */
    private void till() {
        BlockState state = npc.level().getBlockState(workPos);
        if ((state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))
                && npc.level().getBlockState(workPos.above()).isAir()
                && npc.getMainHandItem().getItem() instanceof HoeItem) {
            npc.level().setBlock(workPos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** 一次动作完成挖坑和放水，避免服务器重启后留下无法识别的半成品水坑。 */
    private void createIrrigationSource() {
        if (!isIrrigationCell(workPos) || !hasWaterBucket()) return;
        boolean dug = digIrrigationCell(workPos);
        if (dug && npc.level().getBlockState(workPos).isAir()) {
            placeWater(workPos);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** 挖好 2×2 水井并在对角放两桶水，其余两格由原版流体规则形成无限水源。 */
    private void createFarmWell() {
        if (!hasTwoWaterBuckets()) return;
        BlockPos east = workPos.east();
        BlockPos south = workPos.south();
        BlockPos diagonal = east.south();
        if (!isIrrigationCell(workPos) || !isIrrigationCell(east)
                || !isIrrigationCell(south) || !isIrrigationCell(diagonal)) return;

        if (!digIrrigationCell(workPos) || !digIrrigationCell(east)
                || !digIrrigationCell(south) || !digIrrigationCell(diagonal)) return;
        if (placeWater(workPos)) {
            placeWater(diagonal);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean digIrrigationCell(BlockPos pos) {
        BlockState state = npc.level().getBlockState(pos);
        if (state.is(Blocks.FARMLAND)) {
            npc.level().destroyBlock(pos, false, npc);
            return true;
        }
        return digGround(pos);
    }

    /** 种甘蔗：在 workPos（土壤）上方种一格甘蔗苗 */
    private void plantCane() {
        BlockPos canePos = workPos.above();
        if (!npc.level().getBlockState(canePos).isAir()) return;
        if (npc.level().getBlockState(workPos).is(Blocks.SUGAR_CANE)) return;
        if (!Blocks.SUGAR_CANE.defaultBlockState().canSurvive(npc.level(), canePos)) return;
        int slot = findItemSlot(Items.SUGAR_CANE);
        if (slot < 0) return;
        npc.getEquipmentInventory().getItem(slot).shrink(1);
        npc.level().setBlock(canePos, Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 种一根丛林原木/丛林木柱（竖着放 axis=Y），从背包扣 1 根对应材料；去皮变体也支持 */
    private void plantLog() {
        BlockPos pos = workPos;
        if (!npc.level().getBlockState(pos).isAir()) return;
        int slot = findJungleLogSlot();
        if (slot < 0) return;
        ItemStack stack = npc.getEquipmentInventory().getItem(slot);
        // 按给的物品放对应方块（原木/木/去皮原木/去皮木，都竖着放）
        Block pillar;
        if (stack.is(Items.STRIPPED_JUNGLE_WOOD)) {
            pillar = Blocks.STRIPPED_JUNGLE_WOOD;
        } else if (stack.is(Items.STRIPPED_JUNGLE_LOG)) {
            pillar = Blocks.STRIPPED_JUNGLE_LOG;
        } else if (stack.is(Items.JUNGLE_WOOD)) {
            pillar = Blocks.JUNGLE_WOOD;
        } else {
            pillar = Blocks.JUNGLE_LOG;
        }
        stack.shrink(1);
        BlockState vertical = pillar.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        npc.level().setBlock(pos, vertical, Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 找背包里第一个丛林原木/丛林木（含去皮变体），返回槽位或 -1 */
    private int findJungleLogSlot() {
        var inv = npc.getEquipmentInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it.is(Items.JUNGLE_LOG) || it.is(Items.JUNGLE_WOOD)
                    || it.is(Items.STRIPPED_JUNGLE_LOG) || it.is(Items.STRIPPED_JUNGLE_WOOD)) return i;
        }
        return -1;
    }

    /** 种可可豆：在 workPos 侧面贴一个 age=0 的可可豆，消耗 1 个可可豆 */
    private void plantCocoa() {
        BlockPos podPos = workPos;
        if (!npc.level().getBlockState(podPos).isAir()) return;
        // 找贴着的载体，确定朝向（FACING 指向原木那一面）
        Direction facing = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (isJungleLogBlock(npc.level().getBlockState(podPos.relative(dir)))) {
                facing = dir;
                break;
            }
        }
        if (facing == null) return;
        BlockState cocoa = Blocks.COCOA.defaultBlockState()
                .setValue(CocoaBlock.FACING, facing)
                .setValue(CocoaBlock.AGE, 0);
        if (!cocoa.canSurvive(npc.level(), podPos)) return; // 原版只认丛林原木，丛林木贴不上
        int slot = findItemSlot(Items.COCOA_BEANS);
        if (slot < 0) return;
        npc.getEquipmentInventory().getItem(slot).shrink(1);
        npc.level().setBlock(podPos, cocoa, Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    private boolean hasCocoaBeans() { return findItemSlot(Items.COCOA_BEANS) >= 0; }

    /** 收成熟可可豆：打掉 workPos 上的可可豆方块，掉落进背包（复用的 harvestBlock） */
    private void harvestCocoa() {
        if (!npc.level().getBlockState(workPos).is(Blocks.COCOA)) return;
        harvestBlock(workPos);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 收成熟简单作物并在原位补种同种（A 收小麦 → A 种小麦）；瓜茎旁不补种，留空位结果实 */
    private void harvestAndReplant() {
        BlockPos cropPos = workPos;
        BlockState state = npc.level().getBlockState(cropPos);
        Block cropBlock = state.getBlock();
        if (!(cropBlock instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        Item seed = seedForCrop(cropBlock);
        if (seed == null) return;

        harvestBlock(cropPos); // 收掉落进背包

        // 原位补种：下方仍是耕地、上方已空、不在瓜茎旁、手里有对应种子
        if (!npc.level().getBlockState(cropPos.below()).is(Blocks.FARMLAND)) return;
        if (!npc.level().getBlockState(cropPos).isAir()) return;
        if (isNearMelonStem(cropPos)) return; // 瓜茎旁留空位结果实
        int slot = findItemSlot(seed);
        if (slot < 0) return; // 没种子（极少数：背包满或刚巧没掉种子），下次扫描再补
        npc.getEquipmentInventory().getItem(slot).shrink(1);
        npc.level().setBlock(cropPos, crop.defaultBlockState(), Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 简单作物方块 → 对应种子 */
    private Item seedForCrop(Block crop) {
        if (crop == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (crop == Blocks.CARROTS) return Items.CARROT;
        if (crop == Blocks.POTATOES) return Items.POTATO;
        if (crop == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        return null;
    }

    /** 找农田四周的简单作物种类（用于踩坏耕地补种对应作物），没有就返回 null */
    private Block cropForNeighbor(BlockPos farmlandPos) {
        BlockPos cropPos = farmlandPos.above();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState s = npc.level().getBlockState(cropPos.relative(dir));
            if (s.is(Blocks.WHEAT) || s.is(Blocks.CARROTS) || s.is(Blocks.POTATOES) || s.is(Blocks.BEETROOTS)) {
                return s.getBlock();
            }
        }
        return null;
    }

    /** 收甘蔗：从 base（底节）上方砍第二节和第三节，留底节继续生长 */
    private void harvestCane(BlockPos base) {
        BlockPos cursor = base.above();
        for (int i = 0; i < 2; i++) {
            if (!npc.level().getBlockState(cursor).is(Blocks.SUGAR_CANE)) break;
            harvestBlock(cursor);
            cursor = cursor.above();
        }
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** 破坏单个甘蔗块并收集掉落（进背包，包满丢地上） */
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

    /** 找简单作物种子槽（从上次位置之后轮换） */
    private int findSeedSlot() {
        int size = npc.getEquipmentInventory().getContainerSize();
        for (int offset = 0; offset < size; offset++) {
            int i = (lastSeedSlot + 1 + offset) % size;
            if (cropForSeed(npc.getEquipmentInventory().getItem(i)) != null) return i;
        }
        return -1;
    }

    /** 找瓜类种子槽（固定从头找，一种用完再换另一种，不来回交替） */
    private int findMelonSeedSlot() {
        int size = npc.getEquipmentInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            if (melonStemForSeed(npc.getEquipmentInventory().getItem(i)) != null) return i;
        }
        return -1;
    }

    /** 简单作物种子 → 作物方块 */
    private Block cropForSeed(ItemStack seed) {
        if (seed.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT;
        if (seed.is(Items.CARROT)) return Blocks.CARROTS;
        if (seed.is(Items.POTATO)) return Blocks.POTATOES;
        if (seed.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS;
        return null;
    }

    /** 瓜类种子 → 茎方块 */
    private Block melonStemForSeed(ItemStack seed) {
        if (seed.is(Items.MELON_SEEDS)) return Blocks.MELON_STEM;
        if (seed.is(Items.PUMPKIN_SEEDS)) return Blocks.PUMPKIN_STEM;
        return null;
    }

    /** 茎四周至少有一个能结果实的空位（空位 + 下方是泥土类方块或耕地，与原版 StemBlock 判定一致） */
    private boolean hasMelonSpot(BlockPos stemPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos spot = stemPos.relative(dir);
            if (npc.level().getBlockState(spot).isAir()
                    && (npc.level().getBlockState(spot.below()).is(BlockTags.DIRT)
                    || npc.level().getBlockState(spot.below()).is(Blocks.FARMLAND))) {
                return true;
            }
        }
        return false;
    }

    /** 判断某位置四周是否有瓜类茎（瓜茎需要四周空位结果实，不能在旁边种作物） */
    private boolean isNearMelonStem(BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState s = npc.level().getBlockState(pos.relative(dir));
            if (s.is(Blocks.MELON_STEM) || s.is(Blocks.PUMPKIN_STEM)) return true;
        }
        return false;
    }

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

    private int findItemSlot(Item item) {
        var inv = npc.getEquipmentInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }

    private boolean hasSugarcane() { return findItemSlot(Items.SUGAR_CANE) >= 0; }
    private boolean hasWaterBucket() { return findItemSlot(Items.WATER_BUCKET) >= 0; }
    private boolean hasEmptyBucket() { return findItemSlot(Items.BUCKET) >= 0; }

    private boolean hasTwoWaterBuckets() {
        var inv = npc.getEquipmentInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WATER_BUCKET)) n++;
        }
        return n >= 2;
    }

    private boolean isDiggableGround(BlockPos pos) {
        BlockState s = npc.level().getBlockState(pos);
        return (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND))
                && npc.level().getBlockState(pos.above()).isAir()
                && npc.level().getBlockState(pos.below()).isSolid();
    }

    private boolean digGround(BlockPos pos) {
        if (!npc.level().isLoaded(pos)) return false;
        BlockState state = npc.level().getBlockState(pos);
        if (state.isAir()) return true; // 已挖过
        if (!state.is(BlockTags.DIRT) && !state.is(BlockTags.SAND)) return false;
        List<ItemStack> drops = Block.getDrops(state, (ServerLevel) npc.level(), pos,
                npc.level().getBlockEntity(pos), npc, npc.getMainHandItem());
        for (ItemStack drop : drops) {
            if (!npc.addToBag(drop)) break;
        }
        npc.level().destroyBlock(pos, false);
        return true;
    }

    /** 放一桶水：把目标变成静止水源（已是静止水源则不动；流动水会被覆盖成水源） */
    private boolean placeWater(BlockPos pos) {
        var fluid = npc.level().getFluidState(pos);
        if (fluid.is(FluidTags.WATER) && fluid.isSource()) return true; // 已是静止水源
        int slot = findItemSlot(Items.WATER_BUCKET);
        if (slot < 0) return false;
        npc.level().setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        npc.getEquipmentInventory().setItem(slot, new ItemStack(Items.BUCKET));
        return true;
    }

    /** 打水：把装备栏里所有空桶都灌满（NPC 已走到无限水源边，直接转换） */
    private boolean refillBucket(BlockPos pos) {
        if (!isInfiniteWater(pos)) return false;
        var inv = npc.getEquipmentInventory();
        boolean any = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.BUCKET)) {
                inv.setItem(i, new ItemStack(Items.WATER_BUCKET));
                any = true;
            }
        }
        return any;
    }

    /** 【测试版：只挖坑】井附近判定整段注释 */
    /*
    private boolean isNearWell(BlockPos p) {
        if (wellPos == null) return false;
        return Math.abs(p.getX() - wellPos.getX()) <= 2
                && Math.abs(p.getZ() - wellPos.getZ()) <= 2
                && Math.abs(p.getY() - wellPos.getY()) <= 1;
    }
    */

    /** 【测试版：只挖坑】随机游荡整段注释 */
    /*
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
    */

    private void debugLogState() {
        String zone = npc.hasWorkZone()
                ? npc.getWorkZoneMin().toShortString() + " → " + npc.getWorkZoneMax().toShortString()
                : "无";
        Sagadyssey.LOGGER.info("[务农调试] {} | 模式={} 命令={} | 阶段={} | 甘蔗={} 水桶={} 两桶={} 空桶={} | 井={} 沟数={} 挖={} 灌={} | 范围={}",
                npc.getNpcName(), npc.getFarmMode(), npc.getCommand(), phaseName(),
                hasSugarcane(), hasWaterBucket(), hasTwoWaterBuckets(), hasEmptyBucket(),
                wellPos != null ? wellPos.toShortString() : "无",
                trenches.size(), digProgress, floodProgress, zone);
    }

    private void debugLogResult(Work work) {
        Sagadyssey.LOGGER.info("[务农调试] {} | findWork={}",
                npc.getNpcName(),
                work != null ? (kindName(work.kind()) + " @ " + work.pos().toShortString()) : "null(无活→游荡)");
    }

    private String phaseName() {
        return switch (phase) {
            case PHASE_BUILD_WELL -> "建井";
            case PHASE_DIG_TRENCHES -> "挖沟";
            case PHASE_FLOOD_TRENCHES -> "灌水";
            case PHASE_PLANT -> "种植";
            default -> "未知";
        };
    }

    private static String kindName(int kind) {
        return switch (kind) {
            case KIND_TILL -> "翻地";
            case KIND_PLANT -> "播种";
            case KIND_HARVEST -> "收获";
            case KIND_DIG -> "挖沟";
            case KIND_FLOOD -> "灌水";
            case KIND_REFILL -> "舀水";
            case KIND_PLANT_CANE -> "种甘蔗";
            case KIND_HARVEST_CANE -> "收甘蔗";
            case KIND_PLANT_LOG -> "种原木";
            case KIND_PLANT_COCOA -> "种可可豆";
            case KIND_HARVEST_COCOA -> "收可可豆";
            case KIND_HARVEST_PLANT -> "收+补种";
            case KIND_BONEMEAL -> "催熟";
            case KIND_CRAFT_BONEMEAL -> "合成骨粉";
            case KIND_CREATE_IRRIGATION -> "建灌溉点";
            case KIND_CREATE_FARM_WELL -> "建无限水井";
            default -> "未知(" + kind + ")";
        };
    }

}
