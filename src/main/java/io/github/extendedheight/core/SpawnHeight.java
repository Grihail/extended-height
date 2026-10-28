package io.github.extendedheight.core;

import java.util.Random;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import io.github.extendedheight.api.HeightRange;

/** Высота случайной точки естественного спавна в расширенном мире (7.6), общая для ванили и Hodgepodge */
public final class SpawnHeight {

    private SpawnHeight() {}

    /** bottom + rand.nextInt(max(1, top - bottom)): один вызов rand, низ — нижняя существующая секция */
    public static int randomY(Random rand, Chunk chunk, HeightRange range) {
        int top = chunk == null ? range.maxYExclusive() : chunk.getTopFilledSegment() + 16 - 1;
        int bottom = chunk == null ? range.minY : bottomFilledSegment(chunk, range);
        return bottom + rand.nextInt(Math.max(1, top - bottom));
    }

    /** Нижний y нижней непустой секции; minY для пустого чанка */
    public static int bottomFilledSegment(Chunk chunk, HeightRange range) {
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        for (ExtendedBlockStorage section : storage) {
            if (section != null) {
                return section.getYLocation();
            }
        }
        return range.minY;
    }
}
