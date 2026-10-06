package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.*
import org.junit.Test

class CanvasMotionTest {
    @Test fun blendPreservesEndpointsIncludingAlpha() {
        assertEquals(0x12345678, CanvasMotion.blend(0x12345678, 0x7fabcdef, 0f))
        assertEquals(0x7fabcdef, CanvasMotion.blend(0x12345678, 0x7fabcdef, 1f))
    }
    @Test fun midpointUsesGammaCorrectedRgb() {
        val result = CanvasMotion.blend(0xff000000.toInt(), 0xffffffff.toInt(), .5f)
        assertEquals(186, result and 255)
        assertEquals(255, result ushr 24)
    }
    @Test fun interpolationClampsExtrapolation() {
        assertEquals(12, CanvasMotion.blend(12, 98, -1f))
        assertEquals(98, CanvasMotion.blend(12, 98, 2f))
    }
    @Test fun arrivalStartsAndEndsAtItsEndpoints() {
        assertEquals(0f, CanvasMotion.overshoot(0f), .00001f)
        assertEquals(1f, CanvasMotion.overshoot(1f), .00001f)
        assertTrue(CanvasMotion.overshoot(.7f) > 1f)
    }
    @Test fun exponentialStepDoesNotDependOnFramePartition() {
        val one = CanvasMotion.step(32, 130f)
        val half = CanvasMotion.step(16, 130f)
        assertEquals(one, 1f - (1f - half) * (1f - half), .00001f)
        assertEquals(0f, CanvasMotion.step(-1, 130f), 0f)
    }
    @Test fun fillDurationIsProportionalAndBounded() {
        assertEquals(200L, CanvasMotion.fillDuration(1, 1000))
        assertEquals(1200L, CanvasMotion.fillDuration(50, 1000))
        assertEquals(2400L, CanvasMotion.fillDuration(100, 1000))
        assertEquals(2400L, CanvasMotion.fillDuration(-200, 1000))
    }
}
