package io.github.extendedheight.mixins.early.net;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.network.handshake.FMLHandshakeMessage;
import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.selftest.SelfTest;

/** Только самотест net-nomod: клиент скрывает этот мод в списке модов рукопожатия */
@Mixin(value = FMLHandshakeMessage.ModList.class, remap = false)
public abstract class MixinHandshakeModList {

    @Shadow
    private Map<String, String> modTags;

    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("RETURN"), require = 1, allow = 1)
    private void eh$hideMod(List<ModContainer> mods, CallbackInfo ci) {
        if (SelfTest.hidesModInHandshake()) {
            modTags.remove(ExtendedHeightMod.MODID);
        }
    }
}
