package io.github.extendedheight.net;

import java.io.IOException;

import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S28PacketEffect;

import io.github.extendedheight.mixins.early.net.S28Accessor;

/** 0x64, расширенный S28 (звуки и частицы playAuxSFX): y как int вместо байта */
public class SPacketExtEffect extends S28PacketEffect implements ExtPackets.IExtPacket {

    /** Самотест: y последнего расширенного эффекта на клиенте */
    public static volatile int lastReceivedY = Integer.MIN_VALUE;

    public SPacketExtEffect() {}

    static int y(S28PacketEffect p) {
        return ((S28Accessor) p).eh$getY();
    }

    public static SPacketExtEffect copyOf(S28PacketEffect p) {
        S28Accessor from = (S28Accessor) p;
        SPacketExtEffect copy = new SPacketExtEffect();
        copy.set(
            from.eh$getType(),
            from.eh$getX(),
            from.eh$getY(),
            from.eh$getZ(),
            from.eh$getData(),
            from.eh$getBroadcast());
        return copy;
    }

    private void set(int type, int x, int y, int z, int data, boolean broadcast) {
        S28Accessor self = (S28Accessor) this;
        self.eh$setType(type);
        self.eh$setX(x);
        self.eh$setY(y);
        self.eh$setZ(z);
        self.eh$setData(data);
        self.eh$setBroadcast(broadcast);
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        int type = buf.readInt();
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        int data = buf.readInt();
        boolean broadcast = buf.readBoolean();
        set(type, x, y, z, data, broadcast);
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        S28Accessor self = (S28Accessor) this;
        buf.writeInt(self.eh$getType());
        buf.writeInt(self.eh$getX());
        buf.writeInt(self.eh$getY());
        buf.writeInt(self.eh$getZ());
        buf.writeInt(self.eh$getData());
        buf.writeBoolean(self.eh$getBroadcast());
    }

    @Override
    public void processPacket(INetHandler handler) {
        ExtPackets.RECEIVED.incrementAndGet(ExtPackets.EFFECT);
        lastReceivedY = ((S28Accessor) this).eh$getY();
        super.processPacket(handler);
    }
}
