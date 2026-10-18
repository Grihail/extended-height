package io.github.extendedheight.mixins.early.ycast;

import net.minecraft.entity.monster.EntityZombie;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.core.EhMath;

/** EntityZombie, лечение жителя и звуки: (int) posY ниже 0 — floor(y) */
@Mixin(EntityZombie.class)
public abstract class MixinZombieY {

    /** поиск решёток/кроватей, нижняя граница */
    @Redirect(
        method = "getConversionTimeBoost",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/monster/EntityZombie;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y0(EntityZombie e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** поиск решёток/кроватей, верхняя граница */
    @Redirect(
        method = "getConversionTimeBoost",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/monster/EntityZombie;posY:D", ordinal = 1),
        require = 1,
        allow = 1)
    private double eh$y1(EntityZombie e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** звук 1016 */
    @Redirect(
        method = "onKillEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/monster/EntityZombie;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y2(EntityZombie e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }

    /** звук 1017 */
    @Redirect(
        method = "convertToVillager",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/monster/EntityZombie;posY:D", ordinal = 0),
        require = 1,
        allow = 1)
    private double eh$y3(EntityZombie e) {
        return EhMath.yForIntCast(e.worldObj, e.posY);
    }
}
