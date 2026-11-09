package io.github.extendedheight.mixins.early;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.core.SectionLayout;

/** Сгенерированный чанк: секции по ванильным индексам переносятся на свои y до populate */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServerLayout {

    @Shadow
    public WorldServer worldObj;

    @Redirect(
        method = "originalLoadChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;",
            remap = true),
        remap = false,
        require = 1,
        allow = 1)
    private Chunk eh$normalizeGenerated(IChunkProvider generator, int x, int z) {
        Chunk chunk = generator.provideChunk(x, z);
        SectionLayout.normalize(chunk, ExtendedHeight.getRange(worldObj));
        return chunk;
    }
}
