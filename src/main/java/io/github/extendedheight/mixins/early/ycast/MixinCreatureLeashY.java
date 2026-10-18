package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityCreature.updateLeashedState, дом моба на поводке: (int) posY ниже 0 — floor(y) */
@Mixin(EntityCreature.class)
public abstract class MixinCreatureLeashY {

    /** центр дома = держатель поводка */
    @Redirect(
        method = "updateLeashedState",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y0(Entity e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
