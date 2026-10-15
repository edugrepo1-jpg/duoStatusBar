package io.github.kvmy666.duostatusbar.fx

import org.junit.Assert.*
import org.junit.Test

class RuntimePrivacyPairTest {
    @Test fun `camera mic from known different owners are not combined into a video call`() {
        assertFalse(RuntimePrivacyPair.active(true,true,true,true,false,setOf("camera.owner"),setOf("audio.owner")))
    }
    @Test fun `shared owner needs both privacy states and a communication session`() {
        assertTrue(RuntimePrivacyPair.active(true,true,true,true,true,setOf("owner"),setOf("owner")))
        assertFalse(RuntimePrivacyPair.active(true,false,true,true,true,setOf("owner"),setOf("owner")))
        assertFalse(RuntimePrivacyPair.active(true,true,true,false,true,setOf("owner"),emptySet()))
        assertFalse(RuntimePrivacyPair.active(false,true,true,true,true,setOf("owner"),setOf("owner")))
    }
    @Test fun `unavailable origin supports communication pair without claiming an owner`() {
        assertTrue(RuntimePrivacyPair.active(true,true,true,true,false,emptySet(),setOf("owner")))
        assertTrue(RuntimePrivacyPair.active(true,true,true,true,false,emptySet(),emptySet()))
    }
}
