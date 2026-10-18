package io.github.extendedheight.mixins.early.net;

import net.minecraft.network.play.server.S23PacketBlockChange;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Приватные координаты S23 (геттеры только клиентские, серверу нужны для замены) */
@Mixin(S23PacketBlockChange.class)
public interface S23Accessor {

    @Accessor("field_148887_a")
    int eh$getX();

    @Accessor("field_148887_a")
    void eh$setX(int x);

    @Accessor("field_148885_b")
    int eh$getY();

    @Accessor("field_148885_b")
    void eh$setY(int y);

    @Accessor("field_148886_c")
    int eh$getZ();

    @Accessor("field_148886_c")
    void eh$setZ(int z);
}
