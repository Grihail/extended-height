package io.github.extendedheight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RenderDistanceLimitTest {

    @Test
    void vanillaWorldKeepsTheSetting() {
        assertEquals(32, RenderDistanceLimit.clamp(false, 32));
        assertEquals(2, RenderDistanceLimit.clamp(false, 2));
    }

    @Test
    void extendedWorldIsLimitedTo16() {
        assertEquals(16, RenderDistanceLimit.clamp(true, 32));
        assertEquals(16, RenderDistanceLimit.clamp(true, 17));
        assertEquals(16, RenderDistanceLimit.clamp(true, 16));
        assertEquals(8, RenderDistanceLimit.clamp(true, 8));
    }
}
