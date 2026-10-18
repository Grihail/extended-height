package io.github.extendedheight.mixins.early.client;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.client.ClientRanges;
import io.github.extendedheight.core.IExtendedHeightWorld;

/** Клиентские миры: диапазон из таблицы сервера задаётся в finishSetup() перед createChunkProvider() */
@Mixin(World.class)
public abstract class MixinWorldClientInit {

    @Inject(
        method = "finishSetup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;createChunkProvider()Lnet/minecraft/world/chunk/IChunkProvider;",
            remap = true),
        remap = false,
        require = 1,
        allow = 1)
    private void eh$resolveRangeClient(CallbackInfo ci) {
        ((IExtendedHeightWorld) this).eh$setRange(ClientRanges.resolve((World) (Object) this));
    }
}
