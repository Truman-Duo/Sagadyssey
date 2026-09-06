package com.jgeted.sagadyssey.mixin;

import com.jgeted.sagadyssey.Sagadyssey;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 结构防碰撞 Mixin。
 * 在 tryGenerateStructure 中 setStartForStructure 之前，
 * 检查该位置是否已被其他结构占用，避免不同 structure_set 之间的重叠。
 */
@Mixin(ChunkGenerator.class)
public class StructureCollisionMixin {

    @Unique
    private static final String TAG = "[STRUCTURE-COLLISION]";

    /**
     * 在 setStartForStructure 调用前拦截，
     * 如果 chunk 中心已有其他结构引用则跳过当前结构。
     */
    @Inject(
        method = "tryGenerateStructure",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/StructureManager;"
                    + "setStartForStructure("
                    + "Lnet/minecraft/core/SectionPos;"
                    + "Lnet/minecraft/world/level/levelgen/structure/Structure;"
                    + "Lnet/minecraft/world/level/levelgen/structure/StructureStart;"
                    + "Lnet/minecraft/world/level/levelgen/structure/StructureAccess;"
                    + ")V"
        ),
        cancellable = true
    )
    private void checkStructureCollision(
            StructureSet.StructureSelectionEntry entry,
            StructureManager structureManager,
            net.minecraft.core.RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkAccess chunkAccess,
            ChunkPos chunkPos,
            SectionPos sectionPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Structure currentStructure = entry.structure().value();
        BlockPos center = chunkPos.getWorldPosition();

        try {
            Map<Structure, LongSet> existing = structureManager.getAllStructuresAt(center);

            // 排除当前结构自身（多次调用 tryGenerateStructure 时）
            existing.remove(currentStructure);

            if (!existing.isEmpty()) {
                String currentName = entry.structure().unwrapKey()
                        .map(ResourceKey::location)
                        .map(Object::toString)
                        .orElse("?");
                Sagadyssey.LOGGER.debug("{} 拦截碰撞: {} at {} - 已有 {} 个其他结构",
                        TAG, currentName, chunkPos, existing.size());
                cir.setReturnValue(false);
            }
        } catch (Exception e) {
            // chunk 数据不可用时跳过碰撞检查，放行
            Sagadyssey.LOGGER.debug("{} 无法检查碰撞 at {}: {}", TAG, chunkPos, e.getMessage());
        }
    }
}
