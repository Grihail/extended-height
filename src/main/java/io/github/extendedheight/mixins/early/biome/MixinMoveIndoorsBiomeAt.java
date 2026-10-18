package io.github.extendedheight.mixins.early.biome;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIMoveIndoors;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): жители прячутся в дом от дождя */
@Mixin(EntityAIMoveIndoors.class)
public abstract class MixinMoveIndoorsBiomeAt {

    @Shadow
    private EntityCreature entityObj;

    @Redirect(
        method = "shouldExecute",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$villager(World world, int x, int z) {
        return BiomeLookup.at(world, x, MathHelper.floor_double(entityObj.posY), z);
    }
}
