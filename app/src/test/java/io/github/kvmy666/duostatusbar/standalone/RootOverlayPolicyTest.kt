package io.github.kvmy666.duostatusbar.standalone

import org.junit.Assert.*
import org.junit.Test

class RootOverlayPolicyTest {
    private val generation="01234567-89ab-cdef-0123-456789abcdef"
    @Test fun `root and current explicit generation are all required`() {
        assertTrue(RootOverlayPolicy.authorized(0,generation,"root",generation))
        assertFalse(RootOverlayPolicy.authorized(1000,generation,"root",generation))
        assertFalse(RootOverlayPolicy.authorized(2000,generation,"root",generation))
        assertFalse(RootOverlayPolicy.authorized(0,generation,"module",generation))
        assertFalse(RootOverlayPolicy.authorized(0,generation,"root",generation+"a"))
        assertFalse(RootOverlayPolicy.authorized(0,generation,null,null))
    }
    @Test fun `helper arguments reject shell syntax and missing nonce`() {
        assertFalse(RootOverlayPolicy.validGeneration(""))
        assertFalse(RootOverlayPolicy.validGeneration("short"))
        assertFalse(RootOverlayPolicy.validGeneration("0123456789012345;id"))
        assertFalse(RootOverlayPolicy.validGeneration("0123456789012345\n"))
        assertFalse(RootOverlayPolicy.validGeneration("0123456789012345\""))
        assertFalse(RootOverlayPolicy.validGeneration("0123456789012345/"))
        assertFalse(RootOverlayPolicy.validGeneration("a".repeat(129)))
    }
    @Test fun `size changes remain effective and do not move the ring to screen center`() {
        val normal=RootOverlayPolicy.box(1080,2400,3f,96,100,0,false,0)
        val large=RootOverlayPolicy.box(1080,2400,3f,96,165,0,false,0)
        assertTrue(large.side>normal.side)
        assertTrue(large.top+large.height<=192)
        assertTrue(large.right>=0)
        assertTrue(large.right+large.width<=1080)
    }
    @Test fun `rotated split layout and extreme saved offsets remain within display`() {
        for((width,height) in listOf(1080 to 2400,2400 to 1080,1 to 1))
            for(density in listOf(3f,Float.NaN,Float.POSITIVE_INFINITY,-1f))
                for(offset in listOf(Int.MIN_VALUE,0,Int.MAX_VALUE)) {
                    val box=RootOverlayPolicy.box(width,height,density,96,300,offset,true,offset)
                    assertTrue(box.width in 1..width);assertTrue(box.height in 1..height)
                    assertTrue(box.right>=0);assertTrue(box.top>=0)
                    assertTrue(box.width+box.right<=width);assertTrue(box.height+box.top<=height)
                }
    }
}
