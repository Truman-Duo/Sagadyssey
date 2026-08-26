package com.jgeted.sagadyssey.npc.entity;

import com.jgeted.sagadyssey.npc.ai.AiMutex;
import com.jgeted.sagadyssey.npc.faction.Faction;
import com.jgeted.sagadyssey.npc.faction.FactionAttachments;
import com.jgeted.sagadyssey.npc.faction.FactionRegistry;
import com.jgeted.sagadyssey.npc.faction.IFactionInteractable;
import com.jgeted.sagadyssey.npc.faction.NpcFaction;
import com.jgeted.sagadyssey.npc.faction.StandingLevel;
import com.jgeted.sagadyssey.npc.network.NpcInteractionPacket;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import com.jgeted.sagadyssey.npc.trade.NpcTradeOffer;
import com.jgeted.sagadyssey.npc.trade.NpcTradeRegistry;
import com.jgeted.sagadyssey.core.config.SagadysseyConfig;
import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Sagadyssey NPC 基类。
 * 包含属性系统、主人归属、NBT 持久化。
 *
 * v0.1 属性：
 *   HP / MaxHp → 原版 Attribute 系统，自动同步客户端
 *   Attack → 原版 Attribute 系统
 *   Speed → 原版 Attribute 系统
 *   Armor → 原版 Attribute 系统
 *   Lvl / Exp / Kills / Moral / Cost / OwnerUUID → 自定义 NBT
 */
public class NpcBase extends PathfinderMob implements IFactionInteractable {

    private static final EntityDataAccessor<Integer> DATA_PROFESSION =
            SynchedEntityData.defineId(NpcBase.class, EntityDataSerializers.INT);

    // === 自定义属性（NBT 持久化） ===
    private String customName = "NPC";
    private NpcProfession profession = NpcProfession.NONE;

    /** NPC 等级，1-100 */
    // 不再存储——由 experience 计算得出

    /** 当前经验值 */
    private int experience = 0;

    /** 商人交易随机池种子（出生时随机，持久化） */
    private int merchantTradeSeed = 0;

    /** 该 NPC 当前显示的交易列表（已固定数量，NBT 持久化） */
    private final List<NpcTradeOffer> activeTrades = new ArrayList<>();

    /** 该 NPC 已解锁的最高交易等级 */
    private int unlockedTradeLevel = 0;

    /** 诗人跟随计时器（tick），每 3600 tick（3 分钟）加经验 */
    private int bardFollowTicks = 0;

    /** 商人加价率 0.20-0.50（仅 TRADER 使用） */
    private double merchantMarkupRate = 0.30;

    /** 击杀计数（累积，不重置） */
    private int kills = 0;

    /** 士气 0-100，v0.1 只显示不生效 */
    private int moral = 50;

    /** 招募费用（绿宝石数量） */
    private int recruitmentCost = 3;

    /** 当前行为指令 */
    private NpcCommand command = NpcCommand.IDLE;

    /** 框选中的工作范围第一角（未完成） */
    private BlockPos workZoneCorner1 = null;
    /** 工作范围最小角 */
    private BlockPos workZoneMin = null;
    /** 工作范围最大角 */
    private BlockPos workZoneMax = null;

    /** 阵营（新系统：基于 FactionRegistry 的 Faction 引用） */
    private Faction faction = FactionRegistry.get("sagadyssey:wilderness");

    /** NPC 等级类型（普通/精英/Boss），影响击杀声望惩罚 */
    private IFactionInteractable.NpcTier npcTier = IFactionInteractable.NpcTier.NORMAL;

    /** 主人 UUID，null 表示未被招募 */
    @Nullable
    private UUID ownerUUID = null;

    /** 招募前的原阵营 ID（解散时恢复），null 表示未招募或已解散 */
    @Nullable
    private String originalFaction = null;

    /** 绑定的坐骑 UUID */
    @Nullable
    private UUID mountUUID = null;

    /** 绑定的坐骑是否带有鞍（缓存，用于快速判断） */
    private boolean mountSaddled = false;

    /** 骑马攻击冷却（tick），0 表示可以攻击 */
    private int mountedAttackCooldown = 0;

    /** 牵马步行模式：true 时禁止自动上马 */
    private boolean leadMountMode = false;

    /** 待上马标记：true 时 MountGoal 会导航 NPC 走向坐骑并骑上去 */
    private boolean pendingMount = false;

    /** AI mutex 位标记集合，防止多个 AI goal 同时操控 NPC */
    private final Set<AiMutex> activeMutexes = EnumSet.noneOf(AiMutex.class);

    /** 敌对目标候选（由 faction 事件/hurt 写入，由 TargetSelector 消费） */
    @Nullable
    private LivingEntity hostileTargetCandidate = null;

    /** 战斗目标选择 goal 引用（主人变更时刷新目标源） */
    @Nullable
    private com.jgeted.sagadyssey.npc.ai.NpcCombatGoal combatGoal = null;

    /** 近战攻击 goal 引用（用于武器切换时动态替换） */
    @Nullable
    private MeleeAttackGoal meleeAttackGoal = null;

    /** 远程攻击 goal（弓手模式时创建，近战模式时为 null） */
    @Nullable
    private com.jgeted.sagadyssey.npc.ai.NpcRangedAttackGoal rangedGoal = null;

    /** 上次检测的手持武器（用于变化检测） */
    private ItemStack lastCheckedWeapon = ItemStack.EMPTY;

    /** 当前是否处于远程模式 */
    private boolean isRangedMode = false;

    /** 9 格自定义背包 */
    private final SimpleContainer equipmentInventory = new SimpleContainer(9);

    /** 弓槽 */
    private ItemStack bowSlot = ItemStack.EMPTY;

    /** 箭槽 */
    private ItemStack arrowSlot = ItemStack.EMPTY;

    // === 构造函数 ===

