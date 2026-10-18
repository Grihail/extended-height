package io.github.extendedheight.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.extendedheight.api.ExtendedHeight;

/** Клиентская часть самотеста client-audit: открыть новый мир, проверить, выйти и закрыть игру */
public final class ClientSelfTest {

    private static final int WORLD_TIMEOUT_TICKS = 20 * 120;
    private static final int TICKS_IN_WORLD = 40;

    private final SelfTest report;
    private int ticks;
    private int ticksInWorld;
    private boolean launched;
    private boolean done;

    private ClientSelfTest(SelfTest report) {
        this.report = report;
    }

    /** Вызывается по окончании загрузки, после записи аудита миксинов в report */
    static void start(SelfTest report) {
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientSelfTest(report));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        if (!launched) {
            if (mc.currentScreen != null && ticks > 20) {
                launched = true;
                report.info("opening a singleplayer world");
                WorldSettings settings = new WorldSettings(
                    20261018L,
                    WorldSettings.GameType.CREATIVE,
                    false,
                    false,
                    WorldType.FLAT);
                mc.launchIntegratedServer("eh-selftest-" + System.currentTimeMillis(), "eh-selftest", settings);
            }
            return;
        }
        if (mc.theWorld != null && mc.thePlayer != null) {
            if (++ticksInWorld == TICKS_IN_WORLD) {
                report.check("singleplayer world loaded, player in the world", true, null);
                report.check(
                    "client world is vanilla (no extended range requested)",
                    !ExtendedHeight.isExtended(mc.theWorld),
                    ExtendedHeight.getRange(mc.theWorld));
                report.check(
                    "integrated server overworld is vanilla",
                    mc.getIntegratedServer() != null && !ExtendedHeight.isExtended(
                        mc.getIntegratedServer()
                            .worldServerForDimension(0)),
                    null);
                finish(mc);
            }
        } else if (ticks > WORLD_TIMEOUT_TICKS) {
            report.check("singleplayer world loaded within " + WORLD_TIMEOUT_TICKS / 20 + " s", false, "timeout");
            finish(mc);
        }
    }

    private void finish(Minecraft mc) {
        done = true;
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }
        report.writeReport();
        mc.shutdown();
    }
}
