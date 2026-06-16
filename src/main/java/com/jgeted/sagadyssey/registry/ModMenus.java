package com.jgeted.sagadyssey.registry;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.menu.TestMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, Sagadyssey.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TestMenu>> TEST_MENU =
            REGISTRY.register("test_menu",
                    () -> IMenuTypeExtension.create((id, inventory, data) -> new TestMenu(id, inventory)));
}
