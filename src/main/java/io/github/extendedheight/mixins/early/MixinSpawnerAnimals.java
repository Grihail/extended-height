package io.github.extendedheight.mixins.early;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** 7.6, точка спавна: y = minY + nextInt(top + 15 - minY) — спавн ниже 0 и без падения тика */
@Mixin(SpawnerAnimals.class)
public abstract class MixinSpawnerAnimals {

    @Inject(method = "func_151350_a", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void eh$randomSpawnPos(World world, int cx, int cz, CallbackInfoReturnable<ChunkPosition> cir) {
        HeightRange range = ExtendedHeight.getRange(world);
        if (range.isVanilla()) {
            return;
        }
        Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
        int x = cx * 16 + world.rand.nextInt(16);
        int z = cz * 16 + world.rand.nextInt(16);
        int top = chunk == null ? range.maxYExclusive() : chunk.getTopFilledSegment() + 16 - 1;
        int y = range.minY + world.rand.nextInt(Math.max(1, top - range.minY));
        cir.setReturnValue(new ChunkPosition(x, y, z));
    }
}
