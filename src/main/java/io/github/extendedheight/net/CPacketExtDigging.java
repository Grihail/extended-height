package io.github.extendedheight.net;

import java.io.IOException;

import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;

import io.github.extendedheight.mixins.early.net.C07Accessor;

/** 0x60, расширенный C07: y как int; только из расширенного мира для y вне 0..255 */
public class CPacketExtDigging extends C07PacketPlayerDigging implements ExtPackets.IExtPacket {

    public CPacketExtDigging() {}

    public static CPacketExtDigging copyOf(C07PacketPlayerDigging p) {
        CPacketExtDigging copy = new CPacketExtDigging();
        copy.set(p.func_149506_g(), p.func_149505_c(), p.func_149503_d(), p.func_149502_e(), p.func_149501_f());
        return copy;
    }

    private void set(int status, int x, int y, int z, int face) {
        C07Accessor self = (C07Accessor) this;
        self.eh$setStatus(status);
        self.eh$setX(x);
        self.eh$setY(y);
        self.eh$setZ(z);
        self.eh$setFace(face);
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        int status = buf.readUnsignedByte();
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        int face = buf.readUnsignedByte();
        set(status, x, y, z, face);
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeByte(func_149506_g());
        buf.writeInt(func_149505_c());
        buf.writeInt(func_149503_d());
        buf.writeInt(func_149502_e());
        buf.writeByte(func_149501_f());
    }

    @Override
    public void processPacket(INetHandler handler) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.DIGGING);
        super.processPacket(handler);
    }
}
