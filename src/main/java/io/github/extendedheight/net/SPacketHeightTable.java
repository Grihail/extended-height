package io.github.extendedheight.net;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.client.ClientPacketHandler;
import io.github.extendedheight.core.IHeightTableHolder;
import io.github.extendedheight.core.RangeResolver;

/** 0x66: таблица «измерение → диапазон» сервера; перед каждым S01 и S07, если есть расширенные */
public class SPacketHeightTable extends Packet implements ExtPackets.IExtPacket {

    private Map<Integer, HeightRange> table = Collections.emptyMap();

    public SPacketHeightTable() {}

    public SPacketHeightTable(Map<Integer, HeightRange> table) {
        this.table = Collections.unmodifiableMap(new LinkedHashMap<>(table));
    }

    /** Таблица сервера или null, если расширенных измерений нет */
    public static SPacketHeightTable forServer() {
        World overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return null;
        }
        WorldInfo root = RangeResolver.rootInfo(overworld.getWorldInfo());
        Map<Integer, HeightRange> table = ((IHeightTableHolder) root).eh$getHeightTable();
        if (table.isEmpty()) {
            return null;
        }
        ExtPackets.SENT.incrementAndGet(ExtPackets.TABLE);
        return new SPacketHeightTable(table);
    }

    public Map<Integer, HeightRange> table() {
        return table;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        int n = buf.readVarIntFromBuffer();
        if (n < 0 || n > 4096) {
            throw new IOException("Bad height table size " + n);
        }
        Map<Integer, HeightRange> t = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int dim = buf.readInt();
            int minY = buf.readInt();
            int maxY = buf.readInt();
            String error = HeightRange.validate(minY, maxY);
            if (error != null) {
                throw new IOException(
                    "Bad height range " + minY + ".." + maxY + " for dimension " + dim + ": " + error);
            }
            t.put(dim, HeightRange.of(minY, maxY));
        }
        table = Collections.unmodifiableMap(t);
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeVarIntToBuffer(table.size());
        for (Map.Entry<Integer, HeightRange> e : table.entrySet()) {
            buf.writeInt(e.getKey());
            buf.writeInt(e.getValue().minY);
            buf.writeInt(e.getValue().maxY);
        }
    }

    @Override
    public void processPacket(INetHandler handler) {
        ClientPacketHandler.handleHeightTable(handler, this);
    }

    @Override
    public String serialize() {
        return "table=" + table;
    }
}
