package io.github.extendedheight.mixins.early.net;

import net.minecraft.network.play.server.S28PacketEffect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Поля S28 (геттеры только клиентские, серверу нужны для замены) */
@Mixin(S28PacketEffect.class)
public interface S28Accessor {

    @Accessor("field_149251_a")
    int eh$getType();

    @Accessor("field_149251_a")
    void eh$setType(int type);

    @Accessor("field_149250_c")
    int eh$getX();

    @Accessor("field_149250_c")
    void eh$setX(int x);

    @Accessor("field_149247_d")
    int eh$getY();

    @Accessor("field_149247_d")
    void eh$setY(int y);

    @Accessor("field_149248_e")
    int eh$getZ();

    @Accessor("field_149248_e")
    void eh$setZ(int z);

    @Accessor("field_149249_b")
    int eh$getData();

    @Accessor("field_149249_b")
    void eh$setData(int data);

    @Accessor("field_149246_f")
    boolean eh$getBroadcast();

    @Accessor("field_149246_f")
    void eh$setBroadcast(boolean broadcast);
}
