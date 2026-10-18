package io.github.extendedheight.mixins.early;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Constant.Condition;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IExtendedHeightChunk;

/** Хранилище чанка: слот на секцию диапазона, индекс y >> 4 сдвинут на нижнюю секцию; у ванили сдвиг 0 */
@Mixin(Chunk.class)
public abstract class MixinChunk implements IExtendedHeightChunk {

    private static final String STORAGE = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";

    @Shadow
    private ExtendedBlockStorage[] storageArrays;
    @Shadow
    public List[] entityLists;
    @Shadow
    public World worldObj;
    @Shadow
    public int[] heightMap;

    @Unique
    private int eh$minSection = 0;
    @Unique
    private int eh$minY = 0;
    @Unique
    private int eh$maxY = 255;
    @Unique
    private HeightRange eh$range = HeightRange.VANILLA;
    @Shadow
    private int queuedLightChecks;

    @Override
    public HeightRange eh$getRange() {
        return eh$range;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"), require = 1, allow = 1)
    private void eh$sizeStorage(World world, int x, int z, CallbackInfo ci) {
        if (world == null) {
            return;
        }
        HeightRange range = ExtendedHeight.getRange(world);
        if (range.isVanilla()) {
            return;
        }
        eh$range = range;
        // «Нет блока в столбце» — minY (ваниль 0); «проверки света готовы» — секции * 256 (ваниль 4096)
        Arrays.fill(heightMap, range.minY);
        queuedLightChecks = range.sectionCount() * 256;
        eh$minSection = range.minSection();
        eh$minY = range.minY;
        eh$maxY = range.maxY;
        int sections = range.sectionCount();
        storageArrays = new ExtendedBlockStorage[sections];
        entityLists = new List[sections];
        for (int i = 0; i < sections; i++) {
            entityLists[i] = new ArrayList<>();
        }
    }

    // ---- storageArrays[y >> 4] -> [(y >> 4) - minSection]; require — точное число обращений ----

    /** Чтения storageArrays: всего 17 по перечисленным методам */
    @Redirect(
        method = { "<init>(Lnet/minecraft/world/World;[Lnet/minecraft/block/Block;II)V",
            "<init>(Lnet/minecraft/world/World;[Lnet/minecraft/block/Block;[BII)V", "getBlock", "getBlockMetadata",
            "func_150807_a", "setBlockMetadata", "getSavedLightValue", "setLightValue", "getBlockLightValue",
            "generateSkylightMap", "relightBlock", "getAreLevelsEmpty" },
        at = @At(value = "FIELD", target = STORAGE, args = { "array=get", "fuzz=64" }),
        require = 17,
        allow = 17)
    private ExtendedBlockStorage eh$getSection(ExtendedBlockStorage[] array, int sectionY) {
        int index = sectionY - eh$minSection;
        return index >= 0 && index < array.length ? array[index] : null;
    }

    /** Записи в двух конструкторах Block[] (storageArrays[k] = new ...) */
    @Redirect(
        method = { "<init>(Lnet/minecraft/world/World;[Lnet/minecraft/block/Block;II)V",
            "<init>(Lnet/minecraft/world/World;[Lnet/minecraft/block/Block;[BII)V" },
        at = @At(value = "FIELD", target = STORAGE, args = { "array=set", "fuzz=64" }),
        require = 2,
        allow = 2)
    private void eh$setSection(ExtendedBlockStorage[] array, int sectionY, ExtendedBlockStorage value) {
        int index = sectionY - eh$minSection;
        if (index >= 0 && index < array.length) {
            array[index] = value;
        }
    }

    /* Недостающую секцию расширенного чанка создаём заранее: redirect и @ModifyVariable тут ломаются */

    /** Отложенное значение локальной flag в func_150807_a («новая секция выше карты высот») */
    @Unique
    private boolean eh$newSectionAboveHeightMap;

    @Inject(method = "func_150807_a", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$prepareSection(int x, int y, int z, Block block, int meta, CallbackInfoReturnable<Boolean> cir) {
        eh$newSectionAboveHeightMap = false;
        if (eh$minSection == 0 && storageArrays.length == 16) {
            return;
        }
        int index = (y >> 4) - eh$minSection;
        if (index < 0 || index >= storageArrays.length) {
            cir.setReturnValue(false);
            return;
        }
        // Ваниль создаёт секцию только для не-воздуха, и тогда flag = y >= heightMap
        if (storageArrays[index] == null && block != Blocks.air) {
            storageArrays[index] = new ExtendedBlockStorage(y >> 4 << 4, !worldObj.provider.hasNoSky);
            eh$newSectionAboveHeightMap = y >= heightMap[z << 4 | x];
        }
    }

    /** boolean flag = false; — второй ICONST_0 метода */
    @ModifyConstant(method = "func_150807_a", constant = @Constant(intValue = 0, ordinal = 1), require = 1, allow = 1)
    private int eh$sectionFlag(int zero) {
        boolean flag = eh$newSectionAboveHeightMap;
        eh$newSectionAboveHeightMap = false;
        return flag ? 1 : 0;
    }

    @Inject(method = "setLightValue", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$prepareLightSection(EnumSkyBlock type, int x, int y, int z, int value, CallbackInfo ci) {
        if (eh$minSection == 0 && storageArrays.length == 16) {
            return;
        }
        int index = (y >> 4) - eh$minSection;
        if (index < 0 || index >= storageArrays.length) {
            ci.cancel();
            return;
        }
        // Как заменяемая ванильная ветка: создать секцию, затем пересчитать карту неба
        if (storageArrays[index] == null) {
            storageArrays[index] = new ExtendedBlockStorage(y >> 4 << 4, !worldObj.provider.hasNoSky);
            ((Chunk) (Object) this).generateSkylightMap();
        }
    }

    /** y >> 4 < storageArrays.length: сравнение с верхней секцией + 1 */
    @Redirect(
        method = { "getBlock", "getBlockMetadata" },
        at = @At(value = "FIELD", target = STORAGE, args = "array=length"),
        require = 2,
        allow = 2)
    private int eh$sectionLimit(ExtendedBlockStorage[] array) {
        return array.length + eh$minSection;
    }

    /** Пустой чанк: нижний y вместо 0 */
    @ModifyConstant(method = "getTopFilledSegment", constant = @Constant(intValue = 0), require = 1, allow = 1)
    private int eh$emptyTopSegment(int zero) {
        return eh$minY;
    }

    /** if (min < 0) min = 0; if (max >= 256) max = 255; */
    @ModifyConstant(
        method = "getAreLevelsEmpty",
        constant = { @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.LESS_THAN_ZERO),
            @Constant(intValue = 0, ordinal = 1, expandZeroConditions = Condition.LESS_THAN_ZERO) },
        require = 2,
        allow = 2)
    private int eh$levelsMin(int zero) {
        return eh$minY;
    }

    @ModifyConstant(method = "getAreLevelsEmpty", constant = @Constant(intValue = 256), require = 1, allow = 1)
    private int eh$levelsMaxExcl(int value) {
        return eh$maxY + 1;
    }

    @ModifyConstant(method = "getAreLevelsEmpty", constant = @Constant(intValue = 255), require = 1, allow = 1)
    private int eh$levelsMax(int value) {
        return eh$maxY;
    }

    /** while (y > 0 && l == -1): скан до minY; «нет блока» — minY (ваниль -1) */
    @ModifyConstant(
        method = "getPrecipitationHeight",
        constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Condition.GREATER_THAN_ZERO),
        require = 1,
        allow = 1)
    private int eh$precipitationLoop(int zero) {
        return eh$minY;
    }

    /** l = -1 и l == -1 заменены одним значением, логика цикла та же */
    @ModifyConstant(method = "getPrecipitationHeight", constant = @Constant(intValue = -1), require = 2, allow = 2)
    private int eh$precipitationNone(int value) {
        return eh$minSection == 0 ? -1 : eh$minY;
    }
}
