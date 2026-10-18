package io.github.extendedheight.core;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import io.github.extendedheight.ExtendedHeightMod;

/** config/extendedheight.cfg: всё клиентское и только для расширенных миров; мир может задать свои эффекты */
public final class EhConfig {

    public static final String CATEGORY_VOID = "void";
    /** Версия формата конфига; 2: voidFog auto/off/on, voidParticleIntensity — множитель */
    public static final int CONFIG_VERSION = 2;

    public enum FogSetting {
        /** Решает мир (без своего выбора — тумана нет) */
        AUTO,
        /** Никогда */
        OFF,
        /** Для миров без своего выбора: ванильный туман пустоты от minY; мир не переопределяет */
        ON
    }

    /** Миры без своего выбора: частицы пустоты ниже minY + 8 вместо ванильного y 8 */
    public static volatile boolean voidParticlesFollowMinY = false;
    /** Множитель количества частиц мира, 0..1 */
    public static volatile double voidParticleIntensity = 1.0D;
    public static volatile FogSetting voidFog = FogSetting.AUTO;

    private EhConfig() {}

    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        c.setCategoryComment(
            CATEGORY_VOID,
            "Client-side effects near the bottom of extended worlds (vanilla worlds are unchanged). A world can choose "
                + "its own effects; these options can only weaken or remove them.");
        int version = c.get(Configuration.CATEGORY_GENERAL, "configVersion", 1, "Do not change.")
            .getInt();
        boolean migrate = version < CONFIG_VERSION && c.hasCategory(CATEGORY_VOID);

        voidParticlesFollowMinY = c.getBoolean(
            "voidParticlesFollowMinY",
            CATEGORY_VOID,
            false,
            "For worlds without their own void effects: true - void particles appear below (lowest y of the world + 8); "
                + "false - below y 8, as in vanilla, so the whole part of the world below 0 has them.");

        Property intensity = c.get(
            CATEGORY_VOID,
            "voidParticleIntensity",
            1.0D,
            "Multiplier of the amount of void particles the world shows: 1 = as the world intends, 0 = none. "
                + "(Worlds without their own void effects show half of the vanilla amount.)",
            0.0D,
            1.0D);
        if (migrate) {
            // Версия 1: доля от ванили, 0.5 по умолчанию — теперь встроенное количество
            double old = intensity.getDouble(0.5D);
            intensity.set(Math.min(1.0D, old / VoidEffectsResolver.BUILT_IN_INTENSITY));
            ExtendedHeightMod.LOG.info(
                "Config migrated: voidParticleIntensity {} (relative to vanilla) -> {} (multiplier)",
                old,
                intensity.getDouble());
        }
        voidParticleIntensity = Math.max(0.0D, Math.min(1.0D, intensity.getDouble(1.0D)));

        Property fog = c.get(
            CATEGORY_VOID,
            "voidFog",
            "auto",
            "Vanilla void darkening (fog and sky turn black towards the bottom, the fog closes in near the bottom in "
                + "survival), measured from the lowest y of the world. auto - as the world decides (worlds without their "
                + "own void effects: off); off - never; on - also in worlds without their own void effects (cannot "
                + "switch it on in a world that switched it off).",
            new String[] { "auto", "off", "on" });
        String value = fog.getString()
            .trim()
            .toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "false".equals(value)) {
            // Версия 1 была boolean: true = on, false = по умолчанию (теперь auto)
            value = "true".equals(value) ? "on" : "auto";
            fog.set(value);
        }
        voidFog = "off".equals(value) ? FogSetting.OFF : "on".equals(value) ? FogSetting.ON : FogSetting.AUTO;

        c.get(Configuration.CATEGORY_GENERAL, "configVersion", 1)
            .set(CONFIG_VERSION);
        if (c.hasChanged()) {
            c.save();
        }
    }
}
