package io.github.extendedheight.api;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/** Биом по точке, а не по столбцу: на WorldProvider или WorldType; вызывается часто — должен быть дешёвым */
public interface IBiomeAtProvider {

    /** @return биом в точке или null для биома столбца */
    BiomeGenBase getBiome(World world, int x, int y, int z, BiomeGenBase columnBiome);
}
