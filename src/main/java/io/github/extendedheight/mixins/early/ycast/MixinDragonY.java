package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.boss.EntityDragon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityDragon, звук смерти: (int) posY ниже 0 — floor(y) */
@Mixin(EntityDragon.class)
public abstract class MixinDragonY {

    /** звук 1018 на весь мир */
    @Redirect(
        method = "onDeathUpdate",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/boss/EntityDragon;posY:D", ordinal = 2),
        require = 1,
        allow = 1)
    private double eh$y0(EntityDragon e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
