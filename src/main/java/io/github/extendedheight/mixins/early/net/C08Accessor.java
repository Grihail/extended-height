package io.github.extendedheight.mixins.early.net;

import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Сеттеры C08 (конструктор с полями только клиентский) */
@Mixin(C08PacketPlayerBlockPlacement.class)
public interface C08Accessor {

    @Accessor("field_149583_a")
    void eh$setX(int x);

    @Accessor("field_149581_b")
    void eh$setY(int y);

    @Accessor("field_149582_c")
    void eh$setZ(int z);

    @Accessor("field_149579_d")
    void eh$setDirection(int direction);

    @Accessor("field_149580_e")
    void eh$setStack(ItemStack stack);

    @Accessor("field_149577_f")
    void eh$setHitX(float hitX);

    @Accessor("field_149578_g")
    void eh$setHitY(float hitY);

    @Accessor("field_149584_h")
    void eh$setHitZ(float hitZ);
}
