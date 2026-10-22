package io.github.extendedheight.mixins.early.net;

import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.net.IVanillaChunkPacket;

/** Ванильный S21 для чанка расширенного мира запоминает чанк; замена — в MixinNetworkManager */
@Mixin(S21PacketChunkData.class)
public abstract class MixinS21PacketChunkData implements IVanillaChunkPacket {

    @Unique
    private Chunk eh$chunk;
    @Unique
    private int eh$flags;
    @Unique
    private boolean eh$full;

    @Inject(method = "<init>(Lnet/minecraft/world/chunk/Chunk;ZI)V", at = @At("RETURN"), require = 1, allow = 1)
    private void eh$remember(Chunk chunk, boolean full, int flags, CallbackInfo ci) {
        if (chunk != null && ExtendedHeight.isExtended(chunk.worldObj)) {
            this.eh$chunk = chunk;
            this.eh$flags = flags;
            this.eh$full = full;
        }
    }

    @Override
    public Chunk eh$getChunk() {
        return this.eh$chunk;
    }

    @Override
    public int eh$getFlags() {
        return this.eh$flags;
    }

    @Override
    public boolean eh$isFull() {
        return this.eh$full;
    }
}
