package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * NPC 远程攻击 AI。
 * 参考原版 RangedBowAttackGoal，适配 NpcBase 的弓槽/箭槽和职业攻击距离。
 */
public class NpcRangedAttackGoal extends Goal {

    private final NpcBase npc;
    private final double speedModifier;
    private final float attackRadius;
    private final float maxAttackDistance;
    private int attackIntervalMin;
    private int attackTime;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    private static final float DEFAULT_ATTACK_RADIUS = 15.0F;
    private static final float CROSSBOW_ATTACK_RADIUS = 12.0F;

    public NpcRangedAttackGoal(NpcBase npc, double speedModifier, int attackInterval, float maxAttackDistance) {
        this.npc = npc;
        this.speedModifier = speedModifier;
        this.attackIntervalMin = attackInterval;
        this.maxAttackDistance = maxAttackDistance;

        // 根据职业调整攻击距离
        float radius = DEFAULT_ATTACK_RADIUS;
        if (npc.getProfession() != null) {
            radius = switch (npc.getProfession()) {
                case ARCHER -> 16.0F;
                default -> DEFAULT_ATTACK_RADIUS;
            };
        }
        this.attackRadius = radius;

        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 申请 ATTACK mutex，与 NpcCombatGoal/近战系统互斥
        if (!npc.requestMutex(AiMutex.ATTACK)) return false;

        // 检查是否有远程武器
        ItemStack weapon = getRangedWeapon();
        if (weapon.isEmpty() || !hasAmmo()) {
            npc.releaseMutex(AiMutex.ATTACK);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        if (npc.isOwnedBy(target.getUUID())) return false;
        // 弹药耗尽/武器丢失 → 切回近战，让近战 goal 接管战斗
        // （主手物品未变，NpcWeaponSwapHandler 不会弹回远程模式）
        if (getRangedWeapon().isEmpty() || !hasAmmo()) {
            npc.ensureMeleeMode();
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        npc.setAggressive(true);
    }

    @Override
    public void stop() {
        npc.setAggressive(false);
        seeTime = 0;
        npc.releaseMutex(AiMutex.ATTACK);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = npc.getTarget();
        if (target == null) return;

        double distSqr = npc.distanceToSqr(target);
        boolean canSee = npc.getSensing().hasLineOfSight(target);
        if (canSee) {
            seeTime++;
        } else {
            seeTime = 0;
        }

        double attackRadiusSqr = attackRadius * attackRadius;

        if (distSqr <= attackRadiusSqr && seeTime >= 5) {
            npc.getNavigation().stop();
        } else {
            npc.getNavigation().moveTo(target, speedModifier);
        }

        npc.getLookControl().setLookAt(target, 45.0F, 45.0F);

        // 远程武器蓄力/射击
        ItemStack weapon = getRangedWeapon();
        if (weapon.getItem() instanceof BowItem) {
            tickBow(weapon, target, distSqr, canSee);
        }
        // 弩的射击逻辑后续扩展
    }

    private void tickBow(ItemStack bow, LivingEntity target, double distSqr, boolean canSee) {
        // 射后冷却：attackIntervalMin tick 内不重新蓄力
        if (attackTime > 0) {
            attackTime--;
            return;
        }
        boolean usingBow = npc.isUsingItem();
        if (usingBow) {
            int useTicks = npc.getTicksUsingItem();
            if (useTicks >= 20) {
                // 射箭
                performBowAttack(target, bow, useTicks);
            }
        } else if (canSee && distSqr <= maxAttackDistance * maxAttackDistance) {
            // 开始蓄力
            npc.startUsingItem(ProjectileUtil.getWeaponHoldingHand(npc, item -> item instanceof BowItem));
        }
    }

    private void performBowAttack(LivingEntity target, ItemStack bow, int useTicks) {
        float power = BowItem.getPowerForTime(useTicks);
        if (power < 0.1F) return;

        // releaseUsing 只对玩家生效（源码中整个方法体在 instanceof Player 分支内），
        // 因此参照原版 AbstractSkeleton.performRangedAttack 手动生成箭实体
        ItemStack ammo = getAmmoStack();
        AbstractArrow arrow = ProjectileUtil.getMobArrow(npc, ammo, power, bow);

        double d0 = target.getX() - npc.getX();
        double d1 = target.getY(0.3333333333333333) - arrow.getY();
        double d2 = target.getZ() - npc.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        arrow.shoot(d0, d1 + d3 * 0.2F, d2, 1.6F, (float) (14 - npc.level().getDifficulty().getId() * 4));

        npc.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (npc.getRandom().nextFloat() * 0.4F + 0.8F));
        npc.level().addFreshEntity(arrow);

        npc.swing(npc.getUsedItemHand());
        npc.stopUsingItem();

        // 消耗箭
        consumeArrow();
        this.attackTime = this.attackIntervalMin; // 进入射后冷却
    }

    /** 获取当前远程武器（弓或弩） */
    private ItemStack getRangedWeapon() {
        ItemStack mainHand = npc.getMainHandItem();
        if (mainHand.getItem() instanceof BowItem || mainHand.getItem() instanceof CrossbowItem) {
            return mainHand;
        }
        ItemStack bowSlot = npc.getBowSlot();
        if (bowSlot.getItem() instanceof BowItem || bowSlot.getItem() instanceof CrossbowItem) {
            return bowSlot;
        }
        return ItemStack.EMPTY;
    }

    /** 获取可用弹药：优先箭槽，其次扫描背包（9 格装备栏） */
    private ItemStack getAmmoStack() {
        ItemStack arrowSlot = npc.getArrowSlot();
        if (!arrowSlot.isEmpty()) {
            return arrowSlot;
        }
        for (int i = 0; i < npc.getEquipmentInventory().getContainerSize(); i++) {
            ItemStack stack = npc.getEquipmentInventory().getItem(i);
            if (stack.getItem() instanceof ArrowItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 检查是否有弹药 */
    private boolean hasAmmo() {
        return !getAmmoStack().isEmpty();
    }

    /** 消耗一根箭 */
    private void consumeArrow() {
        ItemStack ammo = getAmmoStack();
        if (!ammo.isEmpty()) {
            ammo.shrink(1);
        }
    }

    /** 动态更新攻击间隔 */
    public void setAttackInterval(int interval) {
        this.attackIntervalMin = interval;
    }
}
