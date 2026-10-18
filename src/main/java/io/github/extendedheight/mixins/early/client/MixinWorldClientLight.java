package io.github.extendedheight.mixins.early.client;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** Клиентский getSkyBlockTypeBrightness: нули 0 — return 0, 1 — сравнение, 2 — зажим */
@Mixin(World.class)
public abstract class MixinWorldClientLight {

    @ModifyConstant(
        method = "getSkyBlockTypeBrightness",
        constant = { @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.LESS_THAN_ZERO),
            @Constant(intValue = 0, ordinal = 2, expandZeroConditions = Condition.LESS_THAN_ZERO) },
        require = 2,
        allow = 2)
    private int eh$minY(int zero) {
        return ExtendedHeight.getMinY((World) (Object) this);
    }

    @ModifyConstant(method = "getSkyBlockTypeBrightness", constant = @Constant(intValue = 256), require = 1, allow = 1)
    private int eh$maxYExcl(int value) {
        return ExtendedHeight.getMaxY((World) (Object) this) + 1;
    }
}
