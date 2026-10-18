package io.github.extendedheight.core;

import java.io.File;
import java.util.Map;

import net.minecraft.world.World;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.api.IExtendedHeightProvider;
import io.github.extendedheight.mixins.early.DerivedWorldInfoAccessor;

/** Выбор диапазона мира в конструкторе World: level.dat, иначе запрос, если нет сохранённых чанков; иначе ваниль */
public final class RangeResolver {

    private RangeResolver() {}

    public static HeightRange resolve(World world) {
        if (world.isRemote) {
            // Клиентские миры получают диапазон от сервера (этап 3)
            return HeightRange.VANILLA;
        }
        int dim = world.provider.dimensionId;
        WorldInfo root = authoritativeInfo(world, dim);
        Map<Integer, HeightRange> table = ((IHeightTableHolder) root).eh$getHeightTable();
        HeightRange stored = table.get(dim);
        HeightRange requested = requested(world, root);

        if (stored != null) {
            if (requested != null && !requested.equals(stored)) {
                ExtendedHeightMod.LOG.warn(
                    "Dimension {}: level.dat stores height range {}, but {} is requested now; keeping {}",
                    dim,
                    stored,
                    requested,
                    stored);
            }
            return stored;
        }
        if (requested == null || requested.isVanilla()) {
            return HeightRange.VANILLA;
        }

        File regionDir = regionDirectory(world);
        if (regionDir == null) {
            ExtendedHeightMod.LOG.warn(
                "Dimension {}: height range {} requested, but the save handler {} is unknown, so it is impossible "
                    + "to check for existing chunks. The dimension stays vanilla.",
                dim,
                requested,
                world.getSaveHandler()
                    .getClass()
                    .getName());
            return HeightRange.VANILLA;
        }
        if (hasSavedChunks(regionDir)) {
            ExtendedHeightMod.LOG.warn(
                "Dimension {}: height range {} requested, but the dimension already has saved chunks in {} and was "
                    + "created without the extension. It stays vanilla (0..255) forever.",
                dim,
                requested,
                regionDir);
            return HeightRange.VANILLA;
        }

        table.put(dim, requested);
        ISaveHandler saveHandler = world.getSaveHandler();
        saveHandler.saveWorldInfo(root);
        ExtendedHeightMod.LOG.info("Dimension {}: new dimension, height range {} stored in level.dat", dim, requested);
        return requested;
    }

    static HeightRange requested(World world, WorldInfo root) {
        if (world.provider instanceof IExtendedHeightProvider) {
            HeightRange range = ((IExtendedHeightProvider) world.provider).getExtendedHeightRange();
            if (range != null) {
                return range;
            }
        }
        int dim = world.provider.dimensionId;
        HeightRange byDim = RangeRegistry.forDimension(dim);
        if (byDim != null) {
            return byDim;
        }
        if (dim == 0) {
            return RangeRegistry.forWorldType(root.getTerrainType());
        }
        return null;
    }

    /** WorldInfo, который реально пишется в level.dat: для WorldServerMulti — WorldInfo верхнего мира */
    static WorldInfo authoritativeInfo(World world, int dim) {
        if (dim != 0) {
            World overworld = DimensionManager.getWorld(0);
            if (overworld != null) {
                return rootInfo(overworld.getWorldInfo());
            }
            ExtendedHeightMod.LOG
                .warn("Dimension {} is created before dimension 0; using its own copy of level.dat data", dim);
        }
        return rootInfo(world.getWorldInfo());
    }

    public static WorldInfo rootInfo(WorldInfo info) {
        while (info instanceof DerivedWorldInfo) {
            info = ((DerivedWorldInfoAccessor) info).eh$getParent();
        }
        return info;
    }

    /** <save>/region для измерения 0, иначе <save>/<папка провайдера>/region */
    static File regionDirectory(World world) {
        ISaveHandler handler = world.getSaveHandler();
        if (!(handler instanceof SaveHandler)) {
            return null;
        }
        File root = ((SaveHandler) handler).getWorldDirectory();
        String folder = world.provider.getSaveFolder();
        File dimDir = folder == null ? root : new File(root, folder);
        return new File(dimDir, "region");
    }

    /** true, если в каталоге есть хотя бы один непустой файл региона (.mca или .mcr) */
    public static boolean hasSavedChunks(File regionDir) {
        File[] files = regionDir.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            String name = file.getName();
            if ((name.endsWith(".mca") || name.endsWith(".mcr")) && file.length() > 0) {
                return true;
            }
        }
        return false;
    }
}
