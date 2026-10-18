package io.github.extendedheight.net;

import java.io.IOException;
import java.util.List;

import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.world.chunk.Chunk;

import io.github.extendedheight.client.ClientPacketHandler;
import io.github.extendedheight.core.IExtendedHeightChunk;

/** 0x61, расширенный S26: несколько полных чанков; лимит — {@link ChunkCodec#maxChunksPerBulk} */
public class SPacketExtMapChunkBulk extends S26PacketMapChunkBulk implements ExtPackets.IExtPacket {

    public int[] xs;
    public int[] zs;
    public long[] masks;
    public long[] addMasks;
    /** Несжатые данные всех чанков подряд */
    public byte[] data;
    public boolean sky;
    public int minSection;
    private byte[] compressed;

    public SPacketExtMapChunkBulk() {}

    public SPacketExtMapChunkBulk(List<Chunk> chunks) {
        int n = chunks.size();
        xs = new int[n];
        zs = new int[n];
        masks = new long[n];
        addMasks = new long[n];
        byte[][] parts = new byte[n][];
        int total = 0;
        for (int i = 0; i < n; i++) {
            Chunk chunk = chunks.get(i);
            ChunkCodec.Extracted e = ChunkCodec.extract(chunk, true, -1L);
            xs[i] = chunk.xPosition;
            zs[i] = chunk.zPosition;
            masks[i] = e.mask;
            addMasks[i] = e.addMask;
            parts[i] = e.data;
            total += e.data.length;
            if (i == 0) {
                sky = !chunk.worldObj.provider.hasNoSky;
                minSection = ((IExtendedHeightChunk) chunk).eh$getRange()
                    .minSection();
            }
        }
        data = new byte[total];
        int p = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, data, p, part.length);
            p += part.length;
        }
        ExtPackets.SENT.incrementAndGet(ExtPackets.BULK);
    }

    public int chunkCount() {
        return xs.length;
    }

    private synchronized byte[] compressed() {
        if (compressed == null) {
            compressed = ChunkCodec.deflate(data);
        }
        return compressed;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        int n = buf.readShort();
        int compressedLength = buf.readInt();
        int rawLength = buf.readInt();
        sky = buf.readBoolean();
        minSection = buf.readByte();
        if (n < 0 || n > 64) {
            throw new IOException("Bad extended chunk bulk size " + n);
        }
        ChunkCodec.checkLength(rawLength, n);
        xs = new int[n];
        zs = new int[n];
        masks = new long[n];
        addMasks = new long[n];
        int expected = 0;
        for (int i = 0; i < n; i++) {
            xs[i] = buf.readInt();
            zs[i] = buf.readInt();
            masks[i] = buf.readLong();
            addMasks[i] = buf.readLong();
            expected += ChunkCodec.rawLength(masks[i], addMasks[i], sky, true);
        }
        if (expected != rawLength || compressedLength < 0 || compressedLength > buf.readableBytes()) {
            throw new IOException("Bad extended chunk bulk lengths " + rawLength + "/" + compressedLength);
        }
        byte[] c = new byte[compressedLength];
        buf.readBytes(c);
        data = ChunkCodec.inflate(c, rawLength);
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        byte[] c = compressed();
        buf.writeShort(xs.length);
        buf.writeInt(c.length);
        buf.writeInt(data.length);
        buf.writeBoolean(sky);
        buf.writeByte(minSection);
        for (int i = 0; i < xs.length; i++) {
            buf.writeInt(xs[i]);
            buf.writeInt(zs[i]);
            buf.writeLong(masks[i]);
            buf.writeLong(addMasks[i]);
        }
        buf.writeBytes(c);
    }

    @Override
    public void processPacket(INetHandler handler) {
        ClientPacketHandler.handleMapChunkBulk(this);
    }

    @Override
    public String serialize() {
        return String.format("chunks=%d, minSection=%d, size=%d", xs.length, minSection, data.length);
    }
}
