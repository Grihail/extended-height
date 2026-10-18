package io.github.extendedheight.mixins.early;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** ChunkCache (2.15): границы y 0/256/255 — по диапазону мира */
@Mixin(ChunkCache.class)
public abstract class MixinChunkCache {

    @Unique
    protected int eh$minY = 0;
    @Unique
    protected int eh$maxY = 255;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1, allow = 1)
    private void eh$captureRange(World world, int x1, int y1, int z1, int x2, int y2, int z2, int border,
        CallbackInfo ci) {
        if (world != null) {
            HeightRange range = ExtendedHeight.getRange(world);
            eh$minY = range.minY;
            eh$maxY = range.maxY;
        }
    }

    /** getBlock: y >= 0 && y < 256; getBlockMetadata: if (y < 0) return 0; — первая проверка нуля */
    @ModifyConstant(
        method = { "getBlock", "getBlockMetadata" },
        constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.LESS_THAN_ZERO),
        require = 2,
        allow = 2)
    private int eh$minY(int zero) {
        return eh$minY;
    }

    @ModifyConstant(
        method = { "getBlock", "getBlockMetadata" },
        constant = @Constant(intValue = 256),
        require = 2,
        allow = 2)
    private int eh$maxYExcl(int value) {
        return eh$maxY + 1;
    }
}
