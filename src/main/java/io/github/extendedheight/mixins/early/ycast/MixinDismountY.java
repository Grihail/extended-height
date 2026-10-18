package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityLivingBase.dismountEntity, точка спешивания: (int) posY ниже 0 — floor(y) */
@Mixin(EntityLivingBase.class)
public abstract class MixinDismountY {

    /** твёрдый верх на (int) posY */
    @Redirect(
        method = "dismountEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y0(EntityLivingBase e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** твёрдый верх на (int) posY - 1 */
    @Redirect(
        method = "dismountEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;posY:D", ordinal = 2),
        require = 1,
        allow = 1)
    private double eh$y1(EntityLivingBase e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** вода на (int) posY - 1 */
    @Redirect(
        method = "dismountEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;posY:D", ordinal = 3),
        require = 1,
        allow = 1)
    private double eh$y2(EntityLivingBase e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
