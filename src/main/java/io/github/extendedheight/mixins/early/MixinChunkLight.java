package io.github.extendedheight.mixins.early;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IExtendedHeightChunk;

/** Свет расширенных чанков: методы заменены построчными копиями ванили с реальным y и дном minY */
@Mixin(Chunk.class)
public abstract class MixinChunkLight {

    @Shadow
    private ExtendedBlockStorage[] storageArrays;
    @Shadow
    public World worldObj;
    @Shadow
    public int[] heightMap;
    @Shadow
    public int[] precipitationHeightMap;
    @Shadow
    public int heightMapMinimum;
    @Shadow
    public boolean isModified;
    @Shadow
    public boolean isTerrainPopulated;
    @Shadow
    @Final
    public int xPosition;
    @Shadow
    @Final
    public int zPosition;
    @Shadow
    private int queuedLightChecks;

    @Shadow
    public abstract int func_150808_b(int x, int y, int z);

    @Shadow
    public abstract int getTopFilledSegment();

    @Shadow
    public abstract Block getBlock(int x, int y, int z);

    @Shadow
    private void updateSkylightNeighborHeight(int x, int z, int fromY, int toY) {}

    @Unique
    private HeightRange eh$extendedRange() {
        HeightRange range = ((IExtendedHeightChunk) this).eh$getRange();
        return range.isVanilla() ? null : range;
    }

    @Unique
    private ExtendedBlockStorage eh$section(HeightRange range, int y) {
        int index = range.sectionIndex(y);
        return index >= 0 && index < storageArrays.length ? storageArrays[index] : null;
    }

    // ---- generateSkylightMap ----

