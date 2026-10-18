package io.github.extendedheight.mixins.early.net;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.selftest.NetServerSelfTest;

/** Понятная причина отказа клиенту без мода в рукопожатии FML (5.13); решение — в checkRemote */
@Mixin(value = FMLNetworkHandler.class, remap = false)
public abstract class MixinFMLNetworkHandler {

    @Inject(
        method = "checkModList(Ljava/util/Map;Lcpw/mods/fml/relauncher/Side;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        // два return: null (принят) и String.format(...) (отклонён)
        require = 2,
        allow = 2)
    private static void eh$explainRejection(Map<String, String> remoteMods, Side remoteSide,
        CallbackInfoReturnable<String> cir) {
        String reason = ExtendedHeightMod.rejectionReason(remoteMods, remoteSide);
        if (cir.getReturnValue() != null && reason != null) {
            cir.setReturnValue(reason + " (" + cir.getReturnValue() + ")");
        }
        if (remoteSide == Side.CLIENT && cir.getReturnValue() != null) {
            NetServerSelfTest.lastRejection = cir.getReturnValue();
        }
    }
}
