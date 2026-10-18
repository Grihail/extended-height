package io.github.extendedheight.mixins.early;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** Выход из лужи ниже 0: в расширенных мирах лишний блок вниз при minY < 0 не берётся */
@Mixin(World.class)
public abstract class MixinWorldLiquidY {

    @ModifyConstant(
        method = { "isAnyLiquid", "checkBlockCollision" },
        constant = @Constant(doubleValue = 0.0D, ordinal = 1),
        require = 2,
        allow = 2)
    private double eh$noExtraBlockBelow(double zero) {
        return ExtendedHeight.isExtended((World) (Object) this) ? Double.NEGATIVE_INFINITY : zero;
    }
}
