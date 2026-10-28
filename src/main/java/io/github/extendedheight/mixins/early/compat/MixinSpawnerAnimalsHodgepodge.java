package io.github.extendedheight.mixins.early.compat;

import java.util.Random;

import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.SpawnHeight;

/** 7.6a, Hodgepodge optimizeMobSpawning: y из {@link SpawnHeight#randomY}; remap = false, приоритет 1100 */
@Mixin(value = SpawnerAnimals.class, priority = 1100)
public abstract class MixinSpawnerAnimalsHodgepodge {

    @Dynamic("hodgepodge$getRandomPosInChunk is added by Hodgepodge's MixinSpawnerAnimals_optimizeSpawning")
    @Redirect(
        method = "hodgepodge$getRandomPosInChunk(Lnet/minecraft/world/World;II)Lcom/gtnewhorizon/gtnhlib/blockpos/BlockPos;",
        at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 2, remap = false),
        remap = false,
        require = 1,
        allow = 1)
    private int eh$hodgepodgeSpawnY(Random rand, int vanillaBound, World world, int cx, int cz) {
        HeightRange range = ExtendedHeight.getRange(world);
        if (range.isVanilla()) {
            return rand.nextInt(vanillaBound);
        }
        return SpawnHeight.randomY(rand, world.getChunkFromChunkCoords(cx, cz), range);
    }
}
