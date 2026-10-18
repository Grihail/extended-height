package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemShears;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** ItemShears, координаты для IShearable: (int) posY ниже 0 — floor(y) */
@Mixin(ItemShears.class)
public abstract class MixinShearsY {

    /** IShearable.isShearable(x, (int) y, z) */
    @Redirect(
        method = "itemInteractionForEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y0(EntityLivingBase e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** IShearable.onSheared(x, (int) y, z) */
    @Redirect(
        method = "itemInteractionForEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;posY:D", ordinal = 1),
        require = 1,
        allow = 1)
    private double eh$y1(EntityLivingBase e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
