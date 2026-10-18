package io.github.extendedheight.mixins.early.client;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IExtendedHeightChunk;

/** Клиентский generateHeightMap: как в ванили, но дно — minY */
@Mixin(Chunk.class)
public abstract class MixinChunkClient {

    @Shadow
    public int[] heightMap;
    @Shadow
    public int[] precipitationHeightMap;
    @Shadow
    public int heightMapMinimum;
    @Shadow
    public boolean isModified;

    @Shadow
    public abstract int func_150808_b(int x, int y, int z);

    @Shadow
    public abstract int getTopFilledSegment();

    @Inject(method = "generateHeightMap", at = @At("HEAD"), cancellable = true)
    private void eh$generateHeightMap(CallbackInfo ci) {
        HeightRange range = ((IExtendedHeightChunk) this).eh$getRange();
        if (range.isVanilla()) {
            return;
        }
        ci.cancel();
        int top = getTopFilledSegment();
        heightMapMinimum = Integer.MAX_VALUE;
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                precipitationHeightMap[x + (z << 4)] = -999;
                int h = top + 16 - 1;
                while (h > range.minY && func_150808_b(x, h - 1, z) == 0) {
                    --h;
                }
                if (h > range.minY) {
                    heightMap[z << 4 | x] = h;
                    if (h < heightMapMinimum) {
                        heightMapMinimum = h;
                    }
                }
            }
        }
        isModified = true;
    }
}
