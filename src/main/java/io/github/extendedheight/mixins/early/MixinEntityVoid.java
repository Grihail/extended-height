package io.github.extendedheight.mixins.early;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** posY < -64 -> kill() (7.1): 64 блока ниже дна мира; в ванильных мирах по-прежнему -64 */
@Mixin(Entity.class)
public abstract class MixinEntityVoid {

    @Shadow
    public net.minecraft.world.World worldObj;

    @ModifyConstant(method = "onEntityUpdate", constant = @Constant(doubleValue = -64.0D), require = 1, allow = 1)
    private double eh$voidY(double value) {
        return worldObj == null ? value : ExtendedHeight.getMinY(worldObj) - 64.0D;
    }
}
