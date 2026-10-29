package io.github.extendedheight.mixins.early.compat.dh;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Чанк Distant Horizons: диапазон расширенного мира вместо зашитых 0…256 (секция index — minY + index * 16) */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper", remap = false)
public abstract class MixinDhChunkWrapper {

    @Shadow
    @Final
    private Chunk chunk;

    /** Диапазон мира чанка; null — ванильный, значения DH не меняются */
    private static HeightRange eh$extended(Chunk chunk) {
        if (chunk == null || chunk.worldObj == null) {
            return null;
        }
        HeightRange range = ExtendedHeight.getRange(chunk.worldObj);
        return range.isVanilla() ? null : range;
    }

    @Inject(
        method = "getHeight(Lnet/minecraft/world/chunk/Chunk;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private static void eh$height(Chunk chunk, CallbackInfoReturnable<Integer> cir) {
        HeightRange range = eh$extended(chunk);
        if (range != null) {
            cir.setReturnValue(range.sectionCount() * 16);
        }
    }

    @Inject(
        method = "getInclusiveMinBuildHeight(Lnet/minecraft/world/chunk/Chunk;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private static void eh$minY(Chunk chunk, CallbackInfoReturnable<Integer> cir) {
        HeightRange range = eh$extended(chunk);
        if (range != null) {
            cir.setReturnValue(range.minY);
        }
    }

    @Inject(
        method = "getExclusiveMaxBuildHeight(Lnet/minecraft/world/chunk/Chunk;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private static void eh$maxY(Chunk chunk, CallbackInfoReturnable<Integer> cir) {
        HeightRange range = eh$extended(chunk);
        if (range != null) {
            cir.setReturnValue(range.maxYExclusive());
        }
    }

    /** Карта высот без DH-расчёта: «верх мира» — maxY, а не 255 */
    @ModifyConstant(method = "getSolidHeightMapValue", constant = @Constant(intValue = 255), require = 1)
    private int eh$solidHeightFallback(int original) {
        HeightRange range = eh$extended(this.chunk);
        return range != null ? range.maxY : original;
    }
}
