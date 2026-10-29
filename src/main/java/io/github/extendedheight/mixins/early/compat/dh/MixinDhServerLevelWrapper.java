package io.github.extendedheight.mixins.early.compat.dh;

import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Серверный уровень Distant Horizons: низ мира minY, высота — число блоков диапазона (как в DH для 1.18+) */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper", remap = false)
public abstract class MixinDhServerLevelWrapper {

    @Shadow
    @Final
    private WorldServer level;

    @Inject(method = "getMinHeight", at = @At("HEAD"), cancellable = true, require = 1)
    private void eh$minHeight(CallbackInfoReturnable<Integer> cir) {
        HeightRange range = ExtendedHeight.getRange(this.level);
        if (!range.isVanilla()) {
            cir.setReturnValue(range.minY);
        }
    }

    @Inject(method = "getMaxHeight", at = @At("HEAD"), cancellable = true, require = 1)
    private void eh$maxHeight(CallbackInfoReturnable<Integer> cir) {
        HeightRange range = ExtendedHeight.getRange(this.level);
        if (!range.isVanilla()) {
            cir.setReturnValue(range.sectionCount() * 16);
        }
    }
}