    public NpcBase(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFESSION, NpcProfession.NONE.ordinal());
    }

    // === Getter / Setter ===

    public String getNpcName() { return customName; }
    public void setNpcName(String name) { this.customName = name; }

    public NpcProfession getProfession() {
        if (this.level().isClientSide) {
            int ordinal = this.entityData.get(DATA_PROFESSION);
            NpcProfession[] values = NpcProfession.values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NpcProfession.NONE;
        }
        return profession;
    }

    public void setProfession(NpcProfession profession) {
        boolean wasNone = (this.profession == NpcProfession.NONE);
        this.profession = profession;
        this.entityData.set(DATA_PROFESSION, profession.ordinal());
        if (profession != NpcProfession.NONE) {
            this.customName = profession.getDisplayName();
        }
        this.recruitmentCost = profession.getRecruitmentCost();

        if (wasNone && profession != NpcProfession.NONE) {
            applyInitialEquipment(profession);
        }

        // 应用职业基础属性
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(profession.getMaxHp());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(profession.getAttackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(profession.getSpeed());
        this.getAttribute(Attributes.ARMOR).setBaseValue(profession.getArmor());

        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }

        this.experience = 0;  // 转职重置经验

        // 重置交易列表，重新抽 Lv1
        this.activeTrades.clear();
        this.unlockedTradeLevel = 0;
        if (!this.level().isClientSide && profession != NpcProfession.NONE) {
            this.merchantMarkupRate = 0.20 + this.random.nextDouble() * 0.30;
            unlockNextTradeLevel();
        }
    }

    /** 根据经验计算当前等级（1-4） */
    public int getNpcLevel() {
        if (experience < 200) return 1;
        if (experience < 600) return 2;
        if (experience < 1400) return 3;
        return 4;
    }

    /** 获取等级称号 */
    public String getLevelTitle() {
        return switch (getNpcLevel()) {
            case 1 -> "学徒";
            case 2 -> "熟练";
            case 3 -> "专家";
            case 4 -> "大师";
            default -> "学徒";
        };
    }

    public int getExperience() { return experience; }
    public void setExperience(int exp) { this.experience = Math.max(0, exp); }

    public int getMerchantTradeSeed() { return merchantTradeSeed; }
    public void setMerchantTradeSeed(int seed) { this.merchantTradeSeed = seed; }

    public List<NpcTradeOffer> getActiveTrades() { return Collections.unmodifiableList(activeTrades); }
    public int getUnlockedTradeLevel() { return unlockedTradeLevel; }
    public double getMerchantMarkupRate() { return merchantMarkupRate; }

    /** 初始化/升级时调用：从下一级池抽交易并固定数量 */
    public void unlockNextTradeLevel() {
        if (this.level().isClientSide) return;
        int nextLevel = unlockedTradeLevel + 1;
        if (nextLevel > 4 || getNpcLevel() < nextLevel) return;

        long seed = this.getUUID().getLeastSignificantBits();
        List<NpcTradeOffer> picked;

        if (this.profession == NpcProfession.TRADER) {
            int count = NpcTradeRegistry.PICK_COUNTS.get(NpcProfession.TRADER)[nextLevel - 1];
            List<NpcTradeOffer> pool = NpcTradeRegistry.getMerchantPool(nextLevel);
            Random rand = new Random(seed + nextLevel * 31L);
            // player 阵营 NPC 与主人交易时不加价（ignoreReputation=true）
            boolean isPlayerFaction = this.faction != null
                    && "sagadyssey:player".equals(this.faction.id());
            // Lv4 固定交易不加价
            if (nextLevel < 4) {
                List<NpcTradeOffer> markedUp = new ArrayList<>();
                for (NpcTradeOffer t : pool) {
                    markedUp.add(NpcTradeRegistry.applyMerchantMarkup(t, this.merchantMarkupRate, isPlayerFaction));
                }
                Collections.shuffle(markedUp, rand);
                picked = markedUp.subList(0, Math.min(count, markedUp.size()))
                        .stream().map(t -> t.resolve(rand)).toList();
            } else {
                List<NpcTradeOffer> copy = new ArrayList<>(pool);
                Collections.shuffle(copy, rand);
                picked = copy.subList(0, Math.min(count, copy.size()))
                        .stream().map(t -> t.resolve(rand)).toList();
            }
        } else {
            int[] counts = NpcTradeRegistry.PICK_COUNTS.getOrDefault(profession, new int[]{0, 0, 0, 0});
            int count = counts[nextLevel - 1];
            picked = NpcTradeRegistry.pickRandom(profession, nextLevel, seed, count, this.level().registryAccess());
        }

        activeTrades.addAll(picked);
        unlockedTradeLevel = nextLevel;
    }

    /** 添加经验。满级（4）时不执行。返回是否升级了 */
    public boolean addExperience(int amount) {
        if (amount <= 0) return false;
        int oldLevel = getNpcLevel();
        if (oldLevel >= 4) return false;
        this.experience += amount;
        int newLevel = getNpcLevel();
        if (newLevel > oldLevel) {
            applyLevelUpAttributes(newLevel);
            unlockNextTradeLevel();  // 升级解锁新交易
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        // === 武器变更检测（主手物品变化 → 切换近战/远程 AI） ===
        ItemStack currentHeld = getMainHandItem();
        if (!ItemStack.matches(currentHeld, lastCheckedWeapon)) {
            com.jgeted.sagadyssey.npc.ai.NpcWeaponSwapHandler.onWeaponChanged(this, currentHeld);
            lastCheckedWeapon = currentHeld.copy();
        }

        // === 弓箭手补箭自动切回远程（弹药放背包时不经过 setArrowSlot，需轮询兜底）===
        if (!this.isRangedMode && this.profession == NpcProfession.ARCHER
                && this.tickCount % 10 == 0) {
            if (hasRangedWeaponAvailable() && hasAmmoAvailable()) {
                ensureRangedMode();
            }
        }

        // === 自动上下马检测 ===
        if (this.tickCount % SagadysseyConfig.MOUNT_CHECK_INTERVAL.get() == 0) {
            if (this.isPassenger()) {
                if (shouldDismount()) {
                    // 只有拴马成功才下马，否则留在马上防止坐骑走丢
                    if (tetherHorse()) {
                        this.dismountToBind();
                        this.leadMountMode = true;
                    }
                }
            } else if (this.hasMount() && !this.leadMountMode) {
                Entity mount = getMount();
                if (mount != null && mount.isAlive() && this.distanceTo(mount) <= 4.0F) {
                    // 马被拴在栅栏上时不自动上马
                    boolean isTethered = mount instanceof Leashable l && l.isLeashed();
                    if (!isTethered && !shouldDismount()) {
                        if (mount instanceof AbstractHorse h && h.isSaddled()) {
                            untetherHorse();
                            this.leadMountMode = false;
                            this.startRiding(mount);
                        }
                    }
                }
            }
        }

        // === 喂马逻辑（服务端，每 100 tick ≈ 5 秒） ===
        if (this.tickCount % 100 == 0 && this.hasMount()) {
            Entity mount = getMount();
            if (mount instanceof LivingEntity livingMount && livingMount.isAlive()) {
                float hpRatio = livingMount.getHealth() / livingMount.getMaxHealth();
                if (hpRatio < 0.7f) {
                    int foodSlot = findHorseFood();
                    if (foodSlot >= 0) {
                        ItemStack food = equipmentInventory.getItem(foodSlot);
                        livingMount.heal(getHorseFoodHealAmount(food));
                        food.shrink(1);
                    }
                }
            }
        }

        // === 牵马步行逻辑（服务端） ===
        // NPC 的牵马是 AI 行为驱动，不依赖原版拴绳系统，因此不检查 isLeashed()
        if (this.hasMount() && !this.isPassenger()) {
            Entity mount = getMount();
            if (mount instanceof AbstractHorse horse && horse.isAlive()) {
                boolean npcMoving = this.getNavigation().isInProgress() || this.zza > 0;
                if (npcMoving) {
                    double dist = this.distanceTo(horse);
                    if (dist > 4.0 && this.tickCount % 20 == 0) {
                        horse.getNavigation().moveTo(this, 1.2);
                    } else if (dist <= 2.5 && horse.getNavigation().isInProgress()) {
                        horse.getNavigation().stop();
                    }
                } else {
                    if (horse.getNavigation().isInProgress()) {
                        horse.getNavigation().stop();
                    }
                }
            }
        }

        // === 骑马驱马：直接设置马的速度向量（绕过 travel() 的 zza 机制） ===
        // 原因：AbstractHorse.getControllingPassenger() 只认 Player，NPC 乘客不产生前进输入。
        // MoveControl.setWantedPosition() 只设 speed 不设 zza，travel() 得不到前进力。
        // 改为直接 setDeltaMovement 驱动，可靠且速度可控。
        if (this.isPassenger()) {
            Entity vehicle = this.getVehicle();
            if (vehicle instanceof AbstractHorse horse) {
                Vec3 moveTarget = null;

                // === 骑马战斗（先判断，后移动） ===
                boolean inAttackWindow = false;
                if (this.getTarget() != null && this.getTarget().isAlive()) {
                    // 近战能力：战士/重装，或弓箭手（弹药耗尽切近战兜底时也需骑马挥砍）
                    boolean meleeCapable = this.profession == NpcProfession.WARRIOR
                            || this.profession == NpcProfession.HEAVY
                            || this.profession == NpcProfession.ARCHER;

                    // 远程模式下射击由 NpcRangedAttackGoal 处理，这里不挥砍；近战兜底(!isRangedMode)才贴脸挥剑
                    if (meleeCapable && !this.isRangedMode) {
                        // 冷却递减
                        if (this.mountedAttackCooldown > 0) {
                            this.mountedAttackCooldown--;
                        }

                        double distToTarget = this.distanceTo(this.getTarget());
                        if (distToTarget <= 2.8 && this.mountedAttackCooldown <= 0) {
                            // 停止马 → 挥剑裸伤 → 冷却 12 tick（~0.6 秒）
                            horse.setDeltaMovement(0, horse.getDeltaMovement().y, 0);
                            DamageSource source = this.damageSources().mobAttack(this);
                            float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                            this.getTarget().hurt(source, dmg);
                            this.swing(InteractionHand.MAIN_HAND);
                            this.mountedAttackCooldown = 12;
                            inAttackWindow = true;
                        }
                    } else if (this.isRangedMode) {
                        // 骑马射箭：射击交给 NpcRangedAttackGoal，走位见下方移动段
                    } else {
                        // 非战斗职业：下马战斗
                        this.dismountToBind();
                    }
                } else {
                    this.mountedAttackCooldown = 0;
                }

                // === 移动目标计算（冷却期间不移动，让马站稳攻击） ===
                if (!inAttackWindow) {
                    // 目标优先级：攻击目标 > 跟随主人 > 最近伤害源 > 导航目标
                    if (this.getTarget() != null) {
                        moveTarget = this.getTarget().position();
                    }
                    if (moveTarget == null && this.command == NpcCommand.FOLLOW && this.ownerUUID != null) {
                        Player owner = this.level().getPlayerByUUID(this.ownerUUID);
                        if (owner != null) moveTarget = owner.position();
                    }
                    if (moveTarget == null && this.getLastHurtMob() != null) {
                        moveTarget = this.getLastHurtMob().position();
                    }
                    if (moveTarget == null && this.getNavigation().isInProgress()) {
                        moveTarget = Vec3.atBottomCenterOf(this.getNavigation().getTargetPos());
                    }
                }

                if (moveTarget != null) {
                    double distSq = this.distanceToSqr(moveTarget);
                    // 移动方向：1=逼近, 0=保持距离停下, -1=后撤拉开
                    int moveDir;
                    if (this.getTarget() != null) {
                        if (this.isRangedMode) {
                            // 骑马弓箭手风筝：保持 8~14 格输出距离（可调）
                            if (distSq > 14.0 * 14.0) {
                                moveDir = 1;   // 太远，逼近
                            } else if (distSq < 8.0 * 8.0) {
                                moveDir = -1;  // 太近，后撤拉开
                            } else {
                                moveDir = 0;   // 射程带内，停下射击
                            }
                        } else {
                            // 近战贴身上去砍（1.5 格）——弓箭手弹药耗尽切近战兜底时也走这里
                            moveDir = distSq > 2.25 ? 1 : 0;
                        }
                    } else if (this.command == NpcCommand.FOLLOW) {
                        moveDir = distSq > 12.0 ? 1 : 0;
                    } else {
                        moveDir = distSq > 6.0 ? 1 : 0;
                    }
                    if (moveDir != 0) {
                        // 面向目标（后撤时也保持面向，边退边射）
                        Vec3 toTarget = moveTarget.subtract(this.position());
                        double horizDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
                        float yaw = (float) (Math.atan2(toTarget.z, toTarget.x) * 180.0 / Math.PI) - 90.0F;
                        horse.setYRot(yaw);
                        horse.yBodyRot = yaw;
                        horse.yHeadRot = yaw;

                        if (horizDist < 0.1) {
                            // 目标在正上/下方，水平不动
                            horse.setDeltaMovement(0, horse.getDeltaMovement().y, 0);
                        } else {
                            // 直接设置水平速度（马的属性速度 × 1.5 加成），moveDir=-1 时反向
                            float horseSpeed = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
                            float moveSpeed = horseSpeed * 1.5F;
                            double dx = (toTarget.x / horizDist) * moveSpeed * moveDir;
                            double dz = (toTarget.z / horizDist) * moveSpeed * moveDir;

                            // Y 轴：游泳上浮 / 跳跃障碍 / 正常重力
                            double dy = horse.getDeltaMovement().y;
                            if (horse.isInWater()) {
                                dy = 0.15;
                                horse.setJumping(true);
                            } else if (horse.onGround() && horse.horizontalCollision && this.tickCount % 5 == 0) {
                                dy = 0.42;
                                horse.setJumping(true);
                            }

                            horse.setDeltaMovement(dx, dy, dz);
                        }
                    } else {
                        // 距离足够，停止（保留 Y 轴让重力处理）
                        horse.setDeltaMovement(0, horse.getDeltaMovement().y, 0);
                    }
                } else {
                    // 无目标，停止
                    horse.setDeltaMovement(0, horse.getDeltaMovement().y, 0);
                }

            }
        }

        // 诗人跟随计时器
        if (this.profession == NpcProfession.BARD
                && this.command == NpcCommand.FOLLOW
                && getNpcLevel() < 4) {
            bardFollowTicks++;
            if (bardFollowTicks >= 3600) {
                bardFollowTicks = 0;
                addExperience(5);
            }
        }
    }

    /** 升级属性增长 */
    private void applyLevelUpAttributes(int newLevel) {
        NpcProfession prof = this.profession;
        double hpMult = 1.0 + (newLevel - 1) * 0.15;
        double atkMult = 1.0 + (newLevel - 1) * 0.10;
        double spdMult = 1.0 + (newLevel - 1) * 0.05;
        int armorBonus = (newLevel - 1);

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(prof.getMaxHp() * hpMult);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(prof.getAttackDamage() * atkMult);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(prof.getSpeed() * spdMult);
        this.getAttribute(Attributes.ARMOR).setBaseValue(prof.getArmor() + armorBonus);
        this.setHealth(this.getMaxHealth());
    }

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = Math.max(0, kills); }
    /** 击杀数 +1 */
    public void addKill() { this.kills++; }

    public int getMoral() { return moral; }
    public void setMoral(int moral) { this.moral = Math.max(0, Math.min(100, moral)); }

    public int getRecruitmentCost() { return recruitmentCost; }
    public void setRecruitmentCost(int cost) { this.recruitmentCost = Math.max(1, cost); }

    public NpcCommand getCommand() { return command; }
    public void setCommand(NpcCommand command) { this.command = command; }

    /** 标记工作范围第一角（玩家脚下） */
    public void markWorkZoneCorner1(BlockPos pos) {
        this.workZoneCorner1 = pos.immutable();
    }

    /** 标记第二角，生成矩形范围 */
    public void markWorkZoneCorner2(BlockPos pos) {
        if (this.workZoneCorner1 == null) return;
        BlockPos a = this.workZoneCorner1;
        this.workZoneMin = new BlockPos(
                Math.min(a.getX(), pos.getX()), Math.min(a.getY(), pos.getY()), Math.min(a.getZ(), pos.getZ()));
        this.workZoneMax = new BlockPos(
                Math.max(a.getX(), pos.getX()), Math.max(a.getY(), pos.getY()), Math.max(a.getZ(), pos.getZ()));
        this.workZoneCorner1 = null;
    }

    public boolean hasWorkZone() {
        return this.workZoneMin != null && this.workZoneMax != null;
    }

    public void clearWorkZone() {
        this.workZoneCorner1 = null;
        this.workZoneMin = null;
        this.workZoneMax = null;
    }

    /** 直接设置工作范围（两个角自动归一化为 min/max） */
    public void setWorkZone(BlockPos a, BlockPos b) {
        this.workZoneMin = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.workZoneMax = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        this.workZoneCorner1 = null;
    }

    /** 是否允许在该位置作业（未设范围则全允许） */
    public boolean isWorkAllowedAt(BlockPos pos) {
        return !hasWorkZone() || isInWorkZone(pos);
    }

    public BlockPos getWorkZoneMin() { return workZoneMin; }
    public BlockPos getWorkZoneMax() { return workZoneMax; }

    private boolean isInWorkZone(BlockPos pos) {
        return pos.getX() >= workZoneMin.getX() && pos.getX() <= workZoneMax.getX()
                && pos.getY() >= workZoneMin.getY() && pos.getY() <= workZoneMax.getY()
                && pos.getZ() >= workZoneMin.getZ() && pos.getZ() <= workZoneMax.getZ();
    }

    public Faction getFaction() { return faction; }
    public void setFaction(Faction faction) { this.faction = faction; }

    @Override
    public IFactionInteractable.NpcTier getNpcTier() { return npcTier; }
    public void setNpcTier(IFactionInteractable.NpcTier tier) { this.npcTier = tier; }

    /** 获取旧版 NpcFaction（向后兼容，基于阵营的敌对性判定） */
    public NpcFaction getLegacyFaction() {
        if (faction == null) return NpcFaction.NEUTRAL;
        return faction.canBeHostile() ? NpcFaction.HOSTILE : NpcFaction.NEUTRAL;
    }

    @Nullable
    public UUID getOwnerUUID() { return ownerUUID; }

    public boolean isOwned() { return ownerUUID != null; }

    public boolean isOwnedBy(UUID playerUUID) {
        return ownerUUID != null && ownerUUID.equals(playerUUID);
    }

    /** 招募前的原阵营 ID（解散时恢复），null 表示未招募或已解散 */
    @Nullable
    public String getOriginalFaction() { return originalFaction; }

    public void setOriginalFaction(String id) { this.originalFaction = id; }

    /**
     * 设置主人（招募时调用）。
     * 首次设置 owner 时自动保存原阵营并切换到 player 阵营。
     * 传入 null 时不处理阵营——阵营清除统一由 {@link #dismiss()} 负责。
     */
    public void setOwner(UUID playerUUID) {
        this.ownerUUID = playerUUID;
        if (playerUUID != null) {
            // 保存原阵营（仅首次——防止覆盖已有的 originalFaction）
            if (this.originalFaction == null && this.faction != null
                    && !"sagadyssey:player".equals(this.faction.id())) {
                this.originalFaction = this.faction.id();
            }
            // 切换到 player 阵营
            this.faction = FactionRegistry.getPlayerFaction();
            // 刷新战斗目标源（启用主人保护级）
            if (combatGoal != null) combatGoal.refreshSources();
        }
    }

    /**
     * 解散 NPC：恢复原阵营、清除主人。
     * 原阵营被数据包删除时 fallback 到 wilderness。
     */
    public void dismiss() {
        if (this.originalFaction != null) {
            Faction original = FactionRegistry.get(this.originalFaction);
            this.faction = original != null ? original : FactionRegistry.get("sagadyssey:wilderness");
            this.originalFaction = null;
        }
        this.ownerUUID = null;
    }

    // === 坐骑绑定 ===

    /** 绑定坐骑 */
    public void bindMount(Entity mount) {
        if (mount == null || !mount.isAlive()) return;
        this.mountUUID = mount.getUUID();
        this.mountSaddled = mount instanceof AbstractHorse h && h.isSaddled();

        // 绑定坐骑时确保有栓绳
        if (!hasLeadInInventory()) {
            for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
                if (equipmentInventory.getItem(i).isEmpty()) {
                    equipmentInventory.setItem(i, new ItemStack(Items.LEAD));
                    break;
                }
            }
        }

        // 如果 NPC 已经在马旁边且马有鞍，立刻骑上去
        if (!this.level().isClientSide && this.distanceTo(mount) <= 3.0F) {
            if (mount instanceof AbstractHorse h && h.isSaddled()) {
                this.startRiding(mount);
            }
        }
    }

    /** 解绑坐骑 */
    public void unbindMount() {
        if (this.isPassenger()) this.dismountToBind();
        this.mountUUID = null;
        this.mountSaddled = false;
    }

    /** 获取绑定的坐骑实体 */
    @Nullable
    public Entity getMount() {
        if (mountUUID == null) return null;
        if (this.level() == null) return null;
        for (Entity e : this.level().getEntities(this, this.getBoundingBox().inflate(64))) {
            if (e.getUUID().equals(mountUUID) && e.isAlive()) return e;
        }
        return null;
    }

    /** 是否有绑定坐骑 */
    public boolean hasMount() { return mountUUID != null; }

    /** 骑上绑定的坐骑 */
    public void mountToBind() {
        if (this.isPassenger()) return;
        Entity mount = getMount();
        if (mount != null && mount.isAlive() && this.distanceTo(mount) <= 4.0F) {
            this.startRiding(mount);
        }
    }

    /** 从坐骑下来 */
    public void dismountToBind() {
        if (this.isPassenger()) {
            this.stopRiding();
        }
    }

    /** 设置牵马步行模式（禁止自动上马） */
    public void setLeadMountMode(boolean mode) { this.leadMountMode = mode; }
    public boolean isLeadMountMode() { return leadMountMode; }

    /** 待上马标记（供 MountGoal 和网络包使用） */
    public void setPendingMount() { this.pendingMount = true; }
    public void clearPendingMount() { this.pendingMount = false; }
    public boolean isPendingMount() { return pendingMount; }

    // === AI Mutex 管理 ===

    /**
     * 申请 mutex 位。所有位都空闲时申请成功并占用，否则返回 false。
     */
    public boolean requestMutex(AiMutex... mutexes) {
        for (AiMutex m : mutexes) {
            if (activeMutexes.contains(m)) {
                Sagadyssey.LOGGER.debug("[MUTEX] {} requestMutex {} DENIED (held: {})",
                        getNpcName(), Arrays.toString(mutexes), activeMutexes);
                return false;
            }
        }
        Collections.addAll(activeMutexes, mutexes);
        Sagadyssey.LOGGER.debug("[MUTEX] {} requestMutex {} GRANTED (held: {})",
                getNpcName(), Arrays.toString(mutexes), activeMutexes);
        return true;
    }

    /** 释放 mutex 位 */
    public void releaseMutex(AiMutex... mutexes) {
        for (AiMutex m : mutexes) activeMutexes.remove(m);
        Sagadyssey.LOGGER.debug("[MUTEX] {} releaseMutex {} (held: {})",
                getNpcName(), Arrays.toString(mutexes), activeMutexes);
    }

    // === 武器模式切换 ===

    /** 切换到近战模式：移除远程 goal，注册近战 goal，并把主手的弓换成背包里的剑/斧 */
    public void ensureMeleeMode() {
        if (this.level().isClientSide) return;
        if (!isRangedMode) return; // 已经是近战模式

        if (rangedGoal != null) {
            this.goalSelector.removeGoal(rangedGoal);
            rangedGoal = null;
        }
        if (meleeAttackGoal != null) {
            this.goalSelector.addGoal(1, meleeAttackGoal);
        }
        isRangedMode = false;

        // 自动换近战武器：主手仍是弓/弩时，若背包有剑/斧则交换
        // （弹药耗尽切近战兜底时不再拿弓贴脸）
        equipBestMeleeWeapon();
    }

    /** 切换到远程模式：移除近战 goal，注册远程 goal，并把主手武器换回弓/弩 */
    public void ensureRangedMode() {
        if (this.level().isClientSide) return;
        if (isRangedMode) return; // 已经是远程模式

        if (meleeAttackGoal != null) {
            this.goalSelector.removeGoal(meleeAttackGoal);
        }
        if (rangedGoal == null) {
            rangedGoal = new com.jgeted.sagadyssey.npc.ai.NpcRangedAttackGoal(this, 1.0D, 20, 16.0F);
        }
        this.goalSelector.addGoal(1, rangedGoal);
        isRangedMode = true;

        // 自动换远程武器：主手是剑/斧或空手时，从背包/弓槽找回弓
        // （补箭切回远程时重新拿弓）
        equipBestRangedWeapon();
    }

    // === 武器自动装备辅助 ===

    /** 是否是远程武器（弓/弩） */
    private static boolean isRangedWeaponItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem);
    }

    /** 是否是近战武器（剑/斧） */
    private static boolean isMeleeWeaponItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem);
    }

    /** 在背包(9格)中查找近战武器，返回槽位下标，无则 -1 */
    private int findMeleeWeaponInBag() {
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            if (isMeleeWeaponItem(equipmentInventory.getItem(i))) return i;
        }
        return -1;
    }

    /** 在背包(9格)中查找远程武器，返回槽位下标，无则 -1 */
    private int findRangedWeaponInBag() {
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            if (isRangedWeaponItem(equipmentInventory.getItem(i))) return i;
        }
        return -1;
    }

    /** 是否有远程武器可用（主/副手、弓槽或背包） */
    private boolean hasRangedWeaponAvailable() {
        return isRangedWeaponItem(getMainHandItem())
                || isRangedWeaponItem(getOffhandItem())
                || isRangedWeaponItem(bowSlot)
                || findRangedWeaponInBag() >= 0;
    }

    /** 是否有箭可用（箭槽或背包） */
    private boolean hasAmmoAvailable() {
        if (!arrowSlot.isEmpty()) return true;
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            if (equipmentInventory.getItem(i).getItem() instanceof ArrowItem) return true;
        }
        return false;
    }

    /** 把物品放进背包：优先堆叠到同类物品，其次空位；放不下返回 false */
    public boolean addToBag(ItemStack stack) {
        if (stack.isEmpty()) return true;
        // 先尝试堆叠到已有同类物品
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            ItemStack slot = equipmentInventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) return true;
                }
            }
        }
        // 再找空位
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            if (equipmentInventory.getItem(i).isEmpty()) {
                equipmentInventory.setItem(i, stack.copy());
                return true;
            }
        }
        return false;
    }

    /** 主手与背包某格交换物品（弓↔剑互换） */
    private void swapMainHandWithBag(int bagSlot) {
        ItemStack held = getMainHandItem();
        ItemStack bagItem = equipmentInventory.getItem(bagSlot).copy();
        equipmentInventory.setItem(bagSlot, held.copy());
        setItemSlot(EquipmentSlot.MAINHAND, bagItem);
    }

    /** 进入近战模式时：主手是弓就把剑/斧换上来（弓放回原剑格） */
    private void equipBestMeleeWeapon() {
        if (isRangedWeaponItem(getMainHandItem())) {
            int slot = findMeleeWeaponInBag();
            if (slot >= 0) {
                swapMainHandWithBag(slot);
            }
        }
    }

    /** 进入远程模式时：主手不是弓就从背包/弓槽找回弓（近战武器放回背包） */
    private void equipBestRangedWeapon() {
        ItemStack held = getMainHandItem();
        if (isRangedWeaponItem(held)) return; // 已经拿着弓

        int bagSlot = findRangedWeaponInBag();
        if (bagSlot >= 0) {
            // 背包里有弓 → 主手与背包格交换
            swapMainHandWithBag(bagSlot);
            return;
        }
        // 背包无弓，退而求其次：弓槽
        if (isRangedWeaponItem(bowSlot)) {
            ItemStack bow = bowSlot.copy();
            bowSlot = ItemStack.EMPTY;
            if (!held.isEmpty() && !addToBag(held)) {
                this.spawnAtLocation(held.copy()); // 背包满则掉落，避免复制
            }
            setItemSlot(EquipmentSlot.MAINHAND, bow);
        }
    }

    // === 敌对目标候选（替代 hurt() 中直接 setTarget） ===

    @Nullable
    public LivingEntity getHostileTargetCandidate() { return hostileTargetCandidate; }

    public void setHostileTargetCandidate(@Nullable LivingEntity target) {
        this.hostileTargetCandidate = target;
    }

    /** 判断坐骑当前是否被拴绳拴住 */
    public boolean isMountLeashed() {
        if (!hasMount()) return false;
        Entity mount = getMount();
        return mount instanceof Leashable l && l.isLeashed();
    }

    /**
     * 判断当前是否应下马。
     * true = 下马，false = 留在马上。
     */
    public boolean shouldDismount() {
        // 狭窄空间检测：头顶有方块
        if (!level().getBlockState(this.blockPosition().above(2)).isAir()) {
            return true;
        }
        if (!level().getBlockState(this.blockPosition().above()).isAir()
                && level().getBlockState(this.blockPosition().above()).isSolid()) {
            return true;
        }

        // 非战斗职业在工作场景下马
        boolean isWorkingProf = profession == NpcProfession.WORKER
                || profession == NpcProfession.FARMER
                || profession == NpcProfession.BLACKSMITH;
        if (isWorkingProf && this.getTarget() == null) {
            if (this.command == NpcCommand.IDLE || this.command == NpcCommand.STAY || this.command == NpcCommand.WORK) {
                return true;
            }
        }

        return false;
    }

    // === 栓绳与拴马 ===

    /** 检查背包是否有栓绳 */
    private boolean hasLeadInInventory() {
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            if (equipmentInventory.getItem(i).is(Items.LEAD)) return true;
        }
        return false;
    }

    /** 消耗一根栓绳 */
    private void consumeLeadFromInventory() {
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            ItemStack stack = equipmentInventory.getItem(i);
            if (stack.is(Items.LEAD)) {
                stack.shrink(1);
                break;
            }
        }
    }

    /** 回收一根栓绳到 NPC 背包 */
    public void addLeadToInventory() {
        ItemStack lead = new ItemStack(Items.LEAD);
        // 先尝试合并到已有的栓绳堆叠
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            ItemStack stack = equipmentInventory.getItem(i);
            if (stack.is(Items.LEAD) && stack.getCount() < stack.getMaxStackSize()) {
                stack.grow(1);
                return;
            }
        }
        // 没有可合并的堆叠，放到空槽位
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            if (equipmentInventory.getItem(i).isEmpty()) {
                equipmentInventory.setItem(i, lead);
                return;
            }
        }
        // 背包满了，掉落在地上（兜底）
        this.spawnAtLocation(lead);
    }

    /** 找最近的栅栏 */
    @Nullable
    private BlockPos findNearestFence(int range) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    pos.set(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
                    if (this.level().getBlockState(pos).is(BlockTags.FENCES)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 拴马到最近的栅栏。
     * @return true 拴马成功，false 失败（无栅栏/无栓绳/马已拴）
     */
    public boolean tetherHorse() {
        if (!this.hasMount() || this.level().isClientSide) return false;
        Entity mount = getMount();
        if (mount == null || !mount.isAlive()) return false;

        // 如果马已被拴，不重复操作
        if (mount instanceof Leashable l && l.isLeashed()) return false;

        BlockPos fencePos = findNearestFence(8);
        if (fencePos == null) return false;
        if (!hasLeadInInventory()) return false;

        consumeLeadFromInventory();
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level(), fencePos);
        if (mount instanceof Leashable leashable) {
            leashable.setLeashedTo(knot, true);
        }
        return true;
    }

    /** 解拴马：只断开栓绳连接，不处理物品回收 */
    public void untetherHorse() {
        if (!this.hasMount() || this.level().isClientSide) return;
        Entity mount = getMount();
        if (mount == null || !mount.isAlive()) return;

        if (mount instanceof Leashable leashable && leashable.isLeashed()) {
            leashable.dropLeash(true, false);
        }
    }

    // === 喂马 ===

    /** 在背包中查找马匹食物，返回槽位索引，-1 表示没有 */
    private int findHorseFood() {
        for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
            if (isHorseFood(equipmentInventory.getItem(i))) return i;
        }
        return -1;
    }

    /** 判断是否为马匹食物 */
    private static boolean isHorseFood(ItemStack stack) {
        return stack.is(Items.WHEAT)
                || stack.is(Items.SUGAR)
                || stack.is(Items.APPLE)
                || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.GOLDEN_CARROT)
                || stack.is(Items.HAY_BLOCK);
    }

    /** 根据食物类型返回治疗量 */
    private static float getHorseFoodHealAmount(ItemStack food) {
        if (food.is(Items.GOLDEN_APPLE)) return 10.0f;
        if (food.is(Items.GOLDEN_CARROT)) return 4.0f;
        if (food.is(Items.HAY_BLOCK)) return 20.0f;
        if (food.is(Items.APPLE)) return 3.0f;
        if (food.is(Items.SUGAR)) return 1.0f;
        if (food.is(Items.WHEAT)) return 2.0f;
        return 1.0f;
    }

    /**
     * 检查实体是否为盟友。
     * player 阵营 NPC 之间：同 ownerUUID → 盟友，不需要查声望。
     */
    private boolean isAlliedTo(LivingEntity entity) {
        if (entity instanceof Player player) {
            return isOwnedBy(player.getUUID());
        }
        if (entity instanceof NpcBase other) {
            // 同一主人 → 盟友
            if (this.ownerUUID != null && this.ownerUUID.equals(other.ownerUUID)) {
                return true;
            }
            // 双方都是 player 阵营且同 owner → 盟友（防御性冗余）
            if (this.ownerUUID != null
                    && "sagadyssey:player".equals(this.faction != null ? this.faction.id() : null)
                    && "sagadyssey:player".equals(other.faction != null ? other.faction.id() : null)
                    && this.ownerUUID.equals(other.ownerUUID)) {
                return true;
            }
        }
        return false;
    }

    public SimpleContainer getEquipmentInventory() { return equipmentInventory; }

    public ItemStack getBowSlot() { return bowSlot; }
    public void setBowSlot(ItemStack stack) { this.bowSlot = stack; }

    public ItemStack getArrowSlot() { return arrowSlot; }

    public void setArrowSlot(ItemStack stack) {
        this.arrowSlot = stack;
        // 箭矢补充后，若主/副手、弓槽或背包里有弓，自动切回远程模式
        // （修复弹药耗尽切近战后，补箭不再射箭的问题；弓可能在背包里）
        if (!stack.isEmpty() && !this.level().isClientSide) {
            boolean hasRangedWeapon = isRangedWeaponItem(getMainHandItem())
                    || isRangedWeaponItem(getOffhandItem())
                    || isRangedWeaponItem(bowSlot)
                    || findRangedWeaponInBag() >= 0;
            if (hasRangedWeapon) {
                ensureRangedMode();
            }
        }
    }

    /** 首次分配职业时给予初始装备 */
    public void applyInitialEquipment(NpcProfession profession) {
        switch (profession) {
            case NONE -> {}
            case WARRIOR -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                equipmentInventory.setItem(0, new ItemStack(Items.BREAD, 3));
            }
            case ARCHER -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                setArrowSlot(new ItemStack(Items.ARROW, 8));
                equipmentInventory.setItem(0, new ItemStack(Items.BREAD, 2));
            }
            case HEAVY -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                equipmentInventory.setItem(0, new ItemStack(Items.BREAD, 3));
            }
            case WORKER -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE_AXE));
            }
            case BLACKSMITH -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE_SWORD));
                equipmentInventory.setItem(0, new ItemStack(Items.COAL, 4));
                equipmentInventory.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
            }
            case FARMER -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_HOE));
                equipmentInventory.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
                equipmentInventory.setItem(1, new ItemStack(Items.CARROT, 4));
                equipmentInventory.setItem(2, new ItemStack(Items.POTATO, 4));
                equipmentInventory.setItem(3, new ItemStack(Items.BEETROOT_SEEDS, 4));
                equipmentInventory.setItem(4, new ItemStack(Items.BREAD, 2));
            }
            case BARD -> {
                equipmentInventory.setItem(0, new ItemStack(Items.BREAD, 4));
            }
            case MEDIC -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
                equipmentInventory.setItem(0, new ItemStack(Items.GOLDEN_APPLE, 1));
                equipmentInventory.setItem(1, new ItemStack(Items.BREAD, 4));
            }
            case TRADER -> {
                equipmentInventory.setItem(0, new ItemStack(Items.PAPER, 3));
                equipmentInventory.setItem(1, new ItemStack(Items.BOOK, 1));
            }
        }
    }

    // === 便捷属性读取（从原版 Attribute 系统） ===

    /** 当前生命值 */
    public float getCurrentHp() { return this.getHealth(); }

    /** 最大生命值 */
    public float getMaxHp() { return this.getMaxHealth(); }

    /** 攻击力 */
    public float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** 移动速度 */
    public float getSpeed() {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /** 护甲值 */
    @Override
    public int getArmorValue() {
        return (int) this.getAttributeValue(Attributes.ARMOR);
    }

    // === AI Goals ===

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new com.jgeted.sagadyssey.npc.ai.LowHpRetreatGoal(this));
        this.goalSelector.addGoal(1, new com.jgeted.sagadyssey.npc.ai.StayGoal(this));
        this.goalSelector.addGoal(1, meleeAttackGoal = new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(1, new com.jgeted.sagadyssey.npc.ai.MountGoal(this));
        this.goalSelector.addGoal(2, new com.jgeted.sagadyssey.npc.ai.FollowOwnerGoal(this, 1.0D, 3.0F, 64.0F));
        this.goalSelector.addGoal(2, new com.jgeted.sagadyssey.npc.ai.FarmerWorkGoal(this));
        this.goalSelector.addGoal(2, new com.jgeted.sagadyssey.npc.ai.WorkerWorkGoal(this));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(6, new com.jgeted.sagadyssey.npc.ai.NpcOpenDoorGoal(this));
        this.targetSelector.addGoal(0, combatGoal = new com.jgeted.sagadyssey.npc.ai.NpcCombatGoal(this));
    }

    /** 属性定义（注册实体时调用） */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player) {
            // 新阵营系统：非主人玩家攻击 NPC → NPC 变敌对（通过 canBeHostile）
            if (faction != null && !faction.canBeHostile() && !isOwnedBy(player.getUUID())) {
                // 对该玩家来说此 NPC 变为敌对行为（行为层，不改变阵营本身）
                // 旧系统兼容：仅对 old-faction-based 逻辑保留
            }
        }
        // 被非盟友攻击时记录候选目标（由 TargetSelector 优先级链消费）
        if (source.getEntity() instanceof LivingEntity attacker && attacker.isAlive() && !isAlliedTo(attacker)) {
            this.hostileTargetCandidate = attacker;
        }
        return super.hurt(source, amount);
    }

    // === 右键交互 ===

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        // === 指挥杖：设置 / 清除工作范围 ===
        if (stack.is(ModItems.NPC_COMMAND_WAND.get())) {
            if (player.level().isClientSide) {
                // 客户端不打开命令 GUI（不发 request_stats），交给服务端处理
                return InteractionResult.SUCCESS;
            }
            if (!isOwnedBy(player.getUUID())) {
                player.displayClientMessage(Component.literal("§c这个 NPC 不属于你"), true);
                return InteractionResult.SUCCESS;
            }
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (player.isShiftKeyDown()) {
                clearWorkZone();
                player.displayClientMessage(Component.literal("§a已清除 " + getNpcName() + " 的工作范围"), true);
            } else if (tag.contains("Corner1") && tag.contains("Corner2")) {
                int[] a = tag.getIntArray("Corner1");
                int[] b = tag.getIntArray("Corner2");
                setWorkZone(new BlockPos(a[0], a[1], a[2]), new BlockPos(b[0], b[1], b[2]));
                tag.remove("Corner1");
                tag.remove("Corner2");
                CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
                player.displayClientMessage(Component.literal("§a已设置 " + getNpcName() + " 的工作范围："
                        + getWorkZoneMin().toShortString() + " → " + getWorkZoneMax().toShortString()), true);
            } else {
                player.displayClientMessage(Component.literal("§e先用指挥杖右键地面标记两个角，再右键 NPC"), true);
            }
            return InteractionResult.SUCCESS;
        }

        // === 栓绳交互 ===
        if (stack.is(Items.LEAD)) {
            // 检测玩家是否在牵马
            Leashable leashedHorse = null;
            for (var horse : this.level().getEntitiesOfClass(AbstractHorse.class,
                    player.getBoundingBox().inflate(12))) {
                if (horse instanceof Leashable leashable
                        && leashable.isLeashed()
                        && leashable.getLeashHolder() == player) {
                    leashedHorse = leashable;
                    break;
                }
            }

            if (leashedHorse != null) {
                AbstractHorse horse = (AbstractHorse) leashedHorse;
                if (horse.isTamed() && horse.getOwnerUUID() != null
                        && horse.getOwnerUUID().equals(player.getUUID())) {
                    if (!player.level().isClientSide) {
                        this.bindMount(horse);
                        player.displayClientMessage(
                                Component.literal("§a" + this.getNpcName() + " 已绑定坐骑！"), true);
                    }
                } else {
                    if (!player.level().isClientSide) {
                        player.displayClientMessage(
                                Component.literal("§c这匹马不属于你"), true);
                    }
                }
                return InteractionResult.SUCCESS;
            } else {
                // 玩家只拿着栓绳 → 给 NPC 背包加栓绳
                if (!player.level().isClientSide) {
                    boolean added = false;
                    for (int i = 0; i < equipmentInventory.getContainerSize(); i++) {
                        if (equipmentInventory.getItem(i).isEmpty()) {
                            equipmentInventory.setItem(i, new ItemStack(Items.LEAD));
                            added = true;
                            break;
                        }
                    }
                    if (added) {
                        player.displayClientMessage(
                                Component.literal("§a" + this.getNpcName() + " 拿到了一根栓绳"), true);
                        if (!player.getAbilities().instabuild) stack.shrink(1);
                    } else {
                        player.displayClientMessage(
                                Component.literal("§c" + this.getNpcName() + " 背包已满"), true);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }
        // === 栓绳交互结束 ===

        if (player.level().isClientSide) {
            // 客户端：请求 NPC 数据，服务端收到后会回传 NpcStatsPayload
            PacketDistributor.sendToServer(new NpcInteractionPacket(this.getId(), "request_stats"));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // === NBT 持久化 ===

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcName", this.customName);
        tag.putString("Profession", this.profession.name());
        tag.putInt("Experience", this.experience);
        tag.putInt("MerchantSeed", this.merchantTradeSeed);
        tag.putInt("BardFollowTicks", this.bardFollowTicks);
        tag.putInt("Kills", this.kills);
        tag.putInt("Moral", this.moral);
        tag.putInt("RecruitmentCost", this.recruitmentCost);
        tag.putString("NpcCommand", this.command.name());
        if (this.workZoneMin != null && this.workZoneMax != null) {
            tag.putIntArray("WorkZoneMin", new int[]{workZoneMin.getX(), workZoneMin.getY(), workZoneMin.getZ()});
            tag.putIntArray("WorkZoneMax", new int[]{workZoneMax.getX(), workZoneMax.getY(), workZoneMax.getZ()});
        }
        tag.putString("Faction", this.faction != null ? this.faction.id() : "sagadyssey:wilderness");
        if (this.originalFaction != null) {
            tag.putString("OriginalFaction", this.originalFaction);
        }
        tag.putString("NpcTier", this.npcTier.name());
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }

        if (this.mountUUID != null) {
            tag.putUUID("MountUUID", this.mountUUID);
        }
        tag.putBoolean("MountSaddled", mountSaddled);
        tag.putBoolean("LeadMountMode", leadMountMode);

        if (!this.bowSlot.isEmpty()) {
            tag.put("BowSlot", this.bowSlot.save(level().registryAccess()));
        }
        if (!this.arrowSlot.isEmpty()) {
            tag.put("ArrowSlot", this.arrowSlot.save(level().registryAccess()));
        }
        ContainerHelper.saveAllItems(tag, equipmentInventory.getItems(), level().registryAccess());

        // 交易数据持久化
        tag.putInt("UnlockedTradeLevel", this.unlockedTradeLevel);
        tag.putDouble("MerchantMarkup", this.merchantMarkupRate);
        CompoundTag tradesTag = new CompoundTag();
        tradesTag.putInt("Count", activeTrades.size());
        for (int i = 0; i < activeTrades.size(); i++) {
            NpcTradeOffer t = activeTrades.get(i);
            CompoundTag entry = new CompoundTag();
            entry.put("CostItem", t.costItem().save(level().registryAccess()));
            entry.putInt("CostMin", t.costMin());
            entry.putInt("CostMax", t.costMax());
            entry.put("ResultItem", t.resultItem().save(level().registryAccess()));
            entry.putInt("ResultMin", t.resultMin());
            entry.putInt("ResultMax", t.resultMax());
            entry.putInt("MinLevel", t.minNpcLevel());
            tradesTag.put("t" + i, entry);
        }
        tag.put("ActiveTrades", tradesTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("NpcName")) {
            this.customName = tag.getString("NpcName");
        }
        if (tag.contains("Profession")) {
            try {
                this.profession = NpcProfession.valueOf(tag.getString("Profession"));
            } catch (IllegalArgumentException e) {
                this.profession = NpcProfession.NONE;
            }
        }
        if (tag.contains("NpcLevel") && tag.getInt("NpcLevel") > 1 && !tag.contains("Experience")) {
            // 向后兼容：旧存档有 NpcLevel 但没有 Experience，按最低经验转换
            int oldLevel = tag.getInt("NpcLevel");
            this.experience = switch (oldLevel) {
                case 2 -> 200;
                case 3 -> 600;
                case 4 -> 1400;
                default -> 0;
            };
        }
        if (tag.contains("Experience")) {
            this.experience = tag.getInt("Experience");
        }
        if (tag.contains("MerchantSeed")) {
            this.merchantTradeSeed = tag.getInt("MerchantSeed");
        }
        if (tag.contains("BardFollowTicks")) {
            this.bardFollowTicks = tag.getInt("BardFollowTicks");
        }
        if (tag.contains("Kills")) {
            this.kills = tag.getInt("Kills");
        }
        if (tag.contains("Moral")) {
            this.moral = tag.getInt("Moral");
        }
        if (tag.contains("RecruitmentCost")) {
            this.recruitmentCost = tag.getInt("RecruitmentCost");
        }
        if (tag.contains("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        } else {
            this.ownerUUID = null;
        }
        // 原阵营 ID（招募前保存的完整 ID，如 "sagadyssey:kingdom"）
        this.originalFaction = tag.contains("OriginalFaction")
                ? tag.getString("OriginalFaction") : null;
        if (tag.contains("MountUUID")) {
            this.mountUUID = tag.getUUID("MountUUID");
        } else {
            this.mountUUID = null;
        }
        this.mountSaddled = tag.getBoolean("MountSaddled");
        this.leadMountMode = tag.getBoolean("LeadMountMode");
        if (tag.contains("NpcCommand")) {
            try {
                this.command = NpcCommand.valueOf(tag.getString("NpcCommand"));
            } catch (IllegalArgumentException e) {
                this.command = NpcCommand.IDLE;
            }
        }
        if (tag.contains("WorkZoneMin") && tag.contains("WorkZoneMax")) {
            int[] min = tag.getIntArray("WorkZoneMin");
            int[] max = tag.getIntArray("WorkZoneMax");
            if (min.length == 3 && max.length == 3) {
                this.workZoneMin = new BlockPos(min[0], min[1], min[2]);
                this.workZoneMax = new BlockPos(max[0], max[1], max[2]);
            }
        }
        if (tag.contains("Faction")) {
            try {
                String factionId = tag.getString("Faction");
                // 兼容旧格式（"HOSTILE"/"NEUTRAL"/"FRIENDLY"）和新格式（"sagadyssey:kingdom"）
                if (factionId.contains(":")) {
                    this.faction = FactionRegistry.get(factionId);
                } else {
                    // 旧 NpcFaction 枚举值，映射到新 Faction
                    this.faction = switch (factionId) {
                        case "HOSTILE" -> FactionRegistry.get("sagadyssey:bandit");
                        case "FRIENDLY" -> FactionRegistry.get("sagadyssey:kingdom");
                        default -> FactionRegistry.get("sagadyssey:wilderness");
                    };
                }
            } catch (Exception e) {
                this.faction = FactionRegistry.get("sagadyssey:wilderness");
            }
        }
        if (tag.contains("NpcTier")) {
            try {
                this.npcTier = IFactionInteractable.NpcTier.valueOf(tag.getString("NpcTier"));
            } catch (IllegalArgumentException e) {
                this.npcTier = IFactionInteractable.NpcTier.NORMAL;
            }
        }

        if (tag.contains("BowSlot")) {
            this.bowSlot = ItemStack.parse(level().registryAccess(), tag.getCompound("BowSlot")).orElse(ItemStack.EMPTY);
        } else {
            this.bowSlot = ItemStack.EMPTY;
        }
        if (tag.contains("ArrowSlot")) {
            this.arrowSlot = ItemStack.parse(level().registryAccess(), tag.getCompound("ArrowSlot")).orElse(ItemStack.EMPTY);
        } else {
            this.arrowSlot = ItemStack.EMPTY;
        }
        if (tag.contains("Items")) {
            equipmentInventory.clearContent();
            ContainerHelper.loadAllItems(tag, equipmentInventory.getItems(), level().registryAccess());
        }

        // 交易数据恢复
        if (tag.contains("UnlockedTradeLevel")) {
            this.unlockedTradeLevel = tag.getInt("UnlockedTradeLevel");
        }
        if (tag.contains("MerchantMarkup")) {
            this.merchantMarkupRate = tag.getDouble("MerchantMarkup");
        }
        this.activeTrades.clear();
        if (tag.contains("ActiveTrades")) {
            CompoundTag tradesTag = tag.getCompound("ActiveTrades");
            int count = tradesTag.getInt("Count");
            for (int i = 0; i < count; i++) {
                CompoundTag entry = tradesTag.getCompound("t" + i);
                ItemStack costItem = ItemStack.parse(level().registryAccess(), entry.getCompound("CostItem")).orElse(ItemStack.EMPTY);
                ItemStack resultItem = ItemStack.parse(level().registryAccess(), entry.getCompound("ResultItem")).orElse(ItemStack.EMPTY);
                if (costItem.isEmpty() || resultItem.isEmpty()) continue;
                int costMin = entry.getInt("CostMin");
                int costMax = entry.getInt("CostMax");
                int resultMin = entry.getInt("ResultMin");
                int resultMax = entry.getInt("ResultMax");
                int minLevel = entry.getInt("MinLevel");
                this.activeTrades.add(new NpcTradeOffer(costItem, costMin, costMax, resultItem, resultMin, resultMax, minLevel));
            }
        }

        // 旧存档/数据包未加载兜底：faction 不应为 null（field 初始化时 registry 可能未就绪）
        if (this.faction == null) {
            this.faction = FactionRegistry.get("sagadyssey:wilderness");
            if (this.faction == null) {
                // registry 彻底未加载（极端时序），player 阵营有硬编码兜底，永不返回 null
                this.faction = FactionRegistry.getPlayerFaction();
            }
        }

        // 旧存档静默迁移：有主人但还属于原阵营 → 自动切到 player 阵营
        if (this.ownerUUID != null && !"sagadyssey:player".equals(this.faction.id())) {
            Sagadyssey.LOGGER.info("迁移已招募 NPC: {} 从 {} 转到 player 阵营 (owner={})",
                    this.getDisplayName().getString(),
                    this.faction != null ? this.faction.id() : "null",
                    this.ownerUUID);
            this.originalFaction = this.faction != null ? this.faction.id() : null;
            this.faction = FactionRegistry.getPlayerFaction();
        }
    }
}
