package io.github.extendedheight.mixins.early.client;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): рендер дождя и снега и брызги, по y падения осадков */
@Mixin(EntityRenderer.class)
public abstract class MixinRainBiomeAt {

    @Redirect(
        method = { "addRainParticles", "renderRainSnow" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 2,
        allow = 2)
    private BiomeGenBase eh$rain(WorldClient world, int x, int z) {
        return BiomeLookup.at(world, x, BiomeLookup.precipitationY(world, x, z), z);
    }
}
