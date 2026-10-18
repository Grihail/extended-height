package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.monster.EntityGhast;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityGhast, звуки: (int) posY ниже 0 — floor(y) */
@Mixin(EntityGhast.class)
public abstract class MixinGhastY {

    /** звук 1007 */
    @Redirect(
        method = "updateEntityActionState",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/monster/EntityGhast;posY:D", ordinal = 4),
        require = 1,
        allow = 1)
    private double eh$y0(EntityGhast e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** звук 1008 */
    @Redirect(
        method = "updateEntityActionState",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/monster/EntityGhast;posY:D", ordinal = 5),
        require = 1,
        allow = 1)
    private double eh$y1(EntityGhast e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
