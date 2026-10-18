package io.github.extendedheight.mixins.early;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Мир ChunkCache (через него спрашивают цвета блоков при рендере чанков) */
@Mixin(ChunkCache.class)
public interface ChunkCacheAccessor {

    @Accessor("worldObj")
    World eh$getWorld();
}
