package io.github.extendedheight.mixins.early;

import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** getHeight()/getActualHeight() Forge в расширенных мирах — maxY + 1 */
@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider {

    @Shadow
    public World worldObj;

    @Inject(method = { "getHeight", "getActualHeight" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void eh$height(CallbackInfoReturnable<Integer> cir) {
        if (worldObj != null) {
            HeightRange range = ExtendedHeight.getRange(worldObj);
            if (!range.isVanilla()) {
                cir.setReturnValue(range.maxYExclusive());
            }
        }
    }
}
