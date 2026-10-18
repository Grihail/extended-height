package io.github.extendedheight.mixins.early.client;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** Сущности ниже 0 на клиенте не прыгают в y 0: поиск верхнего бокса начинается с -бесконечности */
@Mixin(Entity.class)
public abstract class MixinEntityPosition {

    @Shadow
    public World worldObj;

    /** Константа 0 — contract(0.03125, 0.0, 0.03125), константа 1 — double d3 = 0.0 */
    @ModifyConstant(
        method = "setPositionAndRotation2",
        constant = @Constant(doubleValue = 0.0D, ordinal = 1),
        require = 1,
        allow = 1)
    private double eh$highestBoxStart(double zero) {
        return worldObj != null && ExtendedHeight.isExtended(worldObj) ? Double.NEGATIVE_INFINITY : zero;
    }
}
