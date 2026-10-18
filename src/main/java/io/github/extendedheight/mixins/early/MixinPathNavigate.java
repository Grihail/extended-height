package io.github.extendedheight.mixins.early;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.core.EhMath;

/** Мобы ниже 0 стояли: (int)-касты в PathNavigate заменены на floor в расширенных мирах */
@Mixin(PathNavigate.class)
public abstract class MixinPathNavigate {

    @Shadow
    private EntityLiving theEntity;
    @Shadow
    private World worldObj;
    @Shadow
    private boolean canSwim;

    @Inject(method = "getPathableYPos", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$pathableYPos(CallbackInfoReturnable<Integer> cir) {
        if (!ExtendedHeight.isExtended(worldObj)) {
            return;
        }
        AxisAlignedBB box = theEntity.boundingBox;
        if (theEntity.isInWater() && canSwim) {
            int x = MathHelper.floor_double(theEntity.posX);
            int z = MathHelper.floor_double(theEntity.posZ);
            int y = MathHelper.floor_double(box.minY);
            for (int j = 0; j <= 16; ++j) {
                Block block = worldObj.getBlock(x, y, z);
                if (block != Blocks.flowing_water && block != Blocks.water) {
                    cir.setReturnValue(y);
                    return;
                }
                ++y;
            }
            cir.setReturnValue(MathHelper.floor_double(box.minY));
        } else {
            cir.setReturnValue(MathHelper.floor_double(box.minY + 0.5D));
        }
    }

    /** removeSunnyPath: подставляем floor(minY + 0.5) - 0.5, чтобы каст дал floor */
    @Redirect(
        method = "removeSunnyPath",
        at = @At(value = "FIELD", target = "Lnet/minecraft/util/AxisAlignedBB;minY:D"),
        require = 1,
        allow = 1)
    private double eh$sunnyPathMinY(AxisAlignedBB box) {
        double minY = box.minY;
        if (!ExtendedHeight.isExtended(worldObj)) {
            return minY;
        }
        return Math.floor(minY + 0.5D) - 0.5D;
    }

    /** Целевой y getPathToXYZ/tryMoveToXYZ ниже 0 заменён на floor */
    @ModifyVariable(
        method = { "getPathToXYZ", "tryMoveToXYZ" },
        at = @At("HEAD"),
        ordinal = 1,
        argsOnly = true,
        require = 2,
        allow = 2)
    private double eh$targetY(double y) {
        return EhMath.yForIntCast(worldObj, y);
    }
}
