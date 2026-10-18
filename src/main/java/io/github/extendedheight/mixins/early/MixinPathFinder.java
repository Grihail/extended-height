package io.github.extendedheight.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.ExtendedHeight;

/** createEntityPathTo: (int) minY ниже 0 даёт блок выше — подставляем floor(minY) */
@Mixin(PathFinder.class)
public abstract class MixinPathFinder {

    @Redirect(
        method = "createEntityPathTo(Lnet/minecraft/entity/Entity;DDDF)Lnet/minecraft/pathfinding/PathEntity;",
        at = @At(value = "FIELD", target = "Lnet/minecraft/util/AxisAlignedBB;minY:D", ordinal = 1),
        require = 1,
        allow = 1)
    private double eh$swimStartMinY(AxisAlignedBB box) {
        return Math.floor(box.minY);
    }

    /** 7.8, getSafePoint: поиск спуска с уступа идёт до minY, а не до 0 */
    @ModifyConstant(
        method = "getSafePoint",
        constant = { @Constant(intValue = 0, ordinal = 3, expandZeroConditions = Condition.GREATER_THAN_ZERO),
            @Constant(intValue = 0, ordinal = 4, expandZeroConditions = Condition.GREATER_THAN_ZERO) },
        require = 2,
        allow = 2)
    private int eh$safePointBottom(int zero, Entity entity, int x, int y, int z, PathPoint size, int step) {
        return entity.worldObj != null ? ExtendedHeight.getMinY(entity.worldObj) : zero;
    }
}
