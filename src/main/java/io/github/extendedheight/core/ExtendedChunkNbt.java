package io.github.extendedheight.core;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.HeightRange;

/** Чтение NBT чанка расширенного мира: слот на каждую секцию диапазона, секции вне диапазона пропускаются */
public final class ExtendedChunkNbt {

    private ExtendedChunkNbt() {}

    public static Chunk read(World world, NBTTagCompound level, HeightRange range) {
        int x = level.getInteger("xPos");
        int z = level.getInteger("zPos");
        Chunk chunk = new Chunk(world, x, z);
        chunk.heightMap = level.getIntArray("HeightMap");
        chunk.isTerrainPopulated = level.getBoolean("TerrainPopulated");
        chunk.isLightPopulated = level.getBoolean("LightPopulated");
        chunk.inhabitedTime = level.getLong("InhabitedTime");
        NBTTagList sections = level.getTagList("Sections", 10);
        ExtendedBlockStorage[] storage = new ExtendedBlockStorage[range.sectionCount()];
        boolean hasSky = !world.provider.hasNoSky;

        for (int i = 0; i < sections.tagCount(); ++i) {
            NBTTagCompound section = sections.getCompoundTagAt(i);
            int sectionY = section.getByte("Y");
            int index = sectionY - range.minSection();
            if (index < 0 || index >= storage.length) {
                ExtendedHeightMod.LOG.error(
                    "Chunk {},{} in dimension {}: section Y={} is outside the world range {}, skipped",
                    x,
                    z,
                    world.provider.dimensionId,
                    sectionY,
                    range);
                continue;
            }
            ExtendedBlockStorage ebs = new ExtendedBlockStorage(sectionY << 4, hasSky);
            ebs.setBlockLSBArray(section.getByteArray("Blocks"));
            if (section.hasKey("Add", 7)) {
                ebs.setBlockMSBArray(new NibbleArray(section.getByteArray("Add"), 4));
            }
            ebs.setBlockMetadataArray(new NibbleArray(section.getByteArray("Data"), 4));
            ebs.setBlocklightArray(new NibbleArray(section.getByteArray("BlockLight"), 4));
            if (hasSky) {
                ebs.setSkylightArray(new NibbleArray(section.getByteArray("SkyLight"), 4));
            }
            ebs.removeInvalidBlocks();
            storage[index] = ebs;
        }

        chunk.setStorageArrays(storage);
        if (level.hasKey("Biomes", 7)) {
            chunk.setBiomeArray(level.getByteArray("Biomes"));
        }
        return chunk;
    }
}
