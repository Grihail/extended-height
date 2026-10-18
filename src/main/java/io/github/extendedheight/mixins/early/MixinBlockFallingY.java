package io.github.extendedheight.mixins.early;

import net.minecraft.block.BlockFalling;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** 8.4, падающие блоки: ванильные пределы y >= 0 и y > 0 становятся minY */
@Mixin(BlockFalling.class)
public abstract class MixinBlockFallingY {

    @ModifyConstant(
        method = "func_149830_m",
        constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
        require = 1,
        allow = 1)
    private int eh$canFallMinY(int zero, World world, int x, int y, int z) {
        return ExtendedHeight.getMinY(world);
    }

    @ModifyConstant(
        method = "func_149830_m",
        constant = { @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.GREATER_THAN_ZERO),
            @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.GREATER_THAN_ZERO) },
        require = 2,
        allow = 2)
    private int eh$instantFallMinY(int zero, World world, int x, int y, int z) {
        return ExtendedHeight.getMinY(world);
    }
}
