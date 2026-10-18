package io.github.extendedheight.core;

import io.github.extendedheight.api.HeightRange;

/** Duck-интерфейс для World */
public interface IExtendedHeightWorld {

    HeightRange eh$getRange();

    void eh$setRange(HeightRange range);
}
