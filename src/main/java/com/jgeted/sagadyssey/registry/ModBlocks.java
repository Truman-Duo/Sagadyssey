package com.jgeted.sagadyssey.registry;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.block.TestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(Sagadyssey.MOD_ID);

    public static final DeferredBlock<TestBlock> TEST_BLOCK = register("test_block",
            () -> new TestBlock(BlockBehaviour.Properties.of()
                    .strength(1.0f)
                    .noOcclusion()));

    private static <T extends Block> DeferredBlock<T> register(String name, Supplier<T> block) {
        return REGISTRY.register(name, block);
    }
}
