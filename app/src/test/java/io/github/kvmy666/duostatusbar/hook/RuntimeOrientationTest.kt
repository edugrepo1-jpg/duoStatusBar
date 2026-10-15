package io.github.kvmy666.duostatusbar.hook

import android.content.res.Configuration
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeOrientationTest {
    @Test fun `lagging resource callbacks cannot temporarily disable an enabled landscape profile`() {
        val portrait=Configuration.ORIENTATION_PORTRAIT
        val landscape=Configuration.ORIENTATION_LANDSCAPE
        // Settings may intentionally differ: portrait OFF, landscape ON. The visible screen must
        // cause exactly one enable and one disable, regardless of repeated stale layout callbacks.
        var applied=portrait
        val lifecycle=mutableListOf<Boolean>()
        fun callback(rotation:Int?, configuration:Int) {
            val next=RuntimeOrientation.current(rotation,configuration)
            if(next!=applied) {applied=next;lifecycle.add(next==landscape)}
        }
        callback(Surface.ROTATION_90,portrait)
        repeat(20) {callback(Surface.ROTATION_90,portrait)}
        callback(Surface.ROTATION_90,landscape)
        assertEquals(landscape,applied)
        assertEquals(listOf(true),lifecycle)
        callback(Surface.ROTATION_0,landscape)
        repeat(20) {callback(Surface.ROTATION_0,landscape)}
        callback(Surface.ROTATION_0,portrait)
        assertEquals(portrait,applied)
        assertEquals(listOf(true,false),lifecycle)
    }

    @Test fun `reverse rotations and missing display use the same profile selection rule`() {
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,RuntimeOrientation.current(Surface.ROTATION_270,Configuration.ORIENTATION_PORTRAIT))
        assertEquals(Configuration.ORIENTATION_PORTRAIT,RuntimeOrientation.current(Surface.ROTATION_180,Configuration.ORIENTATION_LANDSCAPE))
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,RuntimeOrientation.current(null,Configuration.ORIENTATION_LANDSCAPE))
        assertEquals(Configuration.ORIENTATION_PORTRAIT,RuntimeOrientation.current(-1,Configuration.ORIENTATION_PORTRAIT))
        assertEquals(Configuration.ORIENTATION_UNDEFINED,RuntimeOrientation.current(null,Configuration.ORIENTATION_UNDEFINED))
    }
    @Test fun `real dimensions support natural landscape tablets and cutout insets do not change orientation`() {
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,RuntimeOrientation.current(Surface.ROTATION_0,Configuration.ORIENTATION_PORTRAIT,2560,1600))
        assertEquals(Configuration.ORIENTATION_PORTRAIT,RuntimeOrientation.current(Surface.ROTATION_90,Configuration.ORIENTATION_LANDSCAPE,1600,2560))
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,RuntimeOrientation.current(Surface.ROTATION_90,Configuration.ORIENTATION_PORTRAIT,2340,1080))
        assertEquals(Configuration.ORIENTATION_PORTRAIT,RuntimeOrientation.current(Surface.ROTATION_0,Configuration.ORIENTATION_LANDSCAPE,1080,2340))
        // Invalid dimensions never produce a fabricated profile; fallbacks stay available.
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,RuntimeOrientation.current(null,Configuration.ORIENTATION_LANDSCAPE,0,0))
    }
}
