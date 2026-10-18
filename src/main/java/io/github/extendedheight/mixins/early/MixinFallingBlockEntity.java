package io.github.extendedheight.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** 7.5: падающий блок ниже y 1 становился предметом через 5 с; пределы minY + 1 и maxY + 1 */
@Mixin(EntityFallingBlock.class)
public abstract class MixinFallingBlockEntity {

    @Unique
    private World eh$world() {
        return ((Entity) (Object) this).worldObj;
    }

    @ModifyConstant(method = "onUpdate", constant = @Constant(intValue = 1, ordinal = 6), require = 1, allow = 1)
    private int eh$lowest(int one) {
        World w = eh$world();
        return w != null && ExtendedHeight.isExtended(w) ? ExtendedHeight.getMinY(w) + 1 : one;
    }

    @ModifyConstant(method = "onUpdate", constant = @Constant(intValue = 256), require = 1, allow = 1)
    private int eh$highest(int value) {
        World w = eh$world();
        return w != null && ExtendedHeight.isExtended(w) ? ExtendedHeight.getMaxY(w) + 1 : value;
    }
}
