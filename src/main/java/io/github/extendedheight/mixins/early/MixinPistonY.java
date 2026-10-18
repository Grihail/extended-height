package io.github.extendedheight.mixins.early;

import net.minecraft.block.BlockPistonBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** 8.5, поршни: нижняя граница y <= 0 в canExtend/tryExtend становится minY */
@Mixin(BlockPistonBase.class)
public abstract class MixinPistonY {

    @ModifyConstant(
        method = "canExtend",
        constant = @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.LESS_THAN_OR_EQUAL_TO_ZERO),
        require = 1,
        allow = 1)
    private static int eh$canExtendBottom(int zero, World world, int x, int y, int z, int side) {
        return ExtendedHeight.getMinY(world);
    }

    @ModifyConstant(
        method = "tryExtend",
        constant = @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.LESS_THAN_OR_EQUAL_TO_ZERO),
        require = 1,
        allow = 1)
    private int eh$tryExtendBottom(int zero, World world, int x, int y, int z, int side) {
        return ExtendedHeight.getMinY(world);
    }
}
