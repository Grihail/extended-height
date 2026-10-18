package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.core.EhMath;

/** EntityBat.updateAITasks: (int) posY ниже 0 в расширенных мирах — floor(y) ({@link EhMath#yForIntCast}) */
@Mixin(EntityBat.class)
public abstract class MixinBatY {

    /** блок над висящей мышью */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityBat;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y0(EntityBat e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** звук 1015 (просыпается) */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityBat;posY:D", ordinal = 1),
        require = 1,
        allow = 1)
    private double eh$y1(EntityBat e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** звук 1015 (просыпается) */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityBat;posY:D", ordinal = 2),
        require = 1,
        allow = 1)
    private double eh$y2(EntityBat e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** расстояние до цели полёта */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityBat;posY:D", ordinal = 3),
        require = 1,
        allow = 1)
    private double eh$y3(EntityBat e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** новая цель полёта */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityBat;posY:D", ordinal = 4),
        require = 1,
        allow = 1)
    private double eh$y4(EntityBat e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** блок сверху (повисает) */
    @Redirect(
        method = "updateAITasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityBat;posY:D", ordinal = 6),
        require = 1,
        allow = 1)
    private double eh$y5(EntityBat e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** Не каст: цели ниже y 1 отбрасывались каждый тик; в расширенных мирах предел minY + 1 */
    @ModifyConstant(method = "updateAITasks", constant = @Constant(intValue = 1, ordinal = 1), require = 1, allow = 1)
    private int eh$lowestTarget(int one) {
        World world = ((Entity) (Object) this).worldObj;
        return world != null && ExtendedHeight.isExtended(world) ? ExtendedHeight.getMinY(world) + 1 : one;
    }
}
