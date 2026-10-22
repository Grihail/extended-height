package io.github.extendedheight.core;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Плагин миксинов: выключатель для baseline самотеста (свойство extendedheight.disableMixins или файл) */
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

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (ANGELICA && (mixinClassName.endsWith(".client.MixinRenderGlobal")
            || mixinClassName.endsWith(".client.MixinEntityRendererDistance"))) {
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

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
