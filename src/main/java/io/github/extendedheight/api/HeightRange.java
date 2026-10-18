package io.github.extendedheight.api;

/** Неизменяемый диапазон высот: minY кратно 16 в -512..0, maxY + 1 кратно 16, maxY >= 255, до 64 секций */
public final class HeightRange {

    public static final int MIN_ALLOWED_MIN_Y = -512;
    public static final int MAX_SECTIONS = 64;

    public static final HeightRange VANILLA = new HeightRange(0, 255);

    public final int minY;
    public final int maxY;
    private final int minSection;
    private final int sectionCount;

    private HeightRange(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
        this.minSection = minY >> 4;
        this.sectionCount = (maxY + 1 - minY) >> 4;
    }

    public static HeightRange of(int minY, int maxY) {
        String error = validate(minY, maxY);
        if (error != null) {
            throw new IllegalArgumentException("Invalid height range " + minY + ".." + maxY + ": " + error);
        }
        if (minY == 0 && maxY == 255) {
            return VANILLA;
        }
        return new HeightRange(minY, maxY);
    }

    /** @return null, если диапазон допустим, иначе причина */
    public static String validate(int minY, int maxY) {
        if ((minY & 15) != 0) return "minY must be a multiple of 16";
        if (((maxY + 1) & 15) != 0) return "maxY + 1 must be a multiple of 16";
        if (minY > 0) return "minY must be <= 0";
        if (maxY < 255) return "maxY must be >= 255";
        if (minY < MIN_ALLOWED_MIN_Y) return "minY must be >= " + MIN_ALLOWED_MIN_Y;
        if (((maxY + 1 - minY) >> 4) > MAX_SECTIONS) return "at most " + MAX_SECTIONS + " sections are supported";
        return null;
    }

    public boolean isVanilla() {
        return this == VANILLA;
    }

    /** Y нижней секции (y >> 4) */
    public int minSection() {
        return minSection;
    }

    /** Y верхней секции */
    public int maxSection() {
        return minSection + sectionCount - 1;
    }

    public int sectionCount() {
        return sectionCount;
    }

    /** maxY + 1 — «высота мира» в ванильном смысле */
    public int maxYExclusive() {
        return maxY + 1;
    }

    public boolean contains(int y) {
        return y >= minY && y <= maxY;
    }

    /** Индекс в Chunk.storageArrays для y; вне диапазона может выйти за границы */
    public int sectionIndex(int y) {
        return (y >> 4) - minSection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeightRange)) return false;
        HeightRange other = (HeightRange) o;
        return minY == other.minY && maxY == other.maxY;
    }

    @Override
    public int hashCode() {
        return 31 * minY + maxY;
    }

    @Override
    public String toString() {
        return minY + ".." + maxY;
    }
}
