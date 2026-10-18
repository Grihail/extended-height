package io.github.extendedheight.net;

import java.io.IOException;

import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.chunk.Chunk;

import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.client.ClientPacketHandler;
import io.github.extendedheight.core.IExtendedHeightChunk;

/** 0x60, расширенный S21: чанк целиком или часть секций; long-маски, minSection для проверки */
public class SPacketExtChunkData extends Packet implements ExtPackets.IExtPacket {

    public int x;
    public int z;
    public boolean full;
    public boolean sky;
    public int minSection;
    public long mask;
    public long addMask;
    /** Несжатые данные */
    public byte[] data;
    private byte[] compressed;

    public SPacketExtChunkData() {}

    public SPacketExtChunkData(Chunk chunk, boolean full, long requestedMask) {
        HeightRange range = ((IExtendedHeightChunk) chunk).eh$getRange();
        this.x = chunk.xPosition;
        this.z = chunk.zPosition;
        this.full = full;
        this.sky = !chunk.worldObj.provider.hasNoSky;
        this.minSection = range.minSection();
        ChunkCodec.Extracted e = ChunkCodec.extract(chunk, full, requestedMask);
        this.mask = e.mask;
        this.addMask = e.addMask;
        this.data = e.data;
        ExtPackets.SENT.incrementAndGet(ExtPackets.CHUNK);
    }

    /** Один пакет могут писать для разных игроков из разных сетевых потоков */
    private synchronized byte[] compressed() {
        if (compressed == null) {
            compressed = ChunkCodec.deflate(data);
        }
        return compressed;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        x = buf.readInt();
        z = buf.readInt();
        full = buf.readBoolean();
        sky = buf.readBoolean();
        minSection = buf.readByte();
        mask = buf.readLong();
        addMask = buf.readLong();
        int rawLength = buf.readInt();
        int compressedLength = buf.readInt();
        ChunkCodec.checkLength(rawLength, 1);
        if (rawLength != ChunkCodec.rawLength(mask, addMask, sky, full) || compressedLength < 0
            || compressedLength > buf.readableBytes()) {
            throw new IOException("Bad extended chunk packet lengths " + rawLength + "/" + compressedLength);
        }
        byte[] c = new byte[compressedLength];
        buf.readBytes(c);
        data = ChunkCodec.inflate(c, rawLength);
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        byte[] c = compressed();
        buf.writeInt(x);
        buf.writeInt(z);
        buf.writeBoolean(full);
        buf.writeBoolean(sky);
        buf.writeByte(minSection);
        buf.writeLong(mask);
        buf.writeLong(addMask);
        buf.writeInt(data.length);
        buf.writeInt(c.length);
        buf.writeBytes(c);
    }

    @Override
    public void processPacket(INetHandler handler) {
        ClientPacketHandler.handleChunkData(this);
    }

    @Override
    public String serialize() {
        return String.format(
            "x=%d, z=%d, full=%b, minSection=%d, mask=%x, add=%x, size=%d",
            x,
            z,
            full,
            minSection,
            mask,
            addMask,
            data == null ? 0 : data.length);
    }
}
