package io.github.extendedheight.mixins.early.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.GuiIngameForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.IBiomeAtProvider;
import io.github.extendedheight.core.BiomeLookup;

/** Биом в точке (вариант C): имя биома на F3 у ног игрока */
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class MixinF3BiomeAt {

    @Redirect(
        method = "renderHUDText",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/Chunk;getBiomeGenForWorldCoords(IILnet/minecraft/world/biome/WorldChunkManager;)Lnet/minecraft/world/biome/BiomeGenBase;",
            remap = true),
        require = 1,
        allow = 1)
    private BiomeGenBase eh$f3(Chunk chunk, int lx, int lz, WorldChunkManager manager) {
        BiomeGenBase column = chunk.getBiomeGenForWorldCoords(lx, lz, manager);
        Minecraft mc = Minecraft.getMinecraft();
        IBiomeAtProvider p = BiomeLookup.providerOf(mc.theWorld);
        if (p == null || mc.thePlayer == null) {
            return column;
        }
        BiomeGenBase b = p.getBiome(
            mc.theWorld,
            chunk.xPosition * 16 + lx,
            MathHelper.floor_double(mc.thePlayer.boundingBox.minY),
            chunk.zPosition * 16 + lz,
            column);
        return b != null ? b : column;
    }
}
