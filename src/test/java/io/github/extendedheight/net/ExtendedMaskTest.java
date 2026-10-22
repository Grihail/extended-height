package io.github.extendedheight.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExtendedMaskTest {

    @Test
    void unloadPacketStaysVanilla() {
        assertEquals(0L, ExtPackets.extendedMask(true, 0));
        assertEquals(0L, ExtPackets.extendedMask(false, 0));
    }

    @Test
    void wholeVanillaChunkMeansEverySection() {
        assertEquals(-1L, ExtPackets.extendedMask(true, 65535));
    }

    @Test
    void otherMasksKeepTheirBits() {
        assertEquals(0b101L, ExtPackets.extendedMask(false, 0b101));
        assertEquals(0xFFFFL, ExtPackets.extendedMask(false, 65535));
        assertEquals(0b1001L, ExtPackets.extendedMask(true, 0b1001));
    }
}
