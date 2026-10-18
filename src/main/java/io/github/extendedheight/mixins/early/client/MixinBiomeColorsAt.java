package io.github.extendedheight.mixins.early.client;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.BlockVine;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): цвета блоков; при рендере чанков берётся мир ChunkCache */
@Mixin({ BlockLiquid.class, BlockGrass.class, BlockLeaves.class, BlockVine.class, BlockTallGrass.class,
    BlockDoublePlant.class, BlockReed.class })
public abstract class MixinBiomeColorsAt {

    @Redirect(
        method = "colorMultiplier",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$colour(IBlockAccess access, int x, int z, IBlockAccess world, int bx, int y) {
        return BiomeLookup.at(access, x, y, z);
    }
}
