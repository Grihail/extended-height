package io.github.extendedheight.mixins.early.client;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** Клиентские методы ChunkCache для яркости: нули 0, 1, 2 заменены, return 0 (ординал 3) остаётся */
@Mixin(ChunkCache.class)
public abstract class MixinChunkCacheClient {

    @Shadow
    private World worldObj;

    @ModifyConstant(
        method = { "getSkyBlockTypeBrightness", "getSpecialBlockBrightness" },
        constant = { @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.LESS_THAN_ZERO),
            @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.LESS_THAN_ZERO),
            @Constant(intValue = 0, ordinal = 2, expandZeroConditions = Condition.LESS_THAN_ZERO) },
        require = 6,
        allow = 6)
    private int eh$minY(int zero) {
        return ExtendedHeight.getMinY(worldObj);
    }

    @ModifyConstant(
        method = { "getSkyBlockTypeBrightness", "getSpecialBlockBrightness", "getHeight" },
        constant = @Constant(intValue = 256),
        require = 5,
        allow = 5)
    private int eh$maxYExcl(int value) {
        return ExtendedHeight.getMaxY(worldObj) + 1;
    }

    @ModifyConstant(
        method = { "getSkyBlockTypeBrightness", "getSpecialBlockBrightness" },
        constant = @Constant(intValue = 255),
        require = 2,
        allow = 2)
    private int eh$maxY(int value) {
        return ExtendedHeight.getMaxY(worldObj);
    }
}
