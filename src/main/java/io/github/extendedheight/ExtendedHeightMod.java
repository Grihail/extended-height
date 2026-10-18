package io.github.extendedheight;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;

@Mod(modid = ExtendedHeightMod.MODID, name = "ExtendedHeight", version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public class ExtendedHeightMod {

    public static final String MODID = "extendedheight";
    public static final Logger LOG = LogManager.getLogger(MODID);
}
