package io.github.extendedheight.api;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;

import io.github.extendedheight.core.IExtendedHeightWorld;
import io.github.extendedheight.core.RangeRegistry;

/** Точка входа API: register* до загрузки миров или IExtendedHeightProvider; диапазон фиксируется в level.dat */
public final class ExtendedHeight {

    /** Версия API MAJOR.MINOR: MAJOR — несовместимые изменения, MINOR — добавления; совпадает с FML @API */
    public static final String API_VERSION = "1.0";

    private ExtendedHeight() {}

    /** Запрашивает расширенный диапазон для id измерения */
    public static void register(int dimensionId, int minY, int maxY) {
        RangeRegistry.registerDimension(dimensionId, HeightRange.of(minY, maxY));
    }

    /** Запрашивает расширенный диапазон для верхнего мира (измерение 0) миров этого типа */
    public static void register(WorldType worldType, int minY, int maxY) {
        RangeRegistry.registerWorldType(worldType, HeightRange.of(minY, maxY));
    }

    /** Действующий диапазон загруженного мира; {@link HeightRange#VANILLA} для нерасширенных */
    public static HeightRange getRange(World world) {
        return ((IExtendedHeightWorld) world).eh$getRange();
    }

    public static boolean isExtended(World world) {
        return !getRange(world).isVanilla();
    }

    /** Биом в точке: через {@link IBiomeAtProvider} мира, иначе биом столбца */
    public static net.minecraft.world.biome.BiomeGenBase getBiome(World world, int x, int y, int z) {
        return io.github.extendedheight.core.BiomeLookup.at(world, x, y, z);
    }

    /** Нижний допустимый y блока (0 в ванильных мирах) */
    public static int getMinY(World world) {
        return getRange(world).minY;
    }

    /** Верхний допустимый y блока (255 в ванильных мирах) */
    public static int getMaxY(World world) {
        return getRange(world).maxY;
    }
}
