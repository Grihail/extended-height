package io.github.extendedheight.mixins.early.biome;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.ChunkProviderEnd;
import net.minecraft.world.gen.ChunkProviderFlat;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.ChunkProviderHell;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): списки спавна ванильных провайдеров чанков */
@Mixin({ ChunkProviderGenerate.class, ChunkProviderFlat.class, ChunkProviderHell.class, ChunkProviderEnd.class })
public abstract class MixinChunkProvidersBiomeAt {

    @Redirect(
        method = "getPossibleCreatures",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$spawnList(World world, int x, int z, EnumCreatureType type, int bx, int y) {
        return BiomeLookup.at(world, x, y, z);
    }
}
