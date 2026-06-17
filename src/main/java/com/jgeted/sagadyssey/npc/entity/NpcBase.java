package com.jgeted.sagadyssey.npc.entity;

import com.jgeted.sagadyssey.npc.network.NpcInteractionPacket;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

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
public class NpcBase extends PathfinderMob {

    // === 自定义属性（NBT 持久化） ===
    private String customName = "NPC";
    private NpcProfession profession = NpcProfession.NONE;

    /** NPC 等级，1-100 */
    private int npcLevel = 1;

    /** 当前经验值 */
    private int experience = 0;

    /** 击杀计数（累积，不重置） */
    private int kills = 0;

    /** 士气 0-100，v0.1 只显示不生效 */
    private int moral = 50;

    /** 招募费用（绿宝石数量） */
    private int recruitmentCost = 3;

    /** 当前行为指令 */
    private NpcCommand command = NpcCommand.IDLE;

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
    }

    public int getNpcLevel() { return npcLevel; }
    public void setNpcLevel(int level) { this.npcLevel = Math.max(1, Math.min(100, level)); }

    public int getExperience() { return experience; }
    public void setExperience(int exp) { this.experience = Math.max(0, exp); }

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

    public SimpleContainer getEquipmentInventory() { return equipmentInventory; }

    public ItemStack getBowSlot() { return bowSlot; }
    public void setBowSlot(ItemStack stack) { this.bowSlot = stack; }

    public ItemStack getArrowSlot() { return arrowSlot; }
    public void setArrowSlot(ItemStack stack) { this.arrowSlot = stack; }

    /** 首次分配职业时给予初始装备 */
    private void applyInitialEquipment(NpcProfession profession) {
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
                equipmentInventory.setItem(0, new ItemStack(Items.EMERALD, 8));
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
        this.goalSelector.addGoal(1, new com.jgeted.sagadyssey.npc.ai.StayGoal(this));
        this.goalSelector.addGoal(2, new com.jgeted.sagadyssey.npc.ai.FollowOwnerGoal(this, 1.0D, 3.0F, 12.0F));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
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
        tag.putInt("NpcLevel", this.npcLevel);
        tag.putInt("Experience", this.experience);
        tag.putInt("Kills", this.kills);
        tag.putInt("Moral", this.moral);
        tag.putInt("RecruitmentCost", this.recruitmentCost);
        tag.putString("NpcCommand", this.command.name());
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
        if (tag.contains("NpcLevel")) {
            this.npcLevel = tag.getInt("NpcLevel");
        }
        if (tag.contains("Experience")) {
            this.experience = tag.getInt("Experience");
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
    }
}
