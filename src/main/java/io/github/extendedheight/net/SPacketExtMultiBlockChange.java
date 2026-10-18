package io.github.extendedheight.net;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.chunk.Chunk;

import io.github.extendedheight.client.ClientPacketHandler;

/** 0x62, расширенный S22: изменения блоков чанка с полным y */
public class SPacketExtMultiBlockChange extends Packet implements ExtPackets.IExtPacket {

    public int chunkX;
    public int chunkZ;
    /** x << 4 | z внутри чанка */
    public byte[] xz;
    public int[] ys;
    public Block[] blocks;
    public int[] metas;

    public SPacketExtMultiBlockChange() {}

    /** @param positions упакованы {@link #pack}, используются первые count */
    public SPacketExtMultiBlockChange(Chunk chunk, int[] positions, int count, int minY) {
        chunkX = chunk.xPosition;
        chunkZ = chunk.zPosition;
        xz = new byte[count];
        ys = new int[count];
        blocks = new Block[count];
        metas = new int[count];
        for (int i = 0; i < count; i++) {
            int x = unpackX(positions[i]);
            int z = unpackZ(positions[i]);
            int y = unpackY(positions[i], minY);
            xz[i] = (byte) (x << 4 | z);
            ys[i] = y;
            blocks[i] = chunk.getBlock(x, y, z);
            metas[i] = chunk.getBlockMetadata(x, y, z);
        }
        ExtPackets.SENT.incrementAndGet(ExtPackets.MULTI);
    }

    /** Позиция в чанке: x и z по 4 бита, y - minY 16 бит */
    public static int pack(int x, int y, int z, int minY) {
        return (x & 15) << 20 | (z & 15) << 16 | (y - minY) & 0xFFFF;
    }

    public static int unpackX(int packed) {
        return packed >> 20 & 15;
    }

    public static int unpackZ(int packed) {
        return packed >> 16 & 15;
    }

    public static int unpackY(int packed, int minY) {
        return (packed & 0xFFFF) + minY;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        chunkX = buf.readInt();
        chunkZ = buf.readInt();
        int n = buf.readVarIntFromBuffer();
        if (n < 0 || n > 16 * 16 * 1024) {
            throw new IOException("Bad multi block change size " + n);
        }
        xz = new byte[n];
        ys = new int[n];
        blocks = new Block[n];
        metas = new int[n];
        for (int i = 0; i < n; i++) {
            xz[i] = buf.readByte();
            ys[i] = buf.readShort();
            blocks[i] = Block.getBlockById(buf.readVarIntFromBuffer());
            metas[i] = buf.readUnsignedByte();
        }
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeVarIntToBuffer(xz.length);
        for (int i = 0; i < xz.length; i++) {
            buf.writeByte(xz[i]);
            buf.writeShort(ys[i]);
            buf.writeVarIntToBuffer(Block.getIdFromBlock(blocks[i]));
            buf.writeByte(metas[i]);
        }
    }

    @Override
    public void processPacket(INetHandler handler) {
        ClientPacketHandler.handleMultiBlockChange(this);
    }

    @Override
    public String serialize() {
        return String.format("x=%d, z=%d, count=%d", chunkX, chunkZ, xz.length);
    }
}
