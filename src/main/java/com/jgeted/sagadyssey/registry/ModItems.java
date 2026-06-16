package com.jgeted.sagadyssey.registry;

import com.jgeted.sagadyssey.Sagadyssey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(Sagadyssey.MOD_ID);

    public static final DeferredItem<BlockItem> TEST_BLOCK_ITEM = register(
            "test_block",
            () -> new BlockItem(ModBlocks.TEST_BLOCK.get(), new Item.Properties()));

    private static <T extends Item> DeferredItem<T> register(String name, Supplier<T> item) {
        return REGISTRY.register(name, item);
    }
}
