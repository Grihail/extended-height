package io.github.extendedheight.net;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S23PacketBlockChange;

import io.github.extendedheight.mixins.early.net.S23Accessor;

/** 0x63, расширенный S23: y как int; создаётся только в {@link ExtPackets#onOutbound} */
public class SPacketExtBlockChange extends S23PacketBlockChange implements ExtPackets.IExtPacket {

    public SPacketExtBlockChange() {}

    static int y(S23PacketBlockChange p) {
        return ((S23Accessor) p).eh$getY();
    }

    public static SPacketExtBlockChange copyOf(S23PacketBlockChange p) {
        S23Accessor from = (S23Accessor) p;
        SPacketExtBlockChange copy = new SPacketExtBlockChange();
        S23Accessor to = (S23Accessor) copy;
        to.eh$setX(from.eh$getX());
        to.eh$setY(from.eh$getY());
        to.eh$setZ(from.eh$getZ());
        copy.field_148883_d = p.field_148883_d;
        copy.field_148884_e = p.field_148884_e;
        return copy;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        S23Accessor self = (S23Accessor) this;
        self.eh$setX(buf.readInt());
        self.eh$setY(buf.readInt());
        self.eh$setZ(buf.readInt());
        field_148883_d = Block.getBlockById(buf.readVarIntFromBuffer());
        field_148884_e = buf.readUnsignedByte();
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        S23Accessor self = (S23Accessor) this;
        buf.writeInt(self.eh$getX());
        buf.writeInt(self.eh$getY());
        buf.writeInt(self.eh$getZ());
        buf.writeVarIntToBuffer(Block.getIdFromBlock(field_148883_d));
        buf.writeByte(field_148884_e);
    }

    @Override
    public void processPacket(INetHandler handler) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.BLOCK);
        super.processPacket(handler);
    }
}
