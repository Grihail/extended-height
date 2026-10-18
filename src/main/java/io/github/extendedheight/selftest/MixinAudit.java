package io.github.extendedheight.selftest;

import org.spongepowered.asm.mixin.MixinEnvironment;

/** Заставляет Mixin сразу применить все миксины: ненайденная цель — немедленная ошибка */
final class MixinAudit {

    private MixinAudit() {}

    /** @return null при успехе, иначе ошибка */
    static Throwable run() {
        try {
            MixinEnvironment.getCurrentEnvironment()
                .audit();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
