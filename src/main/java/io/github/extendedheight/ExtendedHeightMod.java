package io.github.extendedheight;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import io.github.extendedheight.core.MixinPlugin;
import io.github.extendedheight.selftest.SelfTest;

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
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (SelfTest.isActive()) {
            SelfTest.serverStarted(MinecraftServer.getServer());
        }
    }
}
