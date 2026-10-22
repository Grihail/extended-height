package io.github.extendedheight.mixins.early.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.core.RenderDistanceLimit;

/** Дальняя плоскость камеры не дальше лимита рендера MixinRenderGlobal (16) */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererDistance {

    @Shadow
    private Minecraft mc;

    @Redirect(
        method = "setupCameraTransform",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I",
            opcode = 180 /* GETFIELD */),
        require = 1,
        allow = 1)
    private int eh$farPlaneDistance(GameSettings settings) {
        return RenderDistanceLimit
            .clamp(mc.theWorld != null && ExtendedHeight.isExtended(mc.theWorld), settings.renderDistanceChunks);
    }
}
