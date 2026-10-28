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
import io.github.extendedheight.core.SpawnHeight;

/** 7.6, точка спавна: y = bottom + nextInt(top + 15 - bottom); путь Hodgepodge — в 7.6a */
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
        int y = SpawnHeight.randomY(world.rand, chunk, range);
        cir.setReturnValue(new ChunkPosition(x, y, z));
    }
}
