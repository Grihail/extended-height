package io.github.extendedheight.net;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Supplier;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S23PacketBlockChange;

import com.google.common.collect.BiMap;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.netty.buffer.Unpooled;

/** Расширенные пакеты (5.B): регистрация в PLAY, замена исходящих ванильных и счётчики самотеста */
public final class ExtPackets {

    public static final int S_CHUNK_DATA = 0x60;
    public static final int S_MAP_CHUNK_BULK = 0x61;
    public static final int S_MULTI_BLOCK_CHANGE = 0x62;
    public static final int S_BLOCK_CHANGE = 0x63;
    public static final int S_HEIGHT_TABLE = 0x66;
    public static final int C_DIGGING = 0x60;
    public static final int C_PLACEMENT = 0x61;

    /** Слоты счётчиков самотеста: создано/заменено на отправителе и обработано на получателе */
    public static final int CHUNK = 0, BULK = 1, MULTI = 2, BLOCK = 3, TABLE = 4, DIGGING = 5, PLACEMENT = 6;
    public static final String[] NAMES = { "ExtChunkData", "ExtMapChunkBulk", "ExtMultiBlockChange", "ExtBlockChange",
        "HeightTableSync", "ExtPlayerDigging", "ExtBlockPlacement" };
    public static final AtomicIntegerArray SENT = new AtomicIntegerArray(NAMES.length);
    public static final AtomicIntegerArray RECEIVED = new AtomicIntegerArray(NAMES.length);

    /** Диапазон текущего мира клиента; подменяется клиентом (на выделенном сервере ваниль) */
    public static volatile Supplier<HeightRange> clientRange = () -> HeightRange.VANILLA;

    /** Только самотест: сериализовать каждый расширенный пакет даже в памяти одиночной игры */
    public static volatile boolean forceWireRoundTrip;

    private ExtPackets() {}

    public static void register() {
        EnumConnectionState play = EnumConnectionState.PLAY;
        Map<Class<?>, EnumConnectionState> byClass = stateByClassMap();
        registerOne(play.func_150755_b(), byClass, S_CHUNK_DATA, SPacketExtChunkData.class, "SERVER->CLIENT");
        registerOne(play.func_150755_b(), byClass, S_MAP_CHUNK_BULK, SPacketExtMapChunkBulk.class, "SERVER->CLIENT");
        registerOne(
            play.func_150755_b(),
            byClass,
            S_MULTI_BLOCK_CHANGE,
            SPacketExtMultiBlockChange.class,
            "SERVER->CLIENT");
        registerOne(play.func_150755_b(), byClass, S_BLOCK_CHANGE, SPacketExtBlockChange.class, "SERVER->CLIENT");
        registerOne(play.func_150755_b(), byClass, S_HEIGHT_TABLE, SPacketHeightTable.class, "SERVER->CLIENT");
        registerOne(play.func_150753_a(), byClass, C_DIGGING, CPacketExtDigging.class, "CLIENT->SERVER");
        registerOne(play.func_150753_a(), byClass, C_PLACEMENT, CPacketExtPlacement.class, "CLIENT->SERVER");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void registerOne(BiMap ids, Map<Class<?>, EnumConnectionState> byClass, int id,
        Class<? extends Packet> type, String direction) {
        Object taken = ids.get(id);
        if (taken != null) {
            throw new IllegalStateException(
                String.format(
                    "ExtendedHeight: packet id 0x%02X (%s) is already registered to %s, cannot register %s",
                    id,
                    direction,
                    ((Class<?>) taken).getName(),
                    type.getSimpleName()));
        }
        if (ids.inverse()
            .containsKey(type)) {
            throw new IllegalStateException(
                "ExtendedHeight: " + type.getName()
                    + " is already registered with id "
                    + ids.inverse()
                        .get(type));
        }
        EnumConnectionState state = byClass.get(type);
        if (state != null) {
            throw new IllegalStateException(
                "ExtendedHeight: " + type.getName() + " is already assigned to protocol state " + state);
        }
        ids.put(id, type);
        byClass.put(type, EnumConnectionState.PLAY);
    }

    /** Приватная статическая карта «класс пакета → состояние» NetworkManager */
    @SuppressWarnings("unchecked")
    private static Map<Class<?>, EnumConnectionState> stateByClassMap() {
        Field found = null;
        for (Field f : EnumConnectionState.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                if (found != null) {
                    throw new IllegalStateException("ExtendedHeight: two static maps in EnumConnectionState");
                }
                found = f;
            }
        }
        if (found == null) {
            throw new IllegalStateException("ExtendedHeight: packet state map not found in EnumConnectionState");
        }
        try {
            found.setAccessible(true);
            return (Map<Class<?>, EnumConnectionState>) found.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- замена исходящих ----

    /** Меняет ванильные пакеты с неподходящим y на расширенные, только если мир отправителя расширенный */
    public static Packet onOutbound(INetHandler handler, Packet packet) {
        Class<?> type = packet.getClass();
        if (type == S23PacketBlockChange.class) {
            if (handler instanceof NetHandlerPlayServer) {
                S23PacketBlockChange p = (S23PacketBlockChange) packet;
                NetHandlerPlayServer h = (NetHandlerPlayServer) handler;
                int y = SPacketExtBlockChange.y(p);
                if ((y < 0 || y > 255) && h.playerEntity != null
                    && ExtendedHeight.isExtended(h.playerEntity.worldObj)) {
                    packet = SPacketExtBlockChange.copyOf(p);
                    SENT.incrementAndGet(BLOCK);
                }
            }
        } else if (type == C07PacketPlayerDigging.class) {
            C07PacketPlayerDigging p = (C07PacketPlayerDigging) packet;
            int y = p.func_149503_d();
            if ((y < 0 || y > 255) && !clientRange.get()
                .isVanilla()) {
                packet = CPacketExtDigging.copyOf(p);
                SENT.incrementAndGet(DIGGING);
            }
        } else if (type == C08PacketPlayerBlockPlacement.class) {
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) packet;
            int y = p.func_149571_d();
            // направление 255 — «использовать предмет» с позицией (-1, -1, -1): остаётся ванильным
            if (p.func_149568_f() != 255 && (y < 0 || y > 255)
                && !clientRange.get()
                    .isVanilla()) {
                packet = CPacketExtPlacement.copyOf(p);
                SENT.incrementAndGet(PLACEMENT);
            }
        }
        if (forceWireRoundTrip && packet instanceof IExtPacket) {
            packet = roundTrip(packet);
        }
        return packet;
    }

    /** Маркер расширенных пакетов (круговая проверка самотеста) */
    public interface IExtPacket {
    }

    /** Пишет пакет в буфер и читает в новый экземпляр; буфер должен быть прочитан ровно */
    public static Packet roundTrip(Packet packet) {
        try {
            PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
            packet.writePacketData(buf);
            Packet copy = packet.getClass()
                .newInstance();
            copy.readPacketData(buf);
            if (buf.readableBytes() != 0) {
                throw new IllegalStateException(buf.readableBytes() + " unread bytes");
            }
            buf.release();
            return copy;
        } catch (Exception e) {
            ExtendedHeightMod.LOG.error(
                "Wire round trip of {} failed",
                packet.getClass()
                    .getSimpleName(),
                e);
            throw new IllegalStateException(
                "Wire round trip failed for " + packet.getClass()
                    .getSimpleName(),
                e);
        }
    }
}
