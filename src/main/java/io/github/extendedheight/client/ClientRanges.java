package io.github.extendedheight.client;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.world.World;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IClientHeightTable;
import io.github.extendedheight.mixins.early.client.WorldClientAccessor;
import io.github.extendedheight.net.ExtPackets;

/** Клиентская часть синхронизации диапазонов (5.12) */
public final class ClientRanges {

    private ClientRanges() {}

    public static void init() {
        ExtPackets.clientRange = () -> {
            WorldClient world = Minecraft.getMinecraft().theWorld;
            return world == null ? HeightRange.VANILLA : ExtendedHeight.getRange(world);
        };
    }

    /** Диапазон создаваемого клиентского мира из таблицы сервера, пришедшей перед S01/S07; иначе ванильный */
    public static HeightRange resolve(World world) {
        if (!(world instanceof WorldClient)) {
            return HeightRange.VANILLA;
        }
        NetHandlerPlayClient handler = ((WorldClientAccessor) world).eh$getSendQueue();
        if (handler == null) {
            return HeightRange.VANILLA;
        }
        Map<Integer, HeightRange> table = ((IClientHeightTable) handler).eh$getHeightTable();
        HeightRange range = table.get(world.provider.dimensionId);
        return range == null ? HeightRange.VANILLA : range;
    }
}
