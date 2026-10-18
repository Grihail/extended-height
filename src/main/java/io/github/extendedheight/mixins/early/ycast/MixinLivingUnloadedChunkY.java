package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** 7.3: на клиенте в незагруженном чанке сущность ниже 0 висела; граница posY > 0 становится minY */
@Mixin(EntityLivingBase.class)
public abstract class MixinLivingUnloadedChunkY {

    @ModifyConstant(
        method = "moveEntityWithHeading",
        constant = @Constant(doubleValue = 0.0D, ordinal = 2),
        require = 1,
        allow = 1)
    private double eh$bottom(double zero) {
        World world = ((Entity) (Object) this).worldObj;
        return world != null && ExtendedHeight.isExtended(world) ? ExtendedHeight.getMinY(world) : zero;
    }
}
