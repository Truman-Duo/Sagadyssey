package com.jgeted.sagadyssey.npc.entity;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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

    /** 阵营（新系统：基于 FactionRegistry 的 Faction 引用） */
    private Faction faction = FactionRegistry.get("sagadyssey:wilderness");

    /** NPC 等级类型（普通/精英/Boss），影响击杀声望惩罚 */
    private IFactionInteractable.NpcTier npcTier = IFactionInteractable.NpcTier.NORMAL;

    /** 主人 UUID，null 表示未被招募 */
    @Nullable
    private UUID ownerUUID = null;

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

    // === Getter / Setter ===

    public String getNpcName() { return customName; }
    public void setNpcName(String name) { this.customName = name; }

    public NpcProfession getProfession() { return profession; }

    public void setProfession(NpcProfession profession) {
        boolean wasNone = (this.profession == NpcProfession.NONE);
        this.profession = profession;
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
            // Lv4 固定交易不加价
            if (nextLevel < 4) {
                List<NpcTradeOffer> markedUp = new ArrayList<>();
                for (NpcTradeOffer t : pool) {
                    markedUp.add(NpcTradeRegistry.applyMerchantMarkup(t, this.merchantMarkupRate));
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

    /** 设置主人（招募时调用） */
    public void setOwner(UUID playerUUID) {
        this.ownerUUID = playerUUID;
    }

    /** 检查实体是否为盟友（主人 或 同主人的 NPC） */
    private boolean isAlliedTo(LivingEntity entity) {
        if (entity instanceof Player player) {
            return isOwnedBy(player.getUUID());
        }
        if (entity instanceof NpcBase other) {
            if (this.ownerUUID != null && this.ownerUUID.equals(other.ownerUUID)) {
                return true;
            }
        }
        return false;
    }

    public SimpleContainer getEquipmentInventory() { return equipmentInventory; }

    public ItemStack getBowSlot() { return bowSlot; }
    public void setBowSlot(ItemStack stack) { this.bowSlot = stack; }

    public ItemStack getArrowSlot() { return arrowSlot; }
    public void setArrowSlot(ItemStack stack) { this.arrowSlot = stack; }

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
                equipmentInventory.setItem(1, new ItemStack(Items.BREAD, 2));
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
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new com.jgeted.sagadyssey.npc.ai.FollowOwnerGoal(this, 1.0D, 3.0F, 64.0F));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new com.jgeted.sagadyssey.npc.ai.ProtectOwnerGoal(this));
        this.targetSelector.addGoal(1, new com.jgeted.sagadyssey.npc.ai.NpcHostileGoal(this));
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
        // 被非盟友攻击时立即反击
        if (source.getEntity() instanceof LivingEntity attacker && attacker.isAlive() && !isAlliedTo(attacker)) {
            this.setTarget(attacker);
        }
        return super.hurt(source, amount);
    }

    // === 右键交互 ===

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
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
        tag.putString("Faction", this.faction != null ? this.faction.id() : "sagadyssey:wilderness");
        tag.putString("NpcTier", this.npcTier.name());
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }

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
        if (tag.contains("NpcCommand")) {
            try {
                this.command = NpcCommand.valueOf(tag.getString("NpcCommand"));
            } catch (IllegalArgumentException e) {
                this.command = NpcCommand.IDLE;
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
    }
}
