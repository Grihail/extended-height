package io.github.extendedheight.core;

import java.io.File;
import java.util.Locale;

import net.minecraft.world.WorldType;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.selftest.SelfTest;

/** config/extendedheight.cfg: world — высота новых ванильных миров (сервер), void — эффекты пустоты (клиент) */
public final class EhConfig {

    public static final String CATEGORY_WORLD = "world";
    public static final String CATEGORY_VOID = "void";
    public static final int DEFAULT_MIN_Y = -256;
    public static final int DEFAULT_MAX_HEIGHT = 512;
    private static final WorldType[] VANILLA_WORLD_TYPES = { WorldType.DEFAULT, WorldType.FLAT, WorldType.LARGE_BIOMES,
        WorldType.AMPLIFIED, WorldType.DEFAULT_1_1 };
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
    /** Верхние миры ванильных типов: ванильное затемнение пустоты от y 0, как без мода */
    public static volatile boolean vanillaWorldVoidFog = true;
    /** Диапазон новых ванильных миров; {@link HeightRange#VANILLA}, если в конфиге 0..255 */
    public static volatile HeightRange vanillaWorldRange = HeightRange.of(DEFAULT_MIN_Y, DEFAULT_MAX_HEIGHT - 1);

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

        c.setCategoryComment(
            CATEGORY_WORLD,
            "Height of the overworld of vanilla world types (Default, Superflat, Large Biomes, Amplified, Default_1_1). "
                + "Applies only when a world is created: an existing world keeps the height stored in its level.dat. "
                + "Vanilla 1.7.10 values: minY = 0, maxHeight = 256.");
        int minY = c
            .get(
                CATEGORY_WORLD,
                "minY",
                DEFAULT_MIN_Y,
                "Lowest y where blocks can be placed. Multiple of 16, -512..0. Default " + DEFAULT_MIN_Y
                    + ", vanilla 1.7.10: 0.")
            .getInt();
        int maxHeight = c.get(
            CATEGORY_WORLD,
            "maxHeight",
            DEFAULT_MAX_HEIGHT,
            "Build height limit: blocks can be placed below this y. Multiple of 16, at least 256, at most 64 sections "
                + "(1024 blocks) together with minY. Default "
                + DEFAULT_MAX_HEIGHT
                + ", vanilla 1.7.10: 256.")
            .getInt();
        String error = HeightRange.validate(minY, maxHeight - 1);
        if (error == null) {
            vanillaWorldRange = HeightRange.of(minY, maxHeight - 1);
        } else {
            vanillaWorldRange = HeightRange.VANILLA;
            ExtendedHeightMod.LOG.error(
                "Config {}: minY {} and maxHeight {} are invalid ({}); new worlds get the vanilla height 0..255",
                CATEGORY_WORLD,
                minY,
                maxHeight,
                error);
        }
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

        vanillaWorldVoidFog = c.getBoolean(
            "vanillaWorldVoidFog",
            CATEGORY_VOID,
            true,
            "Overworld of vanilla world types (Default, Superflat, Large Biomes, Amplified, Default_1_1): true - vanilla "
                + "void darkening measured from y 0, as without the mod: fog and sky turn black towards y 0, and below "
                + "y 0 the fog closes in to a few blocks in survival (blindness); false - no darkening. voidFog = off "
                + "switches it off too.");

        c.get(Configuration.CATEGORY_GENERAL, "configVersion", 1)
            .set(CONFIG_VERSION);
        if (c.hasChanged()) {
            c.save();
        }
    }

    public static boolean isVanillaWorldType(WorldType type) {
        for (WorldType vanilla : VANILLA_WORLD_TYPES) {
            if (vanilla == type) {
                return true;
            }
        }
        return false;
    }

    /** Регистрирует {@link #vanillaWorldRange} для ванильных типов миров; не в самотесте, кроме стенда compat */
    public static void registerVanillaWorldTypes() {
        if (vanillaWorldRange.isVanilla() || SelfTest.isActive() && !"compat".equals(SelfTest.phase())) {
            return;
        }
        for (WorldType type : VANILLA_WORLD_TYPES) {
            RangeRegistry.registerWorldType(type, vanillaWorldRange);
        }
        ExtendedHeightMod.LOG.info("New overworlds of vanilla world types: height range {}", vanillaWorldRange);
    }
}
