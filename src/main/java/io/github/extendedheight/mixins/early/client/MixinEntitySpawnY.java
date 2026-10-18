package io.github.extendedheight.mixins.early.client;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** 7.4, preparePlayerToSpawn: выталкивание игрока из блоков работает до minY */
@Mixin(Entity.class)
public abstract class MixinEntitySpawnY {

    @Shadow
    public World worldObj;

    @ModifyConstant(
        method = "preparePlayerToSpawn",
        constant = @Constant(doubleValue = 0.0D, ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$bottom(double zero) {
        return worldObj != null && ExtendedHeight.isExtended(worldObj) ? ExtendedHeight.getMinY(worldObj) : zero;
    }
}
