package io.github.extendedheight.core;

import java.util.Map;

import io.github.extendedheight.api.HeightRange;

/** Duck-интерфейс WorldInfo: таблица «id измерения → диапазон» в level.dat под {@value #NBT_KEY} */
public interface IHeightTableHolder {

    String NBT_KEY = "ExtendedHeight";

    /** Изменяемая, в порядке вставки; хранит только расширенные измерения */
    Map<Integer, HeightRange> eh$getHeightTable();
}
