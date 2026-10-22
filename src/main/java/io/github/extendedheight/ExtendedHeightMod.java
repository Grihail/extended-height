package io.github.extendedheight;

import java.util.Map;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import io.github.extendedheight.client.ClientRanges;
import io.github.extendedheight.core.EhConfig;
import io.github.extendedheight.core.MixinPlugin;
import io.github.extendedheight.net.ExtPackets;
import io.github.extendedheight.selftest.SelfTest;
import io.github.extendedheight.selftest.VanillaS21Injector;
import io.github.extendedheight.world.WorldTypeExtendedTest;

@Mod(
    modid = ExtendedHeightMod.MODID,
    name = "ExtendedHeight",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class ExtendedHeightMod {

    public static final String MODID = "extendedheight";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (SelfTest.isActive()) {
            LOG.warn("Self-test mode, phase {} (mixins disabled: {})", SelfTest.phase(), MixinPlugin.mixinsDisabled());
            SelfTest.preInit();
        }
        EhConfig.load(event.getSuggestedConfigurationFile());
        if (!MixinPlugin.mixinsDisabled()) {
            ExtPackets.register();
            WorldTypeExtendedTest.register();
            if (event.getSide()
                .isClient()) {
                ClientRanges.init();
            }
        }
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        if (SelfTest.isActive() && SelfTest.isClientPhase()
            && FMLCommonHandler.instance()
                .getSide()
                .isClient()) {
            SelfTest.clientAudit();
        }
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        VanillaS21Injector.registerIfEnabled();
        // В клиентской фазе это встроенный сервер тестового мира: тест ведёт клиентская часть
        if (SelfTest.isActive() && !SelfTest.isClientPhase()) {
            SelfTest.serverStarted(MinecraftServer.getServer());
        }
    }

    /** Рукопожатие FML (5.13): серверу с модом нужен клиент той же версии; клиент с модом заходит и на ванильные */
    @NetworkCheckHandler
    public boolean checkRemote(Map<String, String> remoteMods, Side remoteSide) {
        return rejectionReason(remoteMods, remoteSide) == null;
    }

    /** null, если удалённая сторона принята, иначе текст для отключённого клиента */
    public static String rejectionReason(Map<String, String> remoteMods, Side remoteSide) {
        String version = remoteMods.get(MODID);
        if (remoteSide == Side.SERVER) {
            return version == null || Tags.VERSION.equals(version) ? null
                : "The server has ExtendedHeight " + version + ", this client has " + Tags.VERSION;
        }
        if (version == null) {
            return "This server requires the mod ExtendedHeight " + Tags.VERSION
                + " on the client (it changes the world height). Install it to join.";
        }
        return Tags.VERSION.equals(version) ? null
            : "This server requires ExtendedHeight " + Tags.VERSION + ", the client has " + version + ".";
    }
}
