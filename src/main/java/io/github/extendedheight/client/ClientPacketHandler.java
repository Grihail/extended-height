package io.github.extendedheight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.INetHandler;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.Chunk;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IClientHeightTable;
import io.github.extendedheight.net.ChunkCodec;
import io.github.extendedheight.net.ExtPackets;
import io.github.extendedheight.net.SPacketExtChunkData;
import io.github.extendedheight.net.SPacketExtMapChunkBulk;
import io.github.extendedheight.net.SPacketExtMultiBlockChange;
import io.github.extendedheight.net.SPacketHeightTable;

/** Клиентская обработка расширенных пакетов в главном потоке, по порядку с ванильными */
public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    public static void handleHeightTable(INetHandler handler, SPacketHeightTable packet) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.TABLE);
        ((IClientHeightTable) handler).eh$setHeightTable(packet.table());
    }

    /** Диапазон текущего клиентского мира или null (с ошибкой в логе), если пакет для другого диапазона */
    private static HeightRange rangeFor(WorldClient world, int minSection, String what) {
        if (world == null) {
            return null;
        }
        HeightRange range = ExtendedHeight.getRange(world);
        if (range.isVanilla() || range.minSection() != minSection) {
            ExtendedHeightMod.LOG.error(
                "{} for minSection {} received, but the client world range is {}; ignored",
                what,
                minSection,
                range);
            return null;
        }
        return range;
    }

    /** Порт NetHandlerPlayClient.handleChunkData с диапазоном мира вместо 0..256 */
    public static void handleChunkData(SPacketExtChunkData p) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.CHUNK);
        WorldClient world = Minecraft.getMinecraft().theWorld;
        HeightRange range = rangeFor(world, p.minSection, "Extended chunk data");
        if (range == null) {
            return;
        }
        if (p.full) {
            if (p.mask == 0) {
                world.doPreChunk(p.x, p.z, false);
                return;
            }
            world.doPreChunk(p.x, p.z, true);
        }
        Chunk chunk = world.getChunkFromChunkCoords(p.x, p.z);
        ClientChunkFill.fill(chunk, p.data, 0, p.mask, p.addMask, p.full, range);
        world.markBlockRangeForRenderUpdate(
            p.x << 4,
            range.minY,
            p.z << 4,
            (p.x << 4) + 15,
            range.maxYExclusive(),
            (p.z << 4) + 15);
        if (!p.full || !(world.provider instanceof WorldProviderSurface)) {
            chunk.resetRelightChecks();
        }
    }

    /** Порт NetHandlerPlayClient.handleMapChunkBulk */
    public static void handleMapChunkBulk(SPacketExtMapChunkBulk p) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.BULK);
        WorldClient world = Minecraft.getMinecraft().theWorld;
        HeightRange range = rangeFor(world, p.minSection, "Extended chunk bulk");
        if (range == null) {
            return;
        }
        int offset = 0;
        for (int i = 0; i < p.chunkCount(); i++) {
            int x = p.xs[i];
            int z = p.zs[i];
            world.doPreChunk(x, z, true);
            Chunk chunk = world.getChunkFromChunkCoords(x, z);
            ClientChunkFill.fill(chunk, p.data, offset, p.masks[i], p.addMasks[i], true, range);
            offset += ChunkCodec.rawLength(p.masks[i], p.addMasks[i], p.sky, true);
            world.markBlockRangeForRenderUpdate(
                x << 4,
                range.minY,
                z << 4,
                (x << 4) + 15,
                range.maxYExclusive(),
                (z << 4) + 15);
            if (!(world.provider instanceof WorldProviderSurface)) {
                chunk.resetRelightChecks();
            }
        }
    }

    /** Порт NetHandlerPlayClient.handleMultiBlockChange */
    public static void handleMultiBlockChange(SPacketExtMultiBlockChange p) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.MULTI);
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }
        int baseX = p.chunkX * 16;
        int baseZ = p.chunkZ * 16;
        for (int i = 0; i < p.xz.length; i++) {
            int x = baseX + (p.xz[i] >> 4 & 15);
            int z = baseZ + (p.xz[i] & 15);
            world.func_147492_c(x, p.ys[i], z, p.blocks[i], p.metas[i]);
        }
    }
}
