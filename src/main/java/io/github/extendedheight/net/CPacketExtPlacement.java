package io.github.extendedheight.net;

import java.io.IOException;

import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import io.github.extendedheight.mixins.early.net.C08Accessor;

/** 0x61, расширенный C08: y как int; форма «использовать предмет» остаётся ванильной */
public class CPacketExtPlacement extends C08PacketPlayerBlockPlacement implements ExtPackets.IExtPacket {

    public CPacketExtPlacement() {}

    public static CPacketExtPlacement copyOf(C08PacketPlayerBlockPlacement p) {
        CPacketExtPlacement copy = new CPacketExtPlacement();
        C08Accessor self = (C08Accessor) copy;
        self.eh$setX(p.func_149576_c());
        self.eh$setY(p.func_149571_d());
        self.eh$setZ(p.func_149570_e());
        self.eh$setDirection(p.func_149568_f());
        self.eh$setStack(p.func_149574_g());
        self.eh$setHitX(p.func_149573_h());
        self.eh$setHitY(p.func_149569_i());
        self.eh$setHitZ(p.func_149575_j());
        return copy;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        C08Accessor self = (C08Accessor) this;
        self.eh$setX(buf.readInt());
        self.eh$setY(buf.readInt());
        self.eh$setZ(buf.readInt());
        self.eh$setDirection(buf.readUnsignedByte());
        self.eh$setStack(buf.readItemStackFromBuffer());
        self.eh$setHitX(buf.readUnsignedByte() / 16.0F);
        self.eh$setHitY(buf.readUnsignedByte() / 16.0F);
        self.eh$setHitZ(buf.readUnsignedByte() / 16.0F);
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeInt(func_149576_c());
        buf.writeInt(func_149571_d());
        buf.writeInt(func_149570_e());
        buf.writeByte(func_149568_f());
        buf.writeItemStackToBuffer(func_149574_g());
        buf.writeByte((int) (func_149573_h() * 16.0F));
        buf.writeByte((int) (func_149569_i() * 16.0F));
        buf.writeByte((int) (func_149575_j() * 16.0F));
    }

    @Override
    public void processPacket(INetHandler handler) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.PLACEMENT);
        super.processPacket(handler);
    }
}
