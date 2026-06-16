package com.jgeted.sagadyssey.menu;

import com.jgeted.sagadyssey.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 测试用菜单容器。没有格子，仅用于打开 GUI 屏幕。
 */
public class TestMenu extends AbstractContainerMenu {

    public TestMenu(int containerId, Inventory inventory) {
        super(ModMenus.TEST_MENU.get(), containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
