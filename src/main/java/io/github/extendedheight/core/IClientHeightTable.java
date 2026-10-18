package io.github.extendedheight.core;

import java.util.Map;

import io.github.extendedheight.api.HeightRange;

/** Duck-интерфейс NetHandlerPlayClient: таблица «измерение → диапазон» этого соединения */
public interface IClientHeightTable {

    Map<Integer, HeightRange> eh$getHeightTable();

    void eh$setHeightTable(Map<Integer, HeightRange> table);
}
