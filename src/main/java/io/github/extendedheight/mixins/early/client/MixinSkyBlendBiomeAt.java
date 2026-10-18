package io.github.extendedheight.mixins.early.client;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.ForgeHooksClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): цвет неба у камеры; кеш Forge сбрасывается и по y */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinSkyBlendBiomeAt {

    @Shadow
    private static boolean skyInit;

    @Unique
    private static int eh$lastY = Integer.MIN_VALUE;

    @Inject(method = "getSkyBlendColour", at = @At("HEAD"), require = 1, allow = 1)
    private static void eh$dropCacheOnHeight(World world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (y != eh$lastY && BiomeLookup.providerOf(world) != null) {
            skyInit = false;
        }
        eh$lastY = y;
    }

    @Redirect(
        method = "getSkyBlendColour",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;",
            remap = true),
        require = 1,
        allow = 1)
    private static BiomeGenBase eh$sky(World access, int x, int z, World world, int px, int y) {
        return BiomeLookup.at(access, x, y, z);
    }
}
