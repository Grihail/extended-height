package io.github.extendedheight.selftest;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.chunk.Chunk;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;

/** Проверочный инструмент: шлёт игрокам расширенных миров ванильные S21, как небрежный мод */
public final class VanillaS21Injector {

    public static final String PROPERTY = "extendedheight.injectVanillaS21";
    private static final int PERIOD = 60;
    private int ticks;

    public static void registerIfEnabled() {
        if (Boolean.getBoolean(PROPERTY)) {
            FMLCommonHandler.instance()
                .bus()
                .register(new VanillaS21Injector());
            ExtendedHeightMod.LOG.warn("Injecting vanilla S21 packets into extended worlds (verification aid)");
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++this.ticks % PERIOD != 0) {
            return;
        }
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            if (!ExtendedHeight.isExtended(player.worldObj)) {
                continue;
            }
            Chunk chunk = player.worldObj
                .getChunkFromChunkCoords(((int) Math.floor(player.posX)) >> 4, ((int) Math.floor(player.posZ)) >> 4);
            player.playerNetServerHandler.sendPacket(new S21PacketChunkData(chunk, true, 65535));
            player.playerNetServerHandler.sendPacket(new S21PacketChunkData(chunk, false, 1));
        }
    }
}
