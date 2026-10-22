package io.github.extendedheight.mixins.early.client.angelica;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Места 5 и 6 и зажим y: индекс секции, пределы света, зажим чтения света */
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice", remap = false)
public abstract class MixinWorldSlice {

    @Shadow
    private WorldClient world;

    /** Место 5: chunk.getBlockStorageArray()[origin.y], второе чтение origin.y */
    @ModifyExpressionValue(
        method = "prepare",
        at = @At(
            value = "FIELD",
            target = "Lcom/gtnewhorizons/angelica/compat/mojang/ChunkSectionPos;y:I",
            ordinal = 1),
        require = 1)
    private static int eh$storageIndex(int y, @Local(argsOnly = true) World world) {
        return y - ExtendedHeight.getRange(world)
            .minSection();
    }

    /** Зажим y в 0..255 для чтения света (CompatMathHelper.clampHeight) */
    @Redirect(
        method = "getLightLevel",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/compat/mojang/CompatMathHelper;clampHeight(I)I"),
        require = 1)
    private int eh$clampHeight(int y) {
        HeightRange range = ExtendedHeight.getRange(this.world);
        return range.isVanilla() ? Math.max(0, Math.min(255, y)) : Math.max(range.minY, Math.min(range.maxY, y));
    }

    // Место 6: пределы света (ваниль 0 и 256) в двух читающих их методах

    @ModifyVariable(method = "func_72802_i", at = @At("STORE"), index = 5, require = 1)
    private int eh$minHeightBrightness(int minHeight) {
        return eh$min(minHeight);
    }

    @ModifyVariable(method = "func_72802_i", at = @At("STORE"), index = 6, require = 1)
    private int eh$maxHeightBrightness(int maxHeight) {
        return eh$max(maxHeight);
    }

    @ModifyVariable(method = "getRawSkyLight", at = @At("STORE"), index = 4, require = 1)
    private int eh$minHeightRawSky(int minHeight) {
        return eh$min(minHeight);
    }

    @ModifyVariable(method = "getRawSkyLight", at = @At("STORE"), index = 5, require = 1)
    private int eh$maxHeightRawSky(int maxHeight) {
        return eh$max(maxHeight);
    }

    private int eh$min(int original) {
        HeightRange range = ExtendedHeight.getRange(this.world);
        return range.isVanilla() ? original : range.minY;
    }

    private int eh$max(int original) {
        HeightRange range = ExtendedHeight.getRange(this.world);
        return range.isVanilla() ? original : range.maxYExclusive();
    }
}
