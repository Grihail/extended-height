package io.github.extendedheight.core;

import net.minecraft.world.World;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.api.IVoidEffectsProvider;
import io.github.extendedheight.api.VoidEffects;

/** Действующие эффекты пустоты: выбор мира или встроенные, ослабленные конфигом игрока */
public final class VoidEffectsResolver {

    /** Количество частиц в расширенных мирах без своего выбора, относительно ванили */
    public static final float BUILT_IN_INTENSITY = 0.5F;

    private VoidEffectsResolver() {}

    /** @return null для ванильных миров (работает ванильный код) */
    public static VoidEffects effective(World world) {
        HeightRange range = ExtendedHeight.getRange(world);
        if (range.isVanilla()) {
            return null;
        }
        VoidEffects chosen = worldChoice(world);
        boolean builtIn = chosen == null;
        if (builtIn) {
            chosen = VoidEffects.of(false, EhConfig.voidParticlesFollowMinY ? range.minY : 0, BUILT_IN_INTENSITY);
        }
        boolean fog;
        switch (EhConfig.voidFog) {
            case OFF:
                fog = false;
                break;
            case ON:
                fog = builtIn || chosen.fog;
                break;
            default:
                fog = chosen.fog;
        }
        float intensity = chosen.particleIntensity * (float) EhConfig.voidParticleIntensity;
        return VoidEffects.of(fog, chosen.particleBaseY, Math.max(0.0F, Math.min(1.0F, intensity)));
    }

    /** Выбор провайдера мира, иначе типа мира (только измерение 0), иначе null */
    public static VoidEffects worldChoice(World world) {
        if (world.provider instanceof IVoidEffectsProvider) {
            VoidEffects v = ((IVoidEffectsProvider) world.provider).getVoidEffects(world);
            if (v != null) {
                return v;
            }
        }
        if (world.provider.dimensionId == 0 && world.getWorldInfo()
            .getTerrainType() instanceof IVoidEffectsProvider) {
            return ((IVoidEffectsProvider) world.getWorldInfo()
                .getTerrainType()).getVoidEffects(world);
        }
        return null;
    }
}
