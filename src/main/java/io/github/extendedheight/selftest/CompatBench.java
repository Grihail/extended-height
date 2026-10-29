package io.github.extendedheight.selftest;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;

/** Фаза compat: стенд совместимости — мир Default, игрок в воздухе, скриншоты через waitSeconds, выход */
public final class CompatBench {

    private static final int SETTLE_TICKS = 100;

    private final SelfTest report;
    private final int waitTicks;
    private final int shots;
    private final long seed;
    private final ConcurrentLinkedQueue<Runnable> serverTasks = new ConcurrentLinkedQueue<>();
    private int step;
    private int stepTicks;
    private int ticks;
    private int shot;
    private boolean done;

    private CompatBench(SelfTest report, int waitSeconds, int shots, long seed) {
        this.report = report;
        this.seed = seed;
        this.waitTicks = waitSeconds * 20;
        this.shots = shots;
    }

    static void start(SelfTest report, int waitSeconds, int shots, long seed) {
        Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = false;
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            report.info("mod " + mod.getModId() + " " + mod.getVersion());
        }
        FMLCommonHandler.instance()
            .bus()
            .register(new CompatBench(report, waitSeconds, shots, seed));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        Runnable task;
        while (event.phase == TickEvent.Phase.END && (task = serverTasks.poll()) != null) {
            task.run();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        stepTicks++;
        try {
            if (runStep(mc)) {
                step++;
                stepTicks = 0;
            }
        } catch (Throwable t) {
            ExtendedHeightMod.LOG.error("Compat bench crashed in step {}", step, t);
            report.check("compat step " + step + " without exception", false, t);
            finish(mc);
        }
    }

    private boolean runStep(Minecraft mc) {
        switch (step) {
            case 0:
                if (mc.currentScreen == null || ticks < 20) {
                    return false;
                }
                report.info("opening a Default singleplayer world, seed " + seed);
                mc.launchIntegratedServer(
                    "eh-compat",
                    "eh-compat",
                    new WorldSettings(seed, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT));
                return true;
            case 1:
                if (mc.theWorld == null || mc.thePlayer == null || stepTicks < SETTLE_TICKS) {
                    return false;
                }
                report.info("client world range " + ExtendedHeight.getRange(mc.theWorld));
                report.info("render distance " + mc.gameSettings.renderDistanceChunks);
                // Игрок над рельефом, взгляд к горизонту
                serverTasks.add(() -> {
                    EntityPlayerMP p = (EntityPlayerMP) mc.getIntegratedServer()
                        .getConfigurationManager().playerEntityList.get(0);
                    p.capabilities.isFlying = true;
                    p.sendPlayerAbilities();
                    p.playerNetServerHandler.setPlayerLocation(p.posX, 160, p.posZ, 0F, 15F);
                });
                return true;
            default:
                if (stepTicks < waitTicks / shots) {
                    return false;
                }
                shot++;
                mc.thePlayer.rotationPitch = 15F;
                ScreenShotHelper.saveScreenshot(
                    mc.mcDataDir,
                    "compat-" + shot + ".png",
                    mc.displayWidth,
                    mc.displayHeight,
                    mc.getFramebuffer());
                report.info(
                    "screenshot compat-" + shot + ".png at t=" + ticks / 20
                        + " s, player y "
                        + (int) mc.thePlayer.posY);
                if (shot >= shots) {
                    finish(mc);
                }
                return true;
        }
    }

    private void finish(Minecraft mc) {
        done = true;
        report.check("compat bench reached the end", shot >= shots, shot);
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
        }
        report.writeReport();
        mc.shutdown();
    }
}
