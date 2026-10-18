package io.github.extendedheight.mixins.early.biome;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): снег, лёд и молния */
@Mixin(World.class)
public abstract class MixinWorldBiomeAt {

    @Redirect(
        method = { "canBlockFreezeBody", "canSnowAtBody" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;",
            remap = true),
        remap = false,
        require = 2,
        allow = 2)
    private BiomeGenBase eh$weather(World world, int x, int z, int bx, int y) {
        return BiomeLookup.at(world, x, y, z);
    }

    @Redirect(
        method = "canLightningStrikeAt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$lightning(World world, int x, int z, int bx, int y) {
        return BiomeLookup.at(world, x, y, z);
    }
}
