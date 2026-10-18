package io.github.extendedheight.selftest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Эталон света перебором: наименьшая неподвижная точка правила World.computeLightValue */
public final class LightReference {

    public interface World {

        int opacity(int x, int y, int z);

        int emission(int x, int y, int z);

        /** y >= heightMap(x, z) */
        boolean seesSky(int x, int y, int z);
    }

    private static final int[][] NEIGHBOURS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    private LightReference() {}

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** @return key(x, y, z) -> значение света для каждой клетки cells */
    public static Map<Long, Integer> compute(List<int[]> cells, World world, boolean sky) {
        Map<Long, Integer> values = new HashMap<>();
        for (int[] c : cells) {
            values.put(key(c[0], c[1], c[2]), 0);
        }
        boolean changed = true;
        int passes = 0;
        while (changed) {
            if (++passes > 10_000) {
                throw new IllegalStateException("light reference did not converge");
            }
            changed = false;
            for (int[] c : cells) {
                long k = key(c[0], c[1], c[2]);
                int v = value(c[0], c[1], c[2], values, world, sky);
                if (v != values.get(k)) {
                    values.put(k, v);
                    changed = true;
                }
            }
        }
        return values;
    }

    private static int value(int x, int y, int z, Map<Long, Integer> values, World world, boolean sky) {
        if (sky && world.seesSky(x, y, z)) {
            return 15;
        }
        int emission = world.emission(x, y, z);
        int own = sky ? 0 : emission;
        int opacity = world.opacity(x, y, z);
        if (opacity >= 15 && emission > 0) {
            opacity = 1;
        }
        if (opacity < 1) {
            opacity = 1;
        }
        if (opacity >= 15) {
            return 0;
        }
        if (own >= 14) {
            return own;
        }
        int best = own;
        for (int[] d : NEIGHBOURS) {
            Integer n = values.get(key(x + d[0], y + d[1], z + d[2]));
            if (n != null && n - opacity > best) {
                best = n - opacity;
            }
        }
        return best;
    }
}
