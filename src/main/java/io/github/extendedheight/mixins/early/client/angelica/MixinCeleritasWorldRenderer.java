package io.github.extendedheight.mixins.early.client.angelica;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Место 2: пределы постройки для Celeritas (ваниль 0 и 256) — диапазон расширенного мира */
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer", remap = false)
public abstract class MixinCeleritasWorldRenderer {

    @Inject(method = "getMinimumBuildHeight", at = @At("HEAD"), cancellable = true, require = 1)
    private void eh$minimum(CallbackInfoReturnable<Integer> cir) {
        HeightRange range = eh$range();
        if (!range.isVanilla()) {
            cir.setReturnValue(range.minY);
        }
    }

    @Inject(method = "getMaximumBuildHeight", at = @At("HEAD"), cancellable = true, require = 1)
    private void eh$maximum(CallbackInfoReturnable<Integer> cir) {
        HeightRange range = eh$range();
        if (!range.isVanilla()) {
            cir.setReturnValue(range.maxYExclusive());
        }
    }

    private static HeightRange eh$range() {
        World world = Minecraft.getMinecraft().theWorld;
        return world == null ? HeightRange.VANILLA : ExtendedHeight.getRange(world);
    }
}
