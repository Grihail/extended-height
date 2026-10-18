package io.github.extendedheight.mixins.early;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Самотест: защищённая случайная точка естественного спавна */
@Mixin(SpawnerAnimals.class)
public interface SpawnerAnimalsInvoker {

    @Invoker("func_151350_a")
    static ChunkPosition eh$randomSpawnPosition(World world, int chunkX, int chunkZ) {
        throw new AssertionError("mixin not applied");
    }
}
