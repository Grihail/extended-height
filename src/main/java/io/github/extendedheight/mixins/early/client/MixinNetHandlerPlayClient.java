package io.github.extendedheight.mixins.early.client;

import java.util.Collections;
import java.util.Map;

import net.minecraft.client.network.NetHandlerPlayClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IClientHeightTable;

/** Сохраняет таблицу высот этого соединения ({@link IClientHeightTable}) */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient implements IClientHeightTable {

    @Unique
    private volatile Map<Integer, HeightRange> eh$heightTable = Collections.emptyMap();

    @Override
    public Map<Integer, HeightRange> eh$getHeightTable() {
        return eh$heightTable;
    }

    @Override
    public void eh$setHeightTable(Map<Integer, HeightRange> table) {
        eh$heightTable = table;
    }
}
