package io.github.extendedheight.mixins.early.client;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Соединение клиентского мира; задано до того, как finishSetup() выбирает диапазон */
@Mixin(WorldClient.class)
public interface WorldClientAccessor {

    @Accessor("sendQueue")
    NetHandlerPlayClient eh$getSendQueue();
}
