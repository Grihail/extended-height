package io.github.extendedheight.mixins.early.biome;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): спавн слизней в болотах */
@Mixin(EntitySlime.class)
public abstract class MixinSlimeBiomeAt {

    @Redirect(
        method = "getCanSpawnHere",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;"),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$atEntity(World world, int x, int z) {
        return BiomeLookup.at(world, x, MathHelper.floor_double(((Entity) (Object) this).posY), z);
    }
}
