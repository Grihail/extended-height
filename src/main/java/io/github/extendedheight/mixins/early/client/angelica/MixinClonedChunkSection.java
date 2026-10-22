package io.github.extendedheight.mixins.early.client.angelica;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Места 3 и 4: вертикальный предел и индекс хранилища клонированной секции */
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection", remap = false)
public abstract class MixinClonedChunkSection {

    @Shadow
    private World world;

    @Inject(method = "isOutOfBuildLimitVertically", at = @At("HEAD"), cancellable = true, require = 1)
    private static void eh$limit(World world, int y, CallbackInfoReturnable<Boolean> cir) {
        HeightRange range = ExtendedHeight.getRange(world);
        if (!range.isVanilla()) {
            cir.setReturnValue(y < range.minY || y >= range.maxYExclusive());
        }
    }

    @Inject(method = "getChunkSection", at = @At("HEAD"), cancellable = true, require = 1)
    private void eh$section(Chunk chunk, ChunkSectionPos pos, CallbackInfoReturnable<ExtendedBlockStorage> cir) {
        HeightRange range = ExtendedHeight.getRange(this.world);
        if (range.isVanilla()) {
            return;
        }
        int blockY = pos.y << 4;
        if (blockY < range.minY || blockY >= range.maxYExclusive()) {
            cir.setReturnValue(null);
            return;
        }
        ExtendedBlockStorage[] array = chunk.getBlockStorageArray();
        int index = pos.y - range.minSection();
        cir.setReturnValue(index >= 0 && index < array.length ? array[index] : null);
    }
}
