package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityAIFleeSun.shouldExecute: (int) minY ниже 0 — floor(y) */
@Mixin(EntityAIFleeSun.class)
public abstract class MixinFleeSunY {

    @Shadow
    private World theWorld;

    /** canBlockSeeTheSky((int) minY) */
    @Redirect(
        method = "shouldExecute",
        at = @At(value = "FIELD", target = "Lnet/minecraft/util/AxisAlignedBB;minY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y0(AxisAlignedBB e) {
        return EhMath.yForIntCast(theWorld, e.minY);
    }
}
