package io.github.extendedheight.mixins.early.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.world.WorldProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.EhConfig;

/** Затемнение пустоты (6.6): в расширенных мирах выключено по умолчанию, отсчёт от minY */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererVoidFog {

    @Shadow
    private Minecraft mc;

    /** Первая запись double d0 = y * voidFogYFactor */
    @ModifyVariable(
        method = "updateFogColor",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0,
        require = 1,
        allow = 1)
    private double eh$fogColorDepth(double d0) {
        if (mc.theWorld == null) {
            return d0;
        }
        HeightRange range = ExtendedHeight.getRange(mc.theWorld);
        if (range.isVanilla()) {
            return d0;
        }
        WorldProvider provider = mc.theWorld.provider;
        return EhConfig.voidFog ? d0 - range.minY * provider.getVoidFogYFactor() : 1.0D;
    }

    @Redirect(
        method = "setupFog",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getWorldHasVoidParticles()Z"),
        require = 1,
        allow = 1)
    private boolean eh$voidFogDistance(WorldProvider provider) {
        boolean vanilla = provider.getWorldHasVoidParticles();
        if (mc.theWorld == null || !ExtendedHeight.isExtended(mc.theWorld)) {
            return vanilla;
        }
        return vanilla && EhConfig.voidFog;
    }

    /** d0 = skyLight / 16 + (y + 4) / 32 от minY */
    @ModifyVariable(method = "setupFog", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 1, allow = 1)
    private double eh$fogDistanceDepth(double d0) {
        if (mc.theWorld == null) {
            return d0;
        }
        HeightRange range = ExtendedHeight.getRange(mc.theWorld);
        return range.isVanilla() ? d0 : d0 - range.minY / 32.0D;
    }
}
