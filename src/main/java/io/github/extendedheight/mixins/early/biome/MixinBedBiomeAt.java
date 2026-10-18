package io.github.extendedheight.mixins.early.biome;

import net.minecraft.block.BlockBed;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): проверка кровати на ад */
@Mixin(BlockBed.class)
public abstract class MixinBedBiomeAt {

    @Redirect(
        method = "onBlockActivated",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$bed(World access, int x, int z, World world, int bx, int y) {
        return BiomeLookup.at(access, x, y, z);
    }
}
