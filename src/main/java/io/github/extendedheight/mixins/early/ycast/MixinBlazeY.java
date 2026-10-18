package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.monster.EntityBlaze;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityBlaze, звук выстрела: (int) posY ниже 0 — floor(y) ({@link EhMath#yForIntCast}) */
@Mixin(EntityBlaze.class)
public abstract class MixinBlazeY {

    /** звук 1009 */
    @Redirect(
        method = "attackEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/monster/EntityBlaze;posY:D", ordinal = 1),
        require = 1,
        allow = 1)
    private double eh$y0(EntityBlaze e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
