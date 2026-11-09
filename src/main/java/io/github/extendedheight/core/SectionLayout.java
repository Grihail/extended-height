package io.github.extendedheight.core;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.HeightRange;

/** Секции, положенные генератором в storageArrays[y >> 4] по-ванильному, переносятся на индекс по своему y */
public final class SectionLayout {

    private static boolean warnedOutOfRange;

    private SectionLayout() {}

    /** Вызывается сразу после provideChunk генератора, до populate; true — что-то перенесено */
    public static boolean normalize(Chunk chunk, HeightRange range) {
        if (chunk == null || range.isVanilla()) {
            return false;
        }
        ExtendedBlockStorage[] array = chunk.getBlockStorageArray();
        int minSection = range.minSection();
        ExtendedBlockStorage[] misplaced = null;
        for (int i = 0; i < array.length; i++) {
            ExtendedBlockStorage s = array[i];
            if (s != null && (s.getYLocation() >> 4) - minSection != i) {
                if (misplaced == null) {
                    misplaced = new ExtendedBlockStorage[array.length];
                }
                misplaced[i] = s;
                array[i] = null;
            }
        }
        if (misplaced == null) {
            return false;
        }
        for (ExtendedBlockStorage s : misplaced) {
            if (s == null) {
                continue;
            }
            int target = (s.getYLocation() >> 4) - minSection;
            if (target < 0 || target >= array.length) {
                if (!warnedOutOfRange) {
                    warnedOutOfRange = true;
                    ExtendedHeightMod.LOG.warn(
                        "Chunk generator produced a section at y {} outside the world range {}; dropped",
                        s.getYLocation(),
                        range);
                }
                continue;
            }
            if (array[target] == null) {
                array[target] = s;
            } else {
                merge(s, array[target]);
            }
        }
        // Карта высот и небесный свет считались по неверной раскладке
        chunk.generateSkylightMap();
        return true;
    }

    /** Не-воздух из from поверх into (генератор записал ту же высоту и через конструктор, и напрямую) */
    private static void merge(ExtendedBlockStorage from, ExtendedBlockStorage into) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Block block = from.getBlockByExtId(x, y, z);
                    if (block != Blocks.air) {
                        into.func_150818_a(x, y, z, block);
                        into.setExtBlockMetadata(x, y, z, from.getExtBlockMetadata(x, y, z));
                    }
                }
            }
        }
    }
}
