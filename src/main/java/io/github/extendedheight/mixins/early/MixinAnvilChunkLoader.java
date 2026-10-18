package io.github.extendedheight.mixins.early;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.ExtendedChunkNbt;

/** Чтение секций с Y < 0 или Y > 15: расширенные миры читает {@link ExtendedChunkNbt}, ванильные — ваниль */
@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader {

    @Inject(method = "readChunkFromNBT", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$readExtended(World world, NBTTagCompound level, CallbackInfoReturnable<Chunk> cir) {
        HeightRange range = ExtendedHeight.getRange(world);
        if (!range.isVanilla()) {
            cir.setReturnValue(ExtendedChunkNbt.read(world, level, range));
        }
    }
}
