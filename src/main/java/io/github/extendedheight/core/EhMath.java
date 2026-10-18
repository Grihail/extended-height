package io.github.extendedheight.core;

import net.minecraft.world.World;

import io.github.extendedheight.api.ExtendedHeight;

/** Помощники для ванильного кода, считающего y >= 0 */
public final class EhMath {

    private EhMath() {}

    /** Замена y под ванильный (int)-каст: ниже 0 в расширенном мире floor(y), иначе сам y */
    public static double yForIntCast(World world, double y) {
        return y < 0 && world != null && ExtendedHeight.isExtended(world) ? Math.floor(y) : y;
    }
}
