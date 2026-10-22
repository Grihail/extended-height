package io.github.extendedheight.core;

/** Ограничение дальности ванильного рендера в расширенных мирах до 16; настройка игрока не меняется */
public final class RenderDistanceLimit {

    /** Наибольшая дальность (в чанках), под которую выделен ванильный рендер в расширенном мире */
    public static final int MAX_EXTENDED = 16;

    private RenderDistanceLimit() {}

    /** @return дальность: как есть в ванильных мирах, не больше {@link #MAX_EXTENDED} в расширенных */
    public static int clamp(boolean extendedWorld, int distance) {
        return extendedWorld ? Math.min(distance, MAX_EXTENDED) : distance;
    }
}
