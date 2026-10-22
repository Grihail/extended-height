package io.github.extendedheight.mixins.early.client.angelica;

import net.minecraft.client.multiplayer.WorldClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Менеджер секций Angelica с диапазоном расширенного мира (вариант B) */
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager", remap = false)
public abstract class MixinAngelicaRenderSectionManager {

    @Shadow
    private WorldClient world;

    /** Место 1: сетка от minSection на sectionCount секций (ваниль 0 и 16) */
    @ModifyArgs(
        method = "create",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaRenderSectionManager;<init>(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;Lnet/minecraft/client/multiplayer/WorldClient;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;IIILcom/gtnewhorizons/angelica/rendering/celeritas/threading/ChunkTaskProvider;)V"),
        require = 1)
    private static void eh$sectionRange(Args args) {
        WorldClient client = args.get(1);
        HeightRange range = ExtendedHeight.getRange(client);
        if (!range.isVanilla()) {
            args.set(4, range.minSection());
            args.set(5, range.minSection() + range.sectionCount());
        }
    }

    /** Место 7: индекс массива хранилища для секции y — y - minSection */
    @ModifyVariable(method = "isSectionVisuallyEmpty", at = @At("HEAD"), index = 2, argsOnly = true, require = 1)
    private int eh$storageIndex(int y) {
        return y - ExtendedHeight.getRange(this.world)
            .minSection();
    }

    /** Место 8: число секций столбца при пересборке биомов... */
    @ModifyVariable(method = "flushBiomeRebuilds", at = @At("STORE"), index = 1, require = 1)
    private int eh$biomeSectionCount(int original) {
        HeightRange range = ExtendedHeight.getRange(this.world);
        return range.isVanilla() ? original : range.sectionCount();
    }

    /** ...начиная с minSection, а не с 0 (запись кеша секций)... */
    @ModifyArg(
        method = "flushBiomeRebuilds",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/world/cloned/ClonedChunkSectionCache;invalidate(III)V"),
        index = 1,
        require = 1)
    private int eh$biomeInvalidate(int cy) {
        return cy + ExtendedHeight.getRange(this.world)
            .minSection();
    }

    /** ...и сама пересборка */
    @ModifyArg(
        method = "flushBiomeRebuilds",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaRenderSectionManager;scheduleRebuild(IIIZ)V"),
        index = 1,
        require = 1)
    private int eh$biomeRebuild(int cy) {
        return cy + ExtendedHeight.getRange(this.world)
            .minSection();
    }
}
