package io.github.extendedheight.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeightRangeTest {

    @Test
    void targetRange() {
        HeightRange r = HeightRange.of(-256, 319);
        assertEquals(36, r.sectionCount());
        assertEquals(-16, r.minSection());
        assertEquals(19, r.maxSection());
        assertEquals(320, r.maxYExclusive());
        assertFalse(r.isVanilla());
    }

    @Test
    void vanillaIsSingleton() {
        assertSame(HeightRange.VANILLA, HeightRange.of(0, 255));
        assertEquals(16, HeightRange.VANILLA.sectionCount());
        assertEquals(0, HeightRange.VANILLA.minSection());
    }

    @Test
    void sectionIndexIsShiftedByMinSection() {
        HeightRange r = HeightRange.of(-256, 319);
        assertEquals(0, r.sectionIndex(-256));
        assertEquals(0, r.sectionIndex(-241));
        assertEquals(1, r.sectionIndex(-240));
        assertEquals(15, r.sectionIndex(-1));
        assertEquals(16, r.sectionIndex(0));
        assertEquals(31, r.sectionIndex(255));
        assertEquals(35, r.sectionIndex(319));
        // Вне диапазона индексы выходят за 0..35 — миксин чанка считает их «нет секции»
        assertEquals(-1, r.sectionIndex(-257));
        assertEquals(36, r.sectionIndex(320));
    }

    @Test
    void sectionIndexInVanillaIsYShift4() {
        for (int y = 0; y < 256; y++) {
            assertEquals(y >> 4, HeightRange.VANILLA.sectionIndex(y));
        }
    }

    @Test
    void everySectionMapsToExactlyOneIndex() {
        HeightRange r = HeightRange.of(-512, 511);
        boolean[] seen = new boolean[r.sectionCount()];
        for (int y = r.minY; y <= r.maxY; y += 16) {
            int i = r.sectionIndex(y);
            assertFalse(seen[i]);
            seen[i] = true;
            assertEquals(i, r.sectionIndex(y + 15));
        }
        for (boolean b : seen) assertTrue(b);
    }

    @Test
    void savedSectionYByteRoundTrips() {
        // AnvilChunkLoader пишет (byte)(yBase >> 4 & 255) и читает знаковым getByte
        HeightRange r = HeightRange.of(-512, 511);
        for (int section = r.minSection(); section <= r.maxSection(); section++) {
            int yBase = section << 4;
            byte written = (byte) (yBase >> 4 & 255);
            assertEquals(section, (int) written);
        }
    }

    @Test
    void validation() {
        assertNull(HeightRange.validate(-256, 319));
        assertNull(HeightRange.validate(0, 255));
        assertNull(HeightRange.validate(-512, 511));
        assertNotNull(HeightRange.validate(-250, 319)); // не кратно 16
        assertNotNull(HeightRange.validate(-256, 320)); // maxY + 1 не кратно 16
        assertNotNull(HeightRange.validate(16, 319)); // minY > 0
        assertNotNull(HeightRange.validate(-256, 127)); // maxY < 255
        assertNotNull(HeightRange.validate(-528, 319)); // minY < -512
        assertNotNull(HeightRange.validate(-512, 527)); // 65 секций
        assertThrows(IllegalArgumentException.class, () -> HeightRange.of(-1, 255));
    }

    @Test
    void precipitationSentinelCannotCollide() {
        // precipitationHeightMap: -999 — «не посчитано»; реальное значение всегда >= minY >= -512
        assertTrue(HeightRange.MIN_ALLOWED_MIN_Y > -999);
    }
}
