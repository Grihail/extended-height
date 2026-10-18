package io.github.extendedheight.mixins.early.biome;

import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): дождь наполняет котлы, по y падения осадков */
@Mixin(WorldServer.class)
public abstract class MixinWorldServerBiomeAt {

    @Redirect(
        method = "func_147456_g",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/WorldServer;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$rain(WorldServer world, int x, int z) {
        return BiomeLookup.at(world, x, BiomeLookup.precipitationY(world, x, z), z);
    }
}
