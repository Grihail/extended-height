package io.github.extendedheight.mixins.early;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IHeightTableHolder;

/** Хранит таблицу «измерение → диапазон» в level.dat; пустую не пишет */
@Mixin(WorldInfo.class)
public abstract class MixinWorldInfo implements IHeightTableHolder {

    @Unique
    private final Map<Integer, HeightRange> eh$heightTable = new LinkedHashMap<>();

    @Override
    public Map<Integer, HeightRange> eh$getHeightTable() {
        return eh$heightTable;
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"))
    private void eh$readTable(NBTTagCompound nbt, CallbackInfo ci) {
        if (!nbt.hasKey(NBT_KEY, 9)) {
            return;
        }
        NBTTagList list = nbt.getTagList(NBT_KEY, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int dim = entry.getInteger("Dim");
            int minY = entry.getInteger("MinY");
            int maxY = entry.getInteger("MaxY");
            String error = HeightRange.validate(minY, maxY);
            if (error != null) {
                // Не угадываем: загрузка измерения с другим диапазоном испортит его
                throw new IllegalStateException(
                    "level.dat: invalid ExtendedHeight entry for dimension " + dim
                        + " ("
                        + minY
                        + ".."
                        + maxY
                        + "): "
                        + error);
            }
            eh$heightTable.put(dim, HeightRange.of(minY, maxY));
        }
        ExtendedHeightMod.LOG.info("level.dat: extended dimensions {}", eh$heightTable);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldInfo;)V", at = @At("RETURN"))
    private void eh$copyTable(WorldInfo other, CallbackInfo ci) {
        eh$heightTable.putAll(((IHeightTableHolder) other).eh$getHeightTable());
    }

    @Inject(method = "updateTagCompound", at = @At("RETURN"))
    private void eh$writeTable(NBTTagCompound nbt, NBTTagCompound playerNbt, CallbackInfo ci) {
        if (eh$heightTable.isEmpty()) {
            return;
        }
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Integer, HeightRange> e : eh$heightTable.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Dim", e.getKey());
            entry.setInteger("MinY", e.getValue().minY);
            entry.setInteger("MaxY", e.getValue().maxY);
            list.appendTag(entry);
        }
        nbt.setTag(NBT_KEY, list);
    }
}
