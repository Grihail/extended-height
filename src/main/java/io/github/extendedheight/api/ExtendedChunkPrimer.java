package io.github.extendedheight.api;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Сборщик одного чанка расширенного мира по реальным y; одноразовый, не потокобезопасный */
public final class ExtendedChunkPrimer {

    private final HeightRange range;
    private final boolean sky;
    private final ExtendedBlockStorage[] sections;
    private final byte[] biomes = new byte[256];
    private boolean consumed;

    /** Праймер для чанков этого мира (его диапазон; небесный свет, если у мира есть небо) */
    public ExtendedChunkPrimer(World world) {
        this(ExtendedHeight.getRange(world), !world.provider.hasNoSky);
    }

    public ExtendedChunkPrimer(HeightRange range, boolean sky) {
        this.range = range;
        this.sky = sky;
        this.sections = new ExtendedBlockStorage[range.sectionCount()];
        Arrays.fill(biomes, (byte) -1); // ваниль: «ещё не задан», его заполнит world chunk manager
    }

    public HeightRange range() {
        return range;
    }

    /** Ставит блок; y вне диапазона игнорируется, воздух без секции ничего не выделяет */
    public void setBlock(int x, int y, int z, Block block, int meta) {
        check();
        if (!range.contains(y)) {
            return;
        }
        int index = range.sectionIndex(y);
        ExtendedBlockStorage ebs = sections[index];
        if (ebs == null) {
            if (block == Blocks.air) {
                return;
            }
            ebs = sections[index] = new ExtendedBlockStorage(y >> 4 << 4, sky);
        }
        ebs.func_150818_a(x & 15, y & 15, z & 15, block);
        ebs.setExtBlockMetadata(x & 15, y & 15, z & 15, meta & 15);
    }

    public void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block, 0);
    }

    /** Воздух вне диапазона и там, где ничего не ставили */
    public Block getBlock(int x, int y, int z) {
        if (!range.contains(y)) {
            return Blocks.air;
        }
        ExtendedBlockStorage ebs = sections[range.sectionIndex(y)];
        return ebs == null ? Blocks.air : ebs.getBlockByExtId(x & 15, y & 15, z & 15);
    }

    public int getMeta(int x, int y, int z) {
        if (!range.contains(y)) {
            return 0;
        }
        ExtendedBlockStorage ebs = sections[range.sectionIndex(y)];
        return ebs == null ? 0 : ebs.getExtBlockMetadata(x & 15, y & 15, z & 15);
    }

    /** Биом столбца (x, z) чанка: ванильный массив Biomes */
    public void setBiome(int x, int z, BiomeGenBase biome) {
        check();
        biomes[(z & 15) << 4 | (x & 15)] = (byte) biome.biomeID;
    }

    /** Собирает чанк: секции, биомы, затем generateSkylightMap(); диапазон мира должен совпадать */
    public Chunk toChunk(World world, int chunkX, int chunkZ) {
        check();
        HeightRange worldRange = ExtendedHeight.getRange(world);
        if (!worldRange.equals(range)) {
            throw new IllegalArgumentException(
                "Primer range " + range + " does not match the world range " + worldRange);
        }
        consumed = true;
        Chunk chunk = new Chunk(world, chunkX, chunkZ);
        ExtendedBlockStorage[] target = chunk.getBlockStorageArray();
        for (int i = 0; i < sections.length; i++) {
            ExtendedBlockStorage ebs = sections[i];
            // Опустевшая секция остаётся null, как в ванильных чанках
            target[i] = ebs == null || ebs.isEmpty() ? null : ebs;
        }
        System.arraycopy(biomes, 0, chunk.getBiomeArray(), 0, biomes.length);
        chunk.generateSkylightMap();
        return chunk;
    }

    private void check() {
        if (consumed) {
            throw new IllegalStateException("This primer has already built its chunk");
        }
    }
}
