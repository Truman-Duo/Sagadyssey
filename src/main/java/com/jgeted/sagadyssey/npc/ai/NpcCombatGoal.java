package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * 统一战斗目标选择器。
 * 合并了 ProtectOwnerGoal 和 NpcHostileGoal：
 * - 有主人 → 启用保护主人目标源（OWNER_ATTACKED / OWNER_ATTACKING）
 * - 无主人 → 跳过主人相关源，从 SELF_DEFENSE 开始
 * <p>
 * 使用 TargetSelector 优先级链统一决策，占用 mutex ATTACK（保护主人时额外占用 MOVE）。
 */
public class NpcCombatGoal extends Goal {

    private final NpcBase npc;
    private final TargetSelector targetSelector;
    private int scanCooldown;

    private static final int SCAN_INTERVAL_PROTECT = 3;   // 保护主人模式扫描更快
    private static final int SCAN_INTERVAL_HOSTILE = 20;  // 普通敌对扫描间隔

    public NpcCombatGoal(NpcBase npc) {
        this.npc = npc;
        this.targetSelector = buildSelector();
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    /** 根据 NPC 是否有主人构建目标源链 */
    private TargetSelector buildSelector() {
        TargetSelector selector = new TargetSelector(npc);
        if (npc.getOwnerUUID() != null) {
            selector.register(TargetSelector.ownerAttacked());
            selector.register(TargetSelector.ownerAttacking());
        }
        selector.register(TargetSelector.selfDefense());
        selector.register(TargetSelector.revenge());
        selector.register(TargetSelector.nearbyHostile());
        return selector;
    }

    @Override
    public boolean canUse() {
        // STAY 模式下不主动寻敌（除非自卫/保护主人——即前 4 级源）
        if (npc.getCommand() == NpcCommand.STAY) {
            return evaluateStayMode();
        }

        if (--scanCooldown > 0) return false;
        scanCooldown = (npc.getOwnerUUID() != null && npc.getCommand() == NpcCommand.FOLLOW)
                ? SCAN_INTERVAL_PROTECT : SCAN_INTERVAL_HOSTILE;

        // 只申请 ATTACK——MOVE 在 start() 中通过停止移动 goal 来处理
        if (!npc.requestMutex(AiMutex.ATTACK)) return false;

        TargetResult result = targetSelector.selectTarget();
        if (result != null) {
            npc.setTarget(result.target());
            // 目标已设置，立即释放 ATTACK，让战斗执行 goal（远程/近战）持有该位
            npc.releaseMutex(AiMutex.ATTACK);
            return true;
        }

        // 没找到目标，释放 mutex
        npc.releaseMutex(AiMutex.ATTACK);
        return false;
    }

    /**
     * STAY 模式下只响应高优先级威胁（owner 相关 + 自卫 + 仇恨候选），不主动扫描。
     */
    private boolean evaluateStayMode() {
        if (npc.getTarget() != null && npc.getTarget().isAlive()
                && npc.distanceToSqr(npc.getTarget()) < 36.0D) {
            return false; // 近身有敌，StayGoal 已放行
        }

        // 轻量检查：只查前 4 级（跳过 NEARBY_HOSTILE 扫描）
        TargetResult r;
        r = checkOwnerAttackedQuick();
        if (r != null) { applyResult(r); return true; }
        r = checkOwnerAttackingQuick();
        if (r != null) { applyResult(r); return true; }
        r = TargetSelector.selfDefense().evaluate(npc);
        if (r != null) { applyResult(r); return true; }
        r = TargetSelector.revenge().evaluate(npc);
        if (r != null) { applyResult(r); return true; }

        return false;
    }

    private TargetResult checkOwnerAttackedQuick() {
        if (npc.getOwnerUUID() == null) return null;
        Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        if (owner == null || !owner.isAlive()) return null;
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker == null || !attacker.isAlive() || attacker == npc) return null;
        if (npc.isOwnedBy(attacker.getUUID())) return null;
        if (npc.distanceToSqr(attacker) > 256.0D) return null;
        return new TargetResult(attacker, TargetReason.OWNER_ATTACKED);
    }

    private TargetResult checkOwnerAttackingQuick() {
        if (npc.getOwnerUUID() == null) return null;
        Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        if (owner == null || !owner.isAlive()) return null;
        LivingEntity victim = owner.getLastHurtMob();
        if (victim == null || !victim.isAlive() || victim == npc) return null;
        if (npc.isOwnedBy(victim.getUUID())) return null;
        if (npc.distanceToSqr(victim) > 256.0D) return null;
        return new TargetResult(victim, TargetReason.OWNER_ATTACKING);
    }

    private void applyResult(TargetResult r) {
        if (npc.requestMutex(AiMutex.ATTACK)) {
            npc.setTarget(r.target());
            // 目标已设置，立即释放 ATTACK，供战斗执行 goal 持有
            npc.releaseMutex(AiMutex.ATTACK);
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        if (npc.isOwnedBy(target.getUUID())) return false;

        // FOLLOWER/保护模式下检查主人距离
        if (npc.getOwnerUUID() != null && npc.getCommand() == NpcCommand.FOLLOW) {
            Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
            if (owner != null && npc.distanceToSqr(owner) > 256.0D) return false;
        }

        return npc.distanceToSqr(target) <= 576.0D; // 24 格范围
    }

    @Override
    public void start() {
        // target 已在 canUse 中设置
    }

    @Override
    public void stop() {
        npc.setTarget(null);
        // ATTACK 已在 canUse/applyResult 中设置目标后立即释放，
        // 这里不释放，避免误放战斗执行 goal（NpcRangedAttackGoal 等）正持有的位
    }

    /**
     * 重新构建目标源链（主人变更时调用）。
     */
    public void refreshSources() {
        targetSelector.clear();
        TargetSelector selector = buildSelector();
        // 复制 buildSelector 中注册的源到当前 selector
        if (npc.getOwnerUUID() != null) {
            targetSelector.register(TargetSelector.ownerAttacked());
            targetSelector.register(TargetSelector.ownerAttacking());
        }
        targetSelector.register(TargetSelector.selfDefense());
        targetSelector.register(TargetSelector.revenge());
        targetSelector.register(TargetSelector.nearbyHostile());
    }
}
