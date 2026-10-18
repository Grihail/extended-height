package io.github.extendedheight.selftest;

import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import io.github.extendedheight.api.ExtendedChunkPrimer;
import io.github.extendedheight.api.HeightRange;

/** Шаг 4 плана: тесты ExtendedChunkPrimer в фазе write */
final class PrimerTests {

    private final SelfTest report;
    private final WorldServer ext;
    private final WorldServer overworld;

    PrimerTests(SelfTest report, WorldServer ext, WorldServer overworld) {
        this.report = report;
        this.ext = ext;
        this.overworld = overworld;
    }

    void run() {
        ExtendedChunkPrimer p = new ExtendedChunkPrimer(ext);
        HeightRange range = p.range();
        int[] ys = { -256, -255, -1, 0, 15, 16, 255, 256, 318, 319 };
        for (int i = 0; i < ys.length; i++) {
            p.setBlock(3, ys[i], 5, Blocks.wool, i);
        }
        p.setBlock(3, -257, 5, Blocks.stone, 0);
        p.setBlock(3, 320, 5, Blocks.stone, 0);
        p.setBlock(7, -100, 7, Blocks.stone, 0);
        p.setBlock(7, -100, 7, Blocks.air, 0); // секция -7 снова опустошается
        p.setBlock(9, 100, 9, Blocks.air, 0); // воздух в отсутствующую секцию ничего не выделяет
        p.setBlock(1, 130, 1, Blocks.stone, 0);
        p.setBiome(3, 5, BiomeGenBase.desert);

        boolean primerReads = p.getBlock(3, -1, 5) == Blocks.wool && p.getMeta(3, -1, 5) == 2
            && p.getBlock(3, -257, 5) == Blocks.air;
        Chunk c = p.toChunk(ext, 2000, 2000);

        int wrong = 0;
        for (int i = 0; i < ys.length; i++) {
            if (c.getBlock(3, ys[i], 5) != Blocks.wool || c.getBlockMetadata(3, ys[i], 5) != i) {
                wrong++;
            }
        }
        report.check(
            "primer: blocks at y -256..319 (section boundaries) land in the chunk",
            wrong == 0 && primerReads,
            wrong + " wrong, primer reads " + primerReads);
        ExtendedBlockStorage[] s = c.getBlockStorageArray();
        boolean yBases = true;
        for (int i = 0; i < s.length; i++) {
            if (s[i] != null && s[i].getYLocation() != (range.minSection() + i) << 4) {
                yBases = false;
            }
        }
        report.check(
            "primer: sections carry their real base y; emptied and air-only sections stay absent",
            yBases && s.length == range.sectionCount()
                && s[range.sectionIndex(-100)] == null
                && s[range.sectionIndex(100)] == null,
            "sections " + s.length);
        report.check(
            "primer: column biome written, unset columns keep 'not set' (255)",
            (c.getBiomeArray()[5 << 4 | 3] & 255) == BiomeGenBase.desert.biomeID && (c.getBiomeArray()[0] & 255) == 255,
            c.getBiomeArray()[5 << 4 | 3] & 255);
        // Верхний ряд верхней секции generateSkylightMap не сканирует — проверяем столбец ниже
        report.check(
            "primer: height map and sky light (generateSkylightMap): stone at 130 -> 131, empty column minY, no sky light under wool",
            c.getHeightValue(1, 1) == 131 && c.getHeightValue(0, 0) == range.minY
                && c.getSavedLightValue(EnumSkyBlock.Sky, 1, 131, 1) == 15
                && c.getSavedLightValue(EnumSkyBlock.Sky, 3, 200, 5) == 0,
            c.getHeightValue(1, 1) + " / "
                + c.getHeightValue(0, 0)
                + " / "
                + c.getSavedLightValue(EnumSkyBlock.Sky, 1, 131, 1)
                + " / "
                + c.getSavedLightValue(EnumSkyBlock.Sky, 3, 200, 5));

        boolean reuse = false;
        try {
            p.setBlock(0, 0, 0, Blocks.stone, 0);
        } catch (IllegalStateException e) {
            reuse = true;
        }
        boolean mismatch = false;
        try {
            new ExtendedChunkPrimer(ext).toChunk(overworld, 2000, 2000);
        } catch (IllegalArgumentException e) {
            mismatch = true;
        }
        report.check(
            "primer: reuse after toChunk and a world of another range are refused",
            reuse && mismatch,
            reuse + " / " + mismatch);
    }
}
