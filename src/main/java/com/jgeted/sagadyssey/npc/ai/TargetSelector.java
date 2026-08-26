package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.faction.FactionAttachments;
import com.jgeted.sagadyssey.npc.faction.StandingModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 多源目标选择优先级链。
 * 按优先级从高到低依次评估，返回第一个有合法目标的结果。
 * <p>
 * 优先级：主人的受击目标 → 主人的攻击目标 → 自卫 → 仇恨候选 → 附近敌对扫描
 */
public class TargetSelector {

    private final NpcBase npc;
    private final List<TargetSource> sources = new ArrayList<>();

    /** 附近敌对扫描范围 */
    private static final double NEARBY_SCAN_RANGE = 16.0D;
    private static final double NEARBY_SCAN_RANGE_MOUNTED = 24.0D;

    public TargetSelector(NpcBase npc) {
        this.npc = npc;
    }

    /** 按优先级从高到低注册目标源 */
    public void register(TargetSource source) {
        sources.add(source);
    }

    /** 清除所有已注册的目标源 */
    public void clear() {
        sources.clear();
    }

    /**
     * 返回第一个有合法目标的结果。
     * @return 最高优先级的目标结果，或 null（无可攻击目标）
     */
    @Nullable
    public TargetResult selectTarget() {
        for (TargetSource source : sources) {
            TargetResult result = source.evaluate(npc);
            if (result != null && result.target() != null && result.target().isAlive()) {
                return result;
            }
        }
        return null;
    }

    // === 内置目标源工厂方法 ===

    /** 优先级 1：主人被谁打了（lastHurtByMob） */
    public static TargetSource ownerAttacked() {
        return npc -> {
            if (npc.getOwnerUUID() == null) return null;
            Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
            if (owner == null || !owner.isAlive()) return null;
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker == null || !attacker.isAlive()) return null;
            if (attacker == npc) return null;
            if (npc.isOwnedBy(attacker.getUUID())) return null;
            double range = npc.isPassenger() ? NEARBY_SCAN_RANGE_MOUNTED : NEARBY_SCAN_RANGE;
            if (npc.distanceToSqr(attacker) > range * range) return null;
            return new TargetResult(attacker, TargetReason.OWNER_ATTACKED);
        };
    }

    /** 优先级 2：主人在打谁（lastHurtMob） */
    public static TargetSource ownerAttacking() {
        return npc -> {
            if (npc.getOwnerUUID() == null) return null;
            Player owner = npc.level().getPlayerByUUID(npc.getOwnerUUID());
            if (owner == null || !owner.isAlive()) return null;
            LivingEntity victim = owner.getLastHurtMob();
            if (victim == null || !victim.isAlive() || victim == npc) return null;
            if (npc.isOwnedBy(victim.getUUID())) return null;
            double range = npc.isPassenger() ? NEARBY_SCAN_RANGE_MOUNTED : NEARBY_SCAN_RANGE;
            if (npc.distanceToSqr(victim) > range * range) return null;
            return new TargetResult(victim, TargetReason.OWNER_ATTACKING);
        };
    }

    /** 优先级 3：自卫（谁打了这个 NPC） */
    public static TargetSource selfDefense() {
        return npc -> {
            LivingEntity attacker = npc.getLastHurtByMob();
            if (attacker == null || !attacker.isAlive() || attacker == npc) return null;
            if (npc.isOwnedBy(attacker.getUUID())) return null;
            return new TargetResult(attacker, TargetReason.SELF_DEFENSE);
        };
    }

    /** 优先级 4：仇恨候选（由 faction 事件/hurt 写入的 hostileTargetCandidate） */
    public static TargetSource revenge() {
        return npc -> {
            LivingEntity candidate = npc.getHostileTargetCandidate();
            if (candidate == null || !candidate.isAlive()) return null;
            if (npc.isOwnedBy(candidate.getUUID())) return null;
            npc.setHostileTargetCandidate(null); // 消费后清除
            return new TargetResult(candidate, TargetReason.REVENGE);
        };
    }

    /** 优先级 5：附近敌对生物扫描 */
    public static TargetSource nearbyHostile() {
        return npc -> {
            var npcFaction = npc.getFaction();
            // 不再用 canBeHostile() 门控——NPC 应始终能扫描敌人
            // canBeHostile 只影响对玩家/NPC 的检测，不影响对原版怪物的检测

            double range = npc.isPassenger() ? NEARBY_SCAN_RANGE_MOUNTED : NEARBY_SCAN_RANGE;
            AABB box = npc.getBoundingBox().inflate(range);

            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;

            // 扫描敌对声望玩家（仅当阵营支持敌对时才检查声望）
            if (npcFaction != null && npcFaction.canBeHostile()) {
                List<Player> players = npc.level().getEntitiesOfClass(Player.class, box,
                        p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                                && !npc.isOwnedBy(p.getUUID()));
                for (Player p : players) {
                    var standings = FactionAttachments.getStandings(p);
                    if (!standings.isHostile(npcFaction)) continue;
                    double dist = npc.distanceToSqr(p);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = p;
                    }
                }
            }

            // 扫描敌对 NPC（仅当阵营支持敌对时）
            if (npcFaction != null && npcFaction.canBeHostile()) {
                List<NpcBase> nearbyNpcs = npc.level().getEntitiesOfClass(NpcBase.class, box,
                        n -> n.isAlive() && n != npc
                                && StandingModifier.isHostileBetween(
                                        npc, n, npc.getOwnerUUID(), n.getOwnerUUID()));
                for (NpcBase n : nearbyNpcs) {
                    double dist = npc.distanceToSqr(n);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = n;
                    }
                }
            }

            // 扫描原版怪物——始终视为敌对目标，不受阵营限制
            List<Mob> monsters = npc.level().getEntitiesOfClass(Mob.class, box,
                    m -> m.isAlive() && m != npc
                            && m.getType().getCategory() == MobCategory.MONSTER
                            && !npc.isOwnedBy(m.getUUID()));
            for (Mob m : monsters) {
                double dist = npc.distanceToSqr(m);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = m;
                }
            }

            if (best != null) {
                return new TargetResult(best, TargetReason.NEARBY_HOSTILE);
            }
            return null;
        };
    }
}
