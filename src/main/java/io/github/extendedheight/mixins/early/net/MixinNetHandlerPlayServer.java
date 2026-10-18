package io.github.extendedheight.mixins.early.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.net.SPacketHeightTable;

@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    @Shadow
    @Final
    public NetworkManager netManager;
    @Shadow
    public EntityPlayerMP playerEntity;

    /** Таблица высот уходит перед S01 и S07 в той же очереди (5.12) */
    @Inject(method = "sendPacket", at = @At("HEAD"), require = 1, allow = 1)
    private void eh$sendHeightTable(Packet packet, CallbackInfo ci) {
        if (packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn) {
            SPacketHeightTable table = SPacketHeightTable.forServer();
            if (table != null) {
                netManager.scheduleOutboundPacket(table);
            }
        }
    }

    /** y >= getBuildLimit(): в расширенном мире предел maxY + 1 (2.16) */
    @Redirect(
        method = { "processPlayerDigging", "processPlayerBlockPlacement" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"),
        require = 4,
        allow = 4)
    private int eh$buildLimit(MinecraftServer server) {
        HeightRange range = ExtendedHeight.getRange(playerEntity.worldObj);
        return range.isVanilla() ? server.getBuildLimit() : range.maxYExclusive();
    }
}
