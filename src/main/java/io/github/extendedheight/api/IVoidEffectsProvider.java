package io.github.extendedheight.api;

import net.minecraft.world.World;

/** Эффекты пустоты мира: на WorldProvider или WorldType; конфиг их только ослабляет; вызов каждый кадр */
public interface IVoidEffectsProvider {

    /** @return эффекты мира или null для встроенных по умолчанию */
    VoidEffects getVoidEffects(World world);
}
