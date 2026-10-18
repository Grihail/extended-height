package io.github.extendedheight.mixins.early.client;

import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Самотест: цвет тумана последнего кадра */
@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {

    @Accessor("fogColorRed")
    float eh$getFogRed();

    @Accessor("fogColorGreen")
    float eh$getFogGreen();

    @Accessor("fogColorBlue")
    float eh$getFogBlue();
}
