package com.jgeted.sagadyssey.npc.ai;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;

/**
 * NPC 武器切换处理器。
 * 检测主手物品类型，动态替换近战/远程 AI goal。
 */
public class NpcWeaponSwapHandler {

    private NpcWeaponSwapHandler() {}

    /**
     * 当 NPC 手持物品变更时调用。
     * 根据武器类型决定启用近战还是远程 AI。
     */
    public static void onWeaponChanged(NpcBase npc, ItemStack newWeapon) {
        if (newWeapon.isEmpty()) {
            // 空手 → 确保近战模式
            npc.ensureMeleeMode();
            return;
        }

        if (newWeapon.getItem() instanceof BowItem || newWeapon.getItem() instanceof CrossbowItem) {
            npc.ensureRangedMode();
        } else if (isMeleeWeapon(newWeapon)) {
            npc.ensureMeleeMode();
        }
        // 非武器物品（工具、食物等）不改变当前模式
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
}
