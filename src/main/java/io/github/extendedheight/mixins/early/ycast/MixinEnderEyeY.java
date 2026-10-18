package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemEnderEye;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** ItemEnderEye, звук броска: (int) posY ниже 0 — floor(y) */
@Mixin(ItemEnderEye.class)
public abstract class MixinEnderEyeY {

    /** звук 1002 */
    @Redirect(
        method = "onItemRightClick",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;posY:D", ordinal = 2),
        require = 1,
        allow = 1)
    private double eh$y0(EntityPlayer e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
