package io.github.extendedheight.core;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** config/extendedheight.cfg: всё клиентское и только для расширенных миров */
public final class EhConfig {

    public static final String CATEGORY_VOID = "void";

    /** Частицы пустоты ниже minY + 8 вместо ванильного y 8 */
    public static volatile boolean voidParticlesFollowMinY = false;
    /** Доля ванильного количества частиц пустоты, 0..1 */
    public static volatile double voidParticleIntensity = 0.5D;
    /** Ванильное затемнение пустоты, отсчитанное от minY */
    public static volatile boolean voidFog = false;

    private EhConfig() {}

    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        c.setCategoryComment(
            CATEGORY_VOID,
            "Client-side effects near the bottom of the world. Apply to extended worlds only; vanilla worlds are unchanged.");
        voidParticlesFollowMinY = c.getBoolean(
            "voidParticlesFollowMinY",
            CATEGORY_VOID,
            false,
            "true: void particles appear below (lowest y of the world + 8). false: below y 8, as in vanilla, so the whole "
                + "part of an extended world below 0 has them.");
        voidParticleIntensity = c.getFloat(
            "voidParticleIntensity",
            CATEGORY_VOID,
            0.5F,
            0.0F,
            1.0F,
            "Amount of void particles relative to vanilla: 1 = vanilla, 0 = none.");
        voidFog = c.getBoolean(
            "voidFog",
            CATEGORY_VOID,
            false,
            "true: vanilla void darkening (fog and sky turn black towards the bottom, fog closes in near the bottom in "
                + "survival), measured from the lowest y of the world instead of y 0. false: no void darkening.");
        if (c.hasChanged()) {
            c.save();
        }
    }
}