    @Inject(method = "generateSkylightMap", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$generateSkylightMap(CallbackInfo ci) {
        HeightRange range = eh$extendedRange();
        if (range == null) {
            return;
        }
        ci.cancel();
        int minY = range.minY;
        int top = getTopFilledSegment();
        heightMapMinimum = Integer.MAX_VALUE;

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                precipitationHeightMap[x + (z << 4)] = -999;
                int h = top + 16 - 1;
                while (h > minY && func_150808_b(x, h - 1, z) == 0) {
                    --h;
                }
                if (h > minY) {
                    heightMap[z << 4 | x] = h;
                    if (h < heightMapMinimum) {
                        heightMapMinimum = h;
                    }
                }

                if (!worldObj.provider.hasNoSky) {
                    int light = 15;
                    int y = top + 16 - 1;
                    do {
                        int opacity = func_150808_b(x, y, z);
                        if (opacity == 0 && light != 15) {
                            opacity = 1;
                        }
                        light -= opacity;
                        if (light > 0) {
                            ExtendedBlockStorage ebs = eh$section(range, y);
                            if (ebs != null) {
                                ebs.setExtSkylightValue(x, y & 15, z, light);
                                worldObj.func_147479_m((xPosition << 4) + x, y, (zPosition << 4) + z);
                            }
                        }
                        --y;
                    } while (y > minY && light > 0);
                }
            }
        }
        isModified = true;
    }

    // ---- relightBlock ----

    @Inject(method = "relightBlock", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$relightBlock(int x, int y, int z, CallbackInfo ci) {
        HeightRange range = eh$extendedRange();
        if (range == null) {
            return;
        }
        ci.cancel();
        int minY = range.minY;
        // Ваниль маскирует старую высоту & 255 — работает только для 0..255
        int oldHeight = heightMap[z << 4 | x];
        int newHeight = Math.max(oldHeight, y);
        while (newHeight > minY && func_150808_b(x, newHeight - 1, z) == 0) {
            --newHeight;
        }
        if (newHeight == oldHeight) {
            return;
        }

        worldObj.markBlocksDirtyVertical(x + xPosition * 16, z + zPosition * 16, newHeight, oldHeight);
        heightMap[z << 4 | x] = newHeight;
        int worldX = xPosition * 16 + x;
        int worldZ = zPosition * 16 + z;

        if (!worldObj.provider.hasNoSky) {
            if (newHeight < oldHeight) {
                for (int yy = newHeight; yy < oldHeight; ++yy) {
                    ExtendedBlockStorage ebs = eh$section(range, yy);
                    if (ebs != null) {
                        ebs.setExtSkylightValue(x, yy & 15, z, 15);
                        worldObj.func_147479_m((xPosition << 4) + x, yy, (zPosition << 4) + z);
                    }
                }
            } else {
                for (int yy = oldHeight; yy < newHeight; ++yy) {
                    ExtendedBlockStorage ebs = eh$section(range, yy);
                    if (ebs != null) {
                        ebs.setExtSkylightValue(x, yy & 15, z, 0);
                        worldObj.func_147479_m((xPosition << 4) + x, yy, (zPosition << 4) + z);
                    }
                }
            }

            int light = 15;
            int yy = newHeight;
            while (yy > minY && light > 0) {
                --yy;
                int opacity = func_150808_b(x, yy, z);
                if (opacity == 0) {
                    opacity = 1;
                }
                light -= opacity;
                if (light < 0) {
                    light = 0;
                }
                ExtendedBlockStorage ebs = eh$section(range, yy);
                if (ebs != null) {
                    ebs.setExtSkylightValue(x, yy & 15, z, light);
                }
            }
        }

        int height = heightMap[z << 4 | x];
        int from = Math.min(oldHeight, height);
        int to = Math.max(oldHeight, height);
        if (height < heightMapMinimum) {
            heightMapMinimum = height;
        }
        if (!worldObj.provider.hasNoSky) {
            updateSkylightNeighborHeight(worldX - 1, worldZ, from, to);
            updateSkylightNeighborHeight(worldX + 1, worldZ, from, to);
            updateSkylightNeighborHeight(worldX, worldZ - 1, from, to);
            updateSkylightNeighborHeight(worldX, worldZ + 1, from, to);
            updateSkylightNeighborHeight(worldX, worldZ, from, to);
        }
        isModified = true;
    }

    // ---- func_150811_f (заселение света столбца) ----

    @Inject(method = "func_150811_f", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$populateColumnLight(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        HeightRange range = eh$extendedRange();
        if (range == null) {
            return;
        }
        int minY = range.minY;
        int top = getTopFilledSegment();
        boolean foundOpaque = false;
        boolean belowSeaLevelSolid = false;
        int y;
        // Как в ванили: выше 63 пересвечиваются все переходы, ниже скан до первого непрозрачного; дно 0 -> minY
        for (y = top + 16 - 1; y > 63 || y > minY && !belowSeaLevelSolid; --y) {
            int opacity = func_150808_b(x, y, z);
            if (opacity == 255 && y < 63) {
                belowSeaLevelSolid = true;
            }
            if (!foundOpaque && opacity > 0) {
                foundOpaque = true;
            } else
                if (foundOpaque && opacity == 0 && !worldObj.func_147451_t(xPosition * 16 + x, y, zPosition * 16 + z)) {
                    cir.setReturnValue(false);
                    return;
                }
        }
        for (; y > minY; --y) {
            if (getBlock(x, y, z).getLightValue() > 0) {
                worldObj.func_147451_t(xPosition * 16 + x, y, zPosition * 16 + z);
            }
        }
        cir.setReturnValue(true);
    }

    // ---- enqueueRelightChecks ----

    @Inject(method = "enqueueRelightChecks", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$enqueueRelightChecks(CallbackInfo ci) {
        HeightRange range = eh$extendedRange();
        if (range == null) {
            return;
        }
        ci.cancel();
        int sections = storageArrays.length;
        int total = sections * 256;
        for (int i = 0; i < 8; ++i) {
            if (queuedLightChecks >= total) {
                return;
            }
            // Ванильный порядок: индекс секции быстрее всего, затем x, затем z
            int section = queuedLightChecks % sections;
            int lx = queuedLightChecks / sections % 16;
            int lz = queuedLightChecks / (sections * 16);
            ++queuedLightChecks;
            int wx = (xPosition << 4) + lx;
            int wz = (zPosition << 4) + lz;
            ExtendedBlockStorage ebs = storageArrays[section];
            int baseY = range.minY + (section << 4);

            for (int ly = 0; ly < 16; ++ly) {
                int wy = baseY + ly;
                boolean edge = ly == 0 || ly == 15 || lx == 0 || lx == 15 || lz == 0 || lz == 15;
                if (ebs == null && edge || ebs != null && ebs.getBlockByExtId(lx, ly, lz)
                    .getMaterial() == Material.air) {
                    if (worldObj.getBlock(wx, wy - 1, wz)
                        .getLightValue() > 0) {
                        worldObj.func_147451_t(wx, wy - 1, wz);
                    }
                    if (worldObj.getBlock(wx, wy + 1, wz)
                        .getLightValue() > 0) {
                        worldObj.func_147451_t(wx, wy + 1, wz);
                    }
                    if (worldObj.getBlock(wx - 1, wy, wz)
                        .getLightValue() > 0) {
                        worldObj.func_147451_t(wx - 1, wy, wz);
                    }
                    if (worldObj.getBlock(wx + 1, wy, wz)
                        .getLightValue() > 0) {
                        worldObj.func_147451_t(wx + 1, wy, wz);
                    }
                    if (worldObj.getBlock(wx, wy, wz - 1)
                        .getLightValue() > 0) {
                        worldObj.func_147451_t(wx, wy, wz - 1);
                    }
                    if (worldObj.getBlock(wx, wy, wz + 1)
                        .getLightValue() > 0) {
                        worldObj.func_147451_t(wx, wy, wz + 1);
                    }
                    worldObj.func_147451_t(wx, wy, wz);
                }
            }
        }
    }
}
