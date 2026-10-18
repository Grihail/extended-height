package io.github.extendedheight.api;

/** Реализовать на WorldProvider, чтобы включить расширенную высоту измерения; действует только при создании */
public interface IExtendedHeightProvider {

    /** @return нужный диапазон или null, чтобы оставить измерение ванильным */
    HeightRange getExtendedHeightRange();
}
