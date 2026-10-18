package io.github.extendedheight.world;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import io.github.extendedheight.api.ExtendedChunkPrimer;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.api.IBiomeAtProvider;
import io.github.extendedheight.api.IVoidEffectsProvider;
import io.github.extendedheight.api.VoidEffects;

/** Тип мира для пробы расширенной высоты: -256..319, равнины, без мобов; шерсть по секциям, столб-маркер */
public class WorldTypeExtendedTest extends WorldType implements IVoidEffectsProvider, IBiomeAtProvider {

    public static final String NAME = "extendedtest";
    public static final int MIN_Y = -256;
    public static final int MAX_Y = 319;
    public static final int TOP_Y = 63;
    public static final int BEDROCK_Y = -64;

    private static WorldTypeExtendedTest instance;

    /** Только самотест: эффекты пустоты типа мира; null — встроенные */
    public static volatile VoidEffects testVoidEffects;

    /** Только самотест: биомы по высоте — болото ниже 0, пустыня выше; иначе биомы столбца */
    public static volatile boolean testBiomesByHeight;

    private WorldTypeExtendedTest() {
        super(NAME);
    }

    /** Создаёт тип мира и запрашивает диапазон; вызывать один раз в pre-init */
    public static void register() {
        if (instance == null) {
            instance = new WorldTypeExtendedTest();
            ExtendedHeight.register(instance, MIN_Y, MAX_Y);
        }
    }

    public static WorldTypeExtendedTest instance() {
        return instance;
    }

    @Override
    public VoidEffects getVoidEffects(World world) {
        return testVoidEffects;
    }

    @Override
    public BiomeGenBase getBiome(World world, int x, int y, int z, BiomeGenBase columnBiome) {
        if (!testBiomesByHeight) {
            return null;
        }
        return y < 0 ? BiomeGenBase.swampland : BiomeGenBase.desert;
    }

    @Override
    public WorldChunkManager getChunkManager(World world) {
        return new WorldChunkManagerHell(BiomeGenBase.plains, 0.5F);
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        return new Generator(world);
    }

    /** Блок и метаданные земли на y (без столба); выше {@link #TOP_Y} — воздух */
    public static Block groundBlock(int y) {
        if (y > TOP_Y) {
            return Blocks.air;
        }
        if (y == TOP_Y) {
            return Blocks.grass;
        }
        return y == BEDROCK_Y ? Blocks.bedrock : Blocks.wool;
    }

    public static int groundMeta(int y) {
        return groundBlock(y) == Blocks.wool ? Math.floorMod(y >> 4, 16) : 0;
    }

    /** Столб-маркер в x 0, z 0 над землёй */
    public static Block columnBlock(int y) {
        return (y & 15) == 0 ? Blocks.wool : Blocks.glass;
    }

    public static int columnMeta(int y) {
        return (y & 15) == 0 ? Math.floorMod(y >> 4, 16) : 0;
    }

    public static class Generator implements IChunkProvider {

        private final World world;

        Generator(World world) {
            this.world = world;
        }

        @Override
        public Chunk provideChunk(int cx, int cz) {
            // Собирается публичным ExtendedChunkPrimer, так что проверки раскладки его покрывают
            ExtendedChunkPrimer primer = new ExtendedChunkPrimer(world);
            HeightRange range = primer.range();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = range.minY; y <= TOP_Y; y++) {
                        primer.setBlock(x, y, z, groundBlock(y), groundMeta(y));
                    }
                    primer.setBiome(x, z, BiomeGenBase.plains);
                }
            }
            if (cx == 0 && cz == 0) {
                for (int y = TOP_Y + 1; y <= range.maxY; y++) {
                    primer.setBlock(0, y, 0, columnBlock(y), columnMeta(y));
                }
            }
            return primer.toChunk(world, cx, cz);
        }

        @Override
        public boolean chunkExists(int x, int z) {
            return true;
        }

        @Override
        public Chunk loadChunk(int x, int z) {
            return provideChunk(x, z);
        }

        @Override
        public void populate(IChunkProvider provider, int x, int z) {}

        @Override
        public boolean saveChunks(boolean all, IProgressUpdate progress) {
            return true;
        }

        @Override
        public boolean unloadQueuedChunks() {
            return false;
        }

        @Override
        public boolean canSave() {
            return true;
        }

        @Override
        public String makeString() {
            return "ExtendedHeightTest";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
            return Collections.emptyList();
        }

        @Override
        public ChunkPosition func_147416_a(World world, String structure, int x, int y, int z) {
            return null;
        }

        @Override
        public int getLoadedChunkCount() {
            return 0;
        }

        @Override
        public void recreateStructures(int x, int z) {}

        @Override
        public void saveExtraData() {}
    }
}
