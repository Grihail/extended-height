package io.github.extendedheight.core;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import io.github.extendedheight.api.IBiomeAtProvider;
import io.github.extendedheight.mixins.early.ChunkCacheAccessor;

/** Биом в точке через {@link IBiomeAtProvider}; для миров без него — биом столбца */
public final class BiomeLookup {

    private BiomeLookup() {}

    /** Интерфейс провайдера мира, иначе типа мира (только измерение 0), иначе null */
    public static IBiomeAtProvider providerOf(World world) {
        if (world == null) {
            return null;
        }
        if (world.provider instanceof IBiomeAtProvider) {
            return (IBiomeAtProvider) world.provider;
        }
        if (world.provider.dimensionId == 0 && world.getWorldInfo()
            .getTerrainType() instanceof IBiomeAtProvider) {
            return (IBiomeAtProvider) world.getWorldInfo()
                .getTerrainType();
        }
        return null;
    }

    /** Мир за доступом к блокам: сам мир, мир ChunkCache рендера или null */
    public static World worldOf(IBlockAccess access) {
        if (access instanceof World) {
            return (World) access;
        }
        if (access instanceof ChunkCache) {
            return ((ChunkCacheAccessor) access).eh$getWorld();
        }
        return null;
    }

    public static BiomeGenBase at(World world, int x, int y, int z) {
        BiomeGenBase column = world.getBiomeGenForCoords(x, z);
        IBiomeAtProvider p = providerOf(world);
        if (p == null) {
            return column;
        }
        BiomeGenBase b = p.getBiome(world, x, y, z, column);
        return b != null ? b : column;
    }

    /** Для перенаправленных access.getBiomeGenForCoords(x, z), где известен y */
    public static BiomeGenBase at(IBlockAccess access, int x, int y, int z) {
        World world = worldOf(access);
        if (world == null || providerOf(world) == null) {
            return access.getBiomeGenForCoords(x, z);
        }
        BiomeGenBase column = access.getBiomeGenForCoords(x, z);
        BiomeGenBase b = providerOf(world).getBiome(world, x, y, z, column);
        return b != null ? b : column;
    }

    /** y, куда в столбце падают осадки (там, где игра спрашивает биом для дождя или снега) */
    public static int precipitationY(World world, int x, int z) {
        return world.getPrecipitationHeight(x, z);
    }
}
