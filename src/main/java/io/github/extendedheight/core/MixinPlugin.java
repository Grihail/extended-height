package io.github.extendedheight.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Плагин миксинов: выключатель для baseline самотеста и выбор миксинов под Angelica и Hodgepodge */
public class MixinPlugin implements IMixinConfigPlugin {

    public static final String DISABLE_FILE = "extendedheight-disable-mixins";

    private static final boolean DISABLED = Boolean.getBoolean("extendedheight.disableMixins")
        || new File(DISABLE_FILE).exists();

    public static boolean mixinsDisabled() {
        return DISABLED;
    }

    @Override
    public void onLoad(String mixinPackage) {
        if (DISABLED) {
            LogManager.getLogger("extendedheight")
                .warn("ExtendedHeight mixins are DISABLED (self-test baseline mode)");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /** С Angelica миксины ванильного рендера не применяются — диапазон получает Angelica */
    private static final boolean ANGELICA = MixinPlugin.class
        .getResource("/com/gtnewhorizons/angelica/loading/AngelicaTweaker.class") != null;

    public static boolean angelicaPresent() {
        return ANGELICA;
    }

    /** Метод, который MixinSpawnerAnimals_optimizeSpawning из Hodgepodge добавляет в SpawnerAnimals */
    static final String HODGEPODGE_SPAWN_METHOD = "hodgepodge$getRandomPosInChunk";

    private static final String HODGEPODGE_SPAWN_MIXIN = "/com/mitchej123/hodgepodge/mixins/early/minecraft/"
        + "MixinSpawnerAnimals_optimizeSpawning.class";

    /** B:optimizeMobSpawning=<значение> в любой категории config/hodgepodge.cfg */
    private static final Pattern HODGEPODGE_SPAWN_OPTION = Pattern
        .compile("^\\s*B:\"?optimizeMobSpawning\"?\\s*=\\s*(\\S*)\\s*$");

    private static Boolean hodgepodgeSpawning;

    /** Применяет ли Hodgepodge optimizeSpawning: читаем его cfg как Forge (по умолчанию true), проверка в postApply */
    public static synchronized boolean hodgepodgeSpawningPresent() {
        if (hodgepodgeSpawning == null) {
            hodgepodgeSpawning = MixinPlugin.class.getResource(HODGEPODGE_SPAWN_MIXIN) != null
                && readHodgepodgeSpawningOption();
            LogManager.getLogger("extendedheight")
                .info(
                    "Hodgepodge optimizeMobSpawning {}: the compatibility mixin for its spawn position is {}",
                    hodgepodgeSpawning ? "is active" : "is not active",
                    hodgepodgeSpawning ? "applied" : "skipped");
        }
        return hodgepodgeSpawning;
    }

    private static boolean readHodgepodgeSpawningOption() {
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        File cfg = new File(new File(home, "config"), "hodgepodge.cfg");
        if (!cfg.isFile()) {
            return true;
        }
        try {
            for (String line : Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8)) {
                Matcher m = HODGEPODGE_SPAWN_OPTION.matcher(line);
                if (m.matches()) {
                    return !"false".equalsIgnoreCase(m.group(1));
                }
            }
        } catch (IOException | RuntimeException e) {
            LogManager.getLogger("extendedheight")
                .warn("Could not read {}, assuming Hodgepodge's default optimizeMobSpawning=true", cfg, e);
        }
        return true;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".compat.MixinSpawnerAnimalsHodgepodge")) {
            return !DISABLED && hodgepodgeSpawningPresent();
        }
        if (ANGELICA && (mixinClassName.endsWith(".client.MixinRenderGlobal")
            || mixinClassName.endsWith(".client.MixinEntityRendererDistance"))) {
            return false;
        }
        // Миксины на классы Angelica (вариант B) есть только вместе с Angelica
        if (!ANGELICA && mixinClassName.contains(".client.angelica.")) {
            return false;
        }
        return !DISABLED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    /** Hodgepodge добавил свой метод спавна, а наш миксин пропущен — падаем при загрузке класса */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".early.MixinSpawnerAnimals") && !hodgepodgeSpawningPresent()) {
            for (MethodNode method : targetClass.methods) {
                if (HODGEPODGE_SPAWN_METHOD.equals(method.name)) {
                    throw new IllegalStateException(
                        "ExtendedHeight: Hodgepodge's optimized mob spawning (" + HODGEPODGE_SPAWN_METHOD
                            + ") is applied to SpawnerAnimals, but its config was read as disabled, so the "
                            + "ExtendedHeight compatibility mixin was skipped. Please report this with "
                            + "config/hodgepodge.cfg and the Hodgepodge version.");
                }
            }
        }
    }
}
