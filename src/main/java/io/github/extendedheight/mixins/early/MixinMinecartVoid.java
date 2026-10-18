package io.github.extendedheight.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** У EntityMinecart своя копия проверки пустоты (7.2) */
@Mixin(EntityMinecart.class)
public abstract class MixinMinecartVoid {

    @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = -64.0D), require = 1, allow = 1)
    private double eh$voidY(double value) {
        Entity self = (Entity) (Object) this;
        return self.worldObj == null ? value : ExtendedHeight.getMinY(self.worldObj) - 64.0D;
    }
}
