package io.github.extendedheight.selftest;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/** Измерение только для самотеста: пустые чанки, без мобов, детерминировано */
public class SelfTestWorldProvider extends WorldProvider {

    @Override
    public String getDimensionName() {
        return "ExtendedHeightSelfTest" + dimensionId;
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        return new EmptyChunkProvider(worldObj);
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    public static class EmptyChunkProvider implements IChunkProvider {

        private final World world;

        EmptyChunkProvider(World world) {
            this.world = world;
        }

        @Override
        public boolean chunkExists(int x, int z) {
            return true;
        }

        @Override
        public Chunk provideChunk(int x, int z) {
            Chunk chunk = new Chunk(world, x, z);
            chunk.generateSkylightMap();
            return chunk;
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
            return "ExtendedHeightSelfTest";
        }

        @Override
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
