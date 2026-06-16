package com.jgeted.sagadyssey.registry;

import com.jgeted.sagadyssey.Sagadyssey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Sagadyssey.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SAGADYSSEY_TAB =
            REGISTRY.register("sagadyssey_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("Sagadyssey"))
                    .icon(() -> new ItemStack(ModBlocks.TEST_BLOCK.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.TEST_BLOCK.get());
                    })
                    .build());
}
