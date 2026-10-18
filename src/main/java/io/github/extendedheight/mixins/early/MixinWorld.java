package io.github.extendedheight.mixins.early;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IExtendedHeightWorld;
import io.github.extendedheight.core.RangeResolver;

/** API мира: жёсткие границы y 0/256/255 заменены диапазоном мира (docs/patch-map.md, раздел 2) */
@Mixin(World.class)
public abstract class MixinWorld implements IExtendedHeightWorld {

    @Unique
    private HeightRange eh$range = HeightRange.VANILLA;
    @Unique
    private int eh$minY = 0;
    @Unique
    private int eh$maxY = 255;
    @Unique
    private int eh$maxYExcl = 256;

    @Override
    public HeightRange eh$getRange() {
        return eh$range;
    }

    @Override
    public void eh$setRange(HeightRange range) {
        eh$range = range;
        eh$minY = range.minY;
        eh$maxY = range.maxY;
        eh$maxYExcl = range.maxYExclusive();
    }

    /** Задаём диапазон до создания провайдера чанков; полный дескриптор конструктора — ради refmap */
    @Inject(
        method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/WorldProvider;registerWorld(Lnet/minecraft/world/World;)V",
            shift = At.Shift.AFTER),
        require = 1,
        allow = 1)
    private void eh$resolveRange(CallbackInfo ci) {
        eh$setRange(RangeResolver.resolve((World) (Object) this));
    }

    // ---- проверки y >= 0 / y < 0 (первое сравнение с нулём) ----

    @ModifyConstant(
        method = { "getBlock", "blockExists", "checkChunksExist", "getTileEntity" },
        constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
        require = 4,
        allow = 4)
    private int eh$minYCheckFirst(int zero) {
        return eh$minY;
    }

    @ModifyConstant(
        method = { "setBlock(IIILnet/minecraft/block/Block;II)Z", "getBlockMetadata", "setBlockMetadataWithNotify",
            "setLightValue" },
        constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.LESS_THAN_ZERO),
        require = 4,
        allow = 4)
    private int eh$minYCheckFirstLt(int zero) {
        return eh$minY;
    }

    /** if (y < 0) return 0; if (y >= 256) y = 255; — только сравнение, возвращаемый 0 остаётся */
    @ModifyConstant(
        method = "getFullBlockLightValue",
        constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.LESS_THAN_ZERO),
        require = 1,
        allow = 1)
    private int eh$minYFullLight(int zero) {
        return eh$minY;
    }

    /** if (y < 0) y = 0; — и сравнение, и значение */
    @ModifyConstant(
        method = "getSavedLightValue",
        constant = { @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.LESS_THAN_ZERO),
            @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.LESS_THAN_ZERO) },
        require = 2,
        allow = 2)
    private int eh$minYClampSavedLight(int zero) {
        return eh$minY;
    }

    /** Проверке y < 0 предшествуют пять рекурсивных вызовов с false (ICONST_0) */
    @ModifyConstant(
        method = "getBlockLightValue_do",
        constant = @Constant(intValue = 0, ordinal = 5, expandZeroConditions = Condition.LESS_THAN_ZERO),
        require = 1,
        allow = 1)
    private int eh$minYLightDo(int zero) {
        return eh$minY;
    }

    /** Перед y >= 0 стоит if (f > 0.15F) return false; (ICONST_0) */
    @ModifyConstant(
        method = { "canBlockFreezeBody", "canSnowAtBody" },
        constant = @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
        remap = false,
        require = 2,
        allow = 2)
    private int eh$minYWeather(int zero) {
        return eh$minY;
    }

    /** Перед if (y < 0 || y >= 256) стоит if (x вне границ) return 0; */
    @ModifyConstant(
        method = "getBlockLightOpacity",
        constant = @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.LESS_THAN_ZERO),
        remap = false,
        require = 1,
        allow = 1)
    private int eh$minYOpacity(int zero) {
        return eh$minY;
    }

    // ---- проверки y < 256 / y >= 256 и зажимы 255 ----

    @ModifyConstant(
        method = { "getBlock", "blockExists", "checkChunksExist", "setBlock(IIILnet/minecraft/block/Block;II)Z",
            "getBlockMetadata", "setBlockMetadataWithNotify", "getFullBlockLightValue", "getBlockLightValue_do",
            "getSavedLightValue", "setLightValue", "getTileEntity" },
        constant = @Constant(intValue = 256),
        require = 11,
        allow = 11)
    private int eh$maxYExcl(int value) {
        return eh$maxYExcl;
    }

    /** Методы, добавленные Forge, не обфусцированы */
    @ModifyConstant(
        method = { "getBlockLightOpacity", "canBlockFreezeBody", "canSnowAtBody" },
        constant = @Constant(intValue = 256),
        remap = false,
        require = 3,
        allow = 3)
    private int eh$maxYExclOpacity(int value) {
        return eh$maxYExcl;
    }

    @ModifyConstant(
        method = { "getFullBlockLightValue", "getBlockLightValue_do", "getSavedLightValue" },
        constant = @Constant(intValue = 255),
        require = 3,
        allow = 3)
    private int eh$maxYClamp(int value) {
        return eh$maxY;
    }

    // ---- сканы столбцов и метки «ничего не найдено» ----

    /** for (k = top + 15; k > 0; --k) и return -1 */
    @ModifyConstant(
        method = "getTopSolidOrLiquidBlock",
        constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.GREATER_THAN_ZERO),
        require = 1,
        allow = 1)
    private int eh$topSolidLoop(int zero) {
        return eh$minY;
    }

    @ModifyConstant(method = "getTopSolidOrLiquidBlock", constant = @Constant(intValue = -1), require = 1, allow = 1)
    private int eh$topSolidNone(int value) {
        return eh$minY - 1;
    }

    /** Значение для незагруженных чанков («блоков нет») */
    @ModifyConstant(
        method = { "getHeightValue", "getChunkHeightMapMinimum" },
        constant = @Constant(intValue = 0),
        require = 2,
        allow = 2)
    private int eh$heightNoChunk(int zero) {
        return eh$minY;
    }
}
