package io.github.extendedheight.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.HeightRange;

/** Диапазоны, запрошенные через ExtendedHeight.register */
public final class RangeRegistry {

    private static final Map<Integer, HeightRange> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<WorldType, HeightRange> BY_WORLD_TYPE = new ConcurrentHashMap<>();

    private RangeRegistry() {}

    public static void registerDimension(int dimensionId, HeightRange range) {
        HeightRange previous = BY_DIMENSION.put(dimensionId, range);
        if (previous != null && !previous.equals(range)) {
            ExtendedHeightMod.LOG
                .warn("Dimension {}: height range re-registered {} -> {}", dimensionId, previous, range);
        }
        warnIfLoaded(dimensionId);
    }

    public static void registerWorldType(WorldType worldType, HeightRange range) {
        HeightRange previous = BY_WORLD_TYPE.put(worldType, range);
        if (previous != null && !previous.equals(range)) {
            ExtendedHeightMod.LOG.warn(
                "World type {}: height range re-registered {} -> {}",
                worldType.getWorldTypeName(),
                previous,
                range);
        }
        warnIfLoaded(0);
    }

    public static HeightRange forDimension(int dimensionId) {
        return BY_DIMENSION.get(dimensionId);
    }

    public static HeightRange forWorldType(WorldType worldType) {
        return worldType == null ? null : BY_WORLD_TYPE.get(worldType);
    }

    private static void warnIfLoaded(int dimensionId) {
        if (DimensionManager.getWorld(dimensionId) != null) {
            ExtendedHeightMod.LOG.warn(
                "Height range for dimension {} registered after the dimension was loaded; "
                    + "it takes effect only for dimensions created later. Register during pre-init/init.",
                dimensionId);
        }
    }
}
