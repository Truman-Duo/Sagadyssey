package com.jgeted.sagadyssey.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让可可豆也能贴在丛林木和去皮丛林木上。
 * 原版 CocoaBlock.canSurvive 只认丛林原木（jungle_log）。
 */
@Mixin(CocoaBlock.class)
public class CocoaBlockMixin {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void sagadyssey$acceptJungleWood(
            BlockState state,
            LevelReader level,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState attached = level.getBlockState(pos.relative(state.getValue(CocoaBlock.FACING)));
        if (attached.is(Blocks.JUNGLE_WOOD)
                || attached.is(Blocks.STRIPPED_JUNGLE_WOOD)
                || attached.is(Blocks.STRIPPED_JUNGLE_LOG)) {
            cir.setReturnValue(true);
        }
    }
}
