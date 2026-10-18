package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.ai.EntityAIOcelotSit;
import net.minecraft.entity.passive.EntityOcelot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityAIOcelotSit, поиск сундука/кровати: (int) posY ниже 0 — floor(y) */
@Mixin(EntityAIOcelotSit.class)
public abstract class MixinOcelotSitY {

    /** слой поиска сундука или кровати */
    @Redirect(
        method = "func_151485_f",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityOcelot;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y0(EntityOcelot e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
