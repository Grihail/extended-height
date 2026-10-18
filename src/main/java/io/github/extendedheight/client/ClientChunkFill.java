package io.github.extendedheight.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import io.github.extendedheight.api.HeightRange;

/** Порт клиентского Chunk.fillChunk для расширенных пакетов: long-маски, бит i — storageArrays[i] */
public final class ClientChunkFill {

    private ClientChunkFill() {}

    @SuppressWarnings("unchecked")
    public static void fill(Chunk chunk, byte[] data, int k, long mask, long addMask, boolean full, HeightRange range) {
        for (Object o : chunk.chunkTileEntityMap.values()) {
            TileEntity te = (TileEntity) o;
            te.updateContainingBlockInfo();
            te.getBlockMetadata();
            te.getBlockType();
        }

        ExtendedBlockStorage[] s = chunk.getBlockStorageArray();
        boolean sky = !chunk.worldObj.provider.hasNoSky;
        int minSection = range.minSection();

        for (int l = 0; l < s.length; ++l) {
            if ((mask & 1L << l) != 0) {
                if (s[l] == null) {
                    s[l] = new ExtendedBlockStorage(l + minSection << 4, sky);
                }
                byte[] lsb = s[l].getBlockLSBArray();
                System.arraycopy(data, k, lsb, 0, lsb.length);
                k += lsb.length;
            } else if (full && s[l] != null) {
                s[l] = null;
            }
        }
        for (int l = 0; l < s.length; ++l) {
            if ((mask & 1L << l) != 0 && s[l] != null) {
                k = copy(data, k, s[l].getMetadataArray());
            }
        }
        for (int l = 0; l < s.length; ++l) {
            if ((mask & 1L << l) != 0 && s[l] != null) {
                k = copy(data, k, s[l].getBlocklightArray());
            }
        }
        if (sky) {
            for (int l = 0; l < s.length; ++l) {
                if ((mask & 1L << l) != 0 && s[l] != null) {
                    k = copy(data, k, s[l].getSkylightArray());
                }
            }
        }
        for (int l = 0; l < s.length; ++l) {
            if ((addMask & 1L << l) != 0) {
                if (s[l] == null) {
                    k += 2048;
                } else {
                    NibbleArray msb = s[l].getBlockMSBArray();
                    if (msb == null) {
                        msb = s[l].createBlockMSBArray();
                    }
                    k = copy(data, k, msb);
                }
            } else if (full && s[l] != null && s[l].getBlockMSBArray() != null) {
                s[l].clearMSBArray();
            }
        }
        if (full) {
            byte[] biomes = chunk.getBiomeArray();
            System.arraycopy(data, k, biomes, 0, biomes.length);
        }
        for (int l = 0; l < s.length; ++l) {
            if (s[l] != null && (mask & 1L << l) != 0) {
                s[l].removeInvalidBlocks();
            }
        }

        chunk.isLightPopulated = true;
        chunk.isTerrainPopulated = true;
        chunk.generateHeightMap();

        List<TileEntity> invalid = new ArrayList<>();
        for (Object o : chunk.chunkTileEntityMap.values()) {
            TileEntity te = (TileEntity) o;
            int x = te.xCoord & 15;
            int y = te.yCoord;
            int z = te.zCoord & 15;
            Block block = te.getBlockType();
            Block now = chunk.getBlock(x, y, z);
            int meta = chunk.getBlockMetadata(x, y, z);
            if ((block != now || te.blockMetadata != meta)
                && te.shouldRefresh(block, now, te.blockMetadata, meta, chunk.worldObj, x, y, z)) {
                invalid.add(te);
            }
            te.updateContainingBlockInfo();
        }
        for (TileEntity te : invalid) {
            te.invalidate();
        }
    }

    private static int copy(byte[] data, int k, NibbleArray nibbles) {
        System.arraycopy(data, k, nibbles.data, 0, nibbles.data.length);
        return k + nibbles.data.length;
    }
}
