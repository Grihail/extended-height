package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.passive.EntityBat;
import net.minecraft.util.ChunkCoordinates;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Самотест: цель полёта летучей мыши */
@Mixin(EntityBat.class)
public interface BatAccessor {

    @Accessor("spawnPosition")
    ChunkCoordinates eh$getSpawnPosition();
}
