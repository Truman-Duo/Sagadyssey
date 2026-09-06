package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;

/**
 * 目标源接口：评估是否有一个合法的攻击目标。
 */
@FunctionalInterface
public interface TargetSource {
    TargetResult evaluate(NpcBase npc);
}
