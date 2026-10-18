package io.github.extendedheight.mixins.early.net;

import net.minecraft.network.play.client.C07PacketPlayerDigging;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Сеттеры C07 (конструктор с полями только клиентский) */
@Mixin(C07PacketPlayerDigging.class)
public interface C07Accessor {

    @Accessor("field_149511_a")
    void eh$setX(int x);

    @Accessor("field_149509_b")
    void eh$setY(int y);

    @Accessor("field_149510_c")
    void eh$setZ(int z);

    @Accessor("field_149507_d")
    void eh$setFace(int face);

    @Accessor("field_149508_e")
    void eh$setStatus(int status);
}
