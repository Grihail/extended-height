package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.boss.EntityWither;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityWither, звуки: (int) posY ниже 0 — floor(y) */
@Mixin(EntityWither.class)
public abstract class MixinWitherY {

    /** звук 1013 на весь мир */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/boss/EntityWither;posY:D", ordinal = 1),
        require = 1,
        allow = 1)
    private double eh$y0(EntityWither e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** звук 1012 */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/boss/EntityWither;posY:D", ordinal = 5),
        require = 1,
        allow = 1)
    private double eh$y1(EntityWither e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** звук 1014 */
    @Redirect(
        method = "func_82209_a",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/boss/EntityWither;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y2(EntityWither e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
