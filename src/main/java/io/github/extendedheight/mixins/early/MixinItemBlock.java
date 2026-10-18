package io.github.extendedheight.mixins.early;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.extendedheight.api.ExtendedHeight;

/** y == 255 && solid -> нельзя поставить: верх мира — maxY (2.17) */
@Mixin(ItemBlock.class)
public abstract class MixinItemBlock {

    @ModifyConstant(method = "onItemUse", constant = @Constant(intValue = 255), require = 1, allow = 1)
    private int eh$topY(int value, ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return ExtendedHeight.getMaxY(world);
    }
}
