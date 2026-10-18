package io.github.extendedheight.mixins.early.client;

import java.nio.IntBuffer;
import java.util.List;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.lwjgl.opengl.ARBOcclusionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Рендер-чанки на весь диапазон (6.1-6.3): сетка от minY; для высокой сетки один раз выделяется больший блок */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    /** Ширина/глубина, под которую выделяет конструктор (дальность 16 -> 33, плюс один) */
    @Unique
    private static final int EH_MAX_WIDE = 34;

    @Shadow
    private WorldClient theWorld;
    @Shadow
    private WorldRenderer[] worldRenderers;
    @Shadow
    private List worldRenderersToUpdate;
    @Shadow
    private int renderChunksWide;
    @Shadow
    private int renderChunksTall;
    @Shadow
    private int renderChunksDeep;
    @Shadow
    private int glRenderListBase;
    @Shadow
    private boolean occlusionEnabled;
    @Shadow
    private IntBuffer glOcclusionQueryBase;
    @Shadow
    private int minBlockX;
    @Shadow
    private int minBlockY;
    @Shadow
    private int minBlockZ;
    @Shadow
    private int maxBlockX;
    @Shadow
    private int maxBlockY;
    @Shadow
    private int maxBlockZ;

    /** Сколько рядов рендереров вмещает текущий блок списков/запросов */
    @Unique
    private int eh$allocatedTall = 16;

    @Unique
    private HeightRange eh$range() {
        return theWorld == null ? HeightRange.VANILLA : ExtendedHeight.getRange((World) theWorld);
    }

    @Inject(method = "loadRenderers", at = @At("HEAD"), require = 1, allow = 1)
    private void eh$ensureCapacity(CallbackInfo ci) {
        int tall = eh$range().sectionCount();
        if (theWorld == null || tall <= eh$allocatedTall) {
            return;
        }
        int renderers = EH_MAX_WIDE * EH_MAX_WIDE * tall;
        glRenderListBase = GLAllocation.generateDisplayLists(renderers * 3);
        if (occlusionEnabled) {
            IntBuffer queries = GLAllocation.createDirectIntBuffer(renderers);
            queries.clear();
            queries.position(0);
            queries.limit(renderers);
            ARBOcclusionQuery.glGenQueriesARB(queries);
            glOcclusionQueryBase = queries;
        }
        eh$allocatedTall = tall;
    }

    @Redirect(
        method = "loadRenderers",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderChunksTall:I",
            opcode = 181 /* PUTFIELD */),
        require = 1,
        allow = 1)
    private void eh$setTall(RenderGlobal self, int value) {
        renderChunksTall = theWorld == null ? value : eh$range().sectionCount();
    }

    /** new WorldRenderer(..., row * 16, ...): ряды начинаются с minY */
    @Redirect(
        method = "loadRenderers",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/WorldRenderer"),
        require = 1,
        allow = 1)
    private WorldRenderer eh$newRenderer(World world, List tileEntities, int x, int y, int z, int glList) {
        return new WorldRenderer(world, tileEntities, x, y + eh$range().minY, z, glList);
    }

    /** Как в ванили, но ряды рендереров на minY + 16 * row */
    @Inject(method = "markRenderersForNewPosition", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    @SuppressWarnings("unchecked")
    private void eh$markRenderersForNewPosition(int x, int y, int z, CallbackInfo ci) {
        HeightRange range = eh$range();
        if (range.isVanilla()) {
            return;
        }
        ci.cancel();
        x -= 8;
        z -= 8;
        minBlockX = minBlockY = minBlockZ = Integer.MAX_VALUE;
        maxBlockX = maxBlockY = maxBlockZ = Integer.MIN_VALUE;
        int l = renderChunksWide * 16;
        int half = l / 2;
        for (int j1 = 0; j1 < renderChunksWide; ++j1) {
            int k1 = j1 * 16;
            int l1 = k1 + half - x;
            if (l1 < 0) {
                l1 -= l - 1;
            }
            l1 /= l;
            k1 -= l1 * l;
            minBlockX = Math.min(minBlockX, k1);
            maxBlockX = Math.max(maxBlockX, k1);
            for (int i2 = 0; i2 < renderChunksDeep; ++i2) {
                int j2 = i2 * 16;
                int k2 = j2 + half - z;
                if (k2 < 0) {
                    k2 -= l - 1;
                }
                k2 /= l;
                j2 -= k2 * l;
                minBlockZ = Math.min(minBlockZ, j2);
                maxBlockZ = Math.max(maxBlockZ, j2);
                for (int row = 0; row < renderChunksTall; ++row) {
                    int ry = range.minY + row * 16;
                    minBlockY = Math.min(minBlockY, ry);
                    maxBlockY = Math.max(maxBlockY, ry);
                    WorldRenderer renderer = worldRenderers[(i2 * renderChunksTall + row) * renderChunksWide + j1];
                    boolean wasDirty = renderer.needsUpdate;
                    renderer.setPosition(k1, ry, j2);
                    if (!wasDirty && renderer.needsUpdate) {
                        worldRenderersToUpdate.add(renderer);
                    }
                }
            }
        }
    }

    /** Ряд = section - minSection вместо ванильного модуля 16; ряды вне сетки пропускаются */
    @Inject(method = "markBlocksForUpdate", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    @SuppressWarnings("unchecked")
    private void eh$markBlocksForUpdate(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
        HeightRange range = eh$range();
        if (range.isVanilla() || worldRenderers == null) {
            return;
        }
        ci.cancel();
        int bx1 = MathHelper.bucketInt(x1, 16);
        int bz1 = MathHelper.bucketInt(z1, 16);
        int bx2 = MathHelper.bucketInt(x2, 16);
        int bz2 = MathHelper.bucketInt(z2, 16);
        int row1 = Math.max(MathHelper.bucketInt(y1, 16) - range.minSection(), 0);
        int row2 = Math.min(MathHelper.bucketInt(y2, 16) - range.minSection(), renderChunksTall - 1);
        for (int bx = bx1; bx <= bx2; ++bx) {
            int ix = Math.floorMod(bx, renderChunksWide);
            for (int row = row1; row <= row2; ++row) {
                for (int bz = bz1; bz <= bz2; ++bz) {
                    int iz = Math.floorMod(bz, renderChunksDeep);
                    WorldRenderer renderer = worldRenderers[(iz * renderChunksTall + row) * renderChunksWide + ix];
                    if (renderer != null && !renderer.needsUpdate) {
                        worldRenderersToUpdate.add(renderer);
                        renderer.markDirty();
                    }
                }
            }
        }
    }
}
