package io.github.extendedheight.selftest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Эталон должен быть верен, прежде чем судить игру */
class LightReferenceTest {

    /** Прямой воздушный коридор вдоль x с источником 15 в x = 0: минус один за шаг */
    @Test
    void corridorDecaysByOne() {
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < 20; x++) cells.add(new int[] { x, -200, 0 });
        LightReference.World w = world((x, y, z) -> x == 0 ? 15 : 0, (x, y, z) -> 0, (x, y, z) -> false);
        Map<Long, Integer> v = LightReference.compute(cells, w, false);
        for (int x = 0; x < 20; x++) {
            assertEquals(Math.max(0, 15 - x), (int) v.get(LightReference.key(x, -200, 0)), "x=" + x);
        }
    }

    /** Непрозрачный источник (светокамень) всё равно светит соседям */
    @Test
    void opaqueEmitterLights() {
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < 3; x++) cells.add(new int[] { x, 0, 0 });
        LightReference.World w = world((x, y, z) -> x == 1 ? 15 : 0, (x, y, z) -> x == 1 ? 255 : 0, (x, y, z) -> false);
        Map<Long, Integer> v = LightReference.compute(cells, w, false);
        assertEquals(14, (int) v.get(LightReference.key(0, 0, 0)));
        assertEquals(15, (int) v.get(LightReference.key(1, 0, 0)));
    }

    /** Небо: клетка под небом — 15, накрытый сосед — 14, непрозрачная — 0 */
    @Test
    void skyAndOpaque() {
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < 4; x++) cells.add(new int[] { x, 300, 0 });
        LightReference.World w = world((x, y, z) -> 0, (x, y, z) -> x == 3 ? 255 : 0, (x, y, z) -> x == 0);
        Map<Long, Integer> v = LightReference.compute(cells, w, true);
        assertEquals(15, (int) v.get(LightReference.key(0, 300, 0)));
        assertEquals(14, (int) v.get(LightReference.key(1, 300, 0)));
        assertEquals(13, (int) v.get(LightReference.key(2, 300, 0)));
        assertEquals(0, (int) v.get(LightReference.key(3, 300, 0)));
    }

    /** Полупрозрачный блок (непрозрачность 3, как вода) стоит 3, а не 1 */
    @Test
    void opacityCost() {
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < 3; x++) cells.add(new int[] { x, 0, 0 });
        LightReference.World w = world((x, y, z) -> x == 0 ? 15 : 0, (x, y, z) -> x == 1 ? 3 : 0, (x, y, z) -> false);
        Map<Long, Integer> v = LightReference.compute(cells, w, false);
        assertEquals(12, (int) v.get(LightReference.key(1, 0, 0)));
        assertEquals(11, (int) v.get(LightReference.key(2, 0, 0)));
    }

    @Test
    void keysDistinguishNegativeAndLargeY() {
        assertEquals(false, LightReference.key(0, -1, 0) == LightReference.key(0, 255, 0));
        assertEquals(false, LightReference.key(0, -256, 0) == LightReference.key(0, 0, 0));
        assertEquals(false, LightReference.key(-1, 0, 0) == LightReference.key(0, 0, -1));
    }

    interface F {

        int at(int x, int y, int z);
    }

    interface B {

        boolean at(int x, int y, int z);
    }

    private static LightReference.World world(F emission, F opacity, B sky) {
        return new LightReference.World() {

            public int opacity(int x, int y, int z) {
                return opacity.at(x, y, z);
            }

            public int emission(int x, int y, int z) {
                return emission.at(x, y, z);
            }

            public boolean seesSky(int x, int y, int z) {
                return sky.at(x, y, z);
            }
        };
    }
}
