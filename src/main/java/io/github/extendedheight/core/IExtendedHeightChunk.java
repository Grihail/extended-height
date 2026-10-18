package io.github.extendedheight.core;

import io.github.extendedheight.api.HeightRange;

/** Duck-интерфейс для Chunk */
public interface IExtendedHeightChunk {

    /** Диапазон мира чанка, задан при создании; {@link HeightRange#VANILLA} для ванильных */
    HeightRange eh$getRange();
}
