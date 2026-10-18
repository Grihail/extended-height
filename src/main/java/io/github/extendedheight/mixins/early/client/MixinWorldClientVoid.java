package io.github.extendedheight.mixins.early.client;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.api.VoidEffects;
import io.github.extendedheight.client.VoidStats;
import io.github.extendedheight.core.VoidEffectsResolver;

/** Частицы пустоты в расширенных мирах — по действующим VoidEffects (зона и количество) */
@Mixin(WorldClient.class)
public abstract class MixinWorldClientVoid {

    @Unique
    private final Random eh$intensityRandom = new Random();

    @Inject(method = "doVoidFogParticles", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$voidParticles(int x, int y, int z, CallbackInfo ci) {
        World world = (World) (Object) this;
        HeightRange range = ExtendedHeight.getRange(world);
        if (range.isVanilla()) {
            return;
        }
        ci.cancel();
        VoidEffects effects = VoidEffectsResolver.effective(world);
        boolean particles = effects.hasParticles();
        int zeroY = effects.particleBaseY;
        double intensity = effects.particleIntensity;
        Random rand = world.rand;
        Random random = new Random();
        for (int l = 0; l < 1000; ++l) {
            int i1 = x + rand.nextInt(16) - rand.nextInt(16);
            int j1 = y + rand.nextInt(16) - rand.nextInt(16);
            int k1 = z + rand.nextInt(16) - rand.nextInt(16);
            Block block = world.getBlock(i1, j1, k1);
            if (block.getMaterial() == Material.air) {
                if (particles && rand.nextInt(8) > j1 - zeroY
                    && world.provider.getWorldHasVoidParticles()
                    && eh$intensityRandom.nextDouble() < intensity) {
                    VoidStats.particles++;
                    world.spawnParticle(
                        "depthsuspend",
                        i1 + rand.nextFloat(),
                        j1 + rand.nextFloat(),
                        k1 + rand.nextFloat(),
                        0.0D,
                        0.0D,
                        0.0D);
                }
            } else {
                block.randomDisplayTick(world, i1, j1, k1, random);
            }
        }
    }
}
