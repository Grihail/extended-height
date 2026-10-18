package io.github.extendedheight.mixins.early.net;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import io.github.extendedheight.net.ExtPackets;

/** Все исходящие пакеты: S23/C07/C08 с неподходящим y меняются на расширенные ({@link ExtPackets#onOutbound}) */
@Mixin(NetworkManager.class)
public abstract class MixinNetworkManager {

    @ModifyVariable(method = "scheduleOutboundPacket", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
    private Packet eh$swapOutbound(Packet packet) {
        return ExtPackets.onOutbound(((NetworkManager) (Object) this).getNetHandler(), packet);
    }
}
