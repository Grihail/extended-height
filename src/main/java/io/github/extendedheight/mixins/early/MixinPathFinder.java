package io.github.extendedheight.mixins.early;

import net.minecraft.pathfinding.PathFinder;
import net.minecraft.util.AxisAlignedBB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
