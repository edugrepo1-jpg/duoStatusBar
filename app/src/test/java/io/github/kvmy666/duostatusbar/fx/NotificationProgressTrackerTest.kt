package io.github.kvmy666.duostatusbar.fx

import org.junit.Assert.*
import org.junit.Test

class NotificationProgressTrackerTest {
    @Test fun `initial completed notification does not create an invented completion event`() {
        val tracker=NotificationProgressTracker()
        assertFalse(tracker.observe("task",100,100,true,seed=true))
        assertFalse(tracker.observe("task",100,100,true))
    }
    @Test fun `only observed incomplete to completed transition emits once per task cycle`() {
        val tracker=NotificationProgressTracker()
        assertFalse(tracker.observe("task",5,100,true))
        assertFalse(tracker.observe("task",95,100,true))
        assertTrue(tracker.observe("task",100,100,true))
        assertFalse(tracker.observe("task",100,100,true))
        assertFalse(tracker.observe("task",0,100,true))
        assertTrue(tracker.observe("task",110,100,true))
    }
    @Test fun `dismissed incomplete task never completes and recycled key requires fresh evidence`() {
        val tracker=NotificationProgressTracker()
        tracker.observe("task",50,100,true);tracker.remove("task")
        assertEquals(0,tracker.size)
        assertFalse(tracker.observe("task",100,100,true))
    }
    @Test fun `indeterminate or missing progress breaks completion inference`() {
        val tracker=NotificationProgressTracker()
        tracker.observe("task",50,100,true)
        assertFalse(tracker.observe("task",99,100,false))
        assertFalse(tracker.observe("task",100,100,true))
        tracker.observe("task",50,100,true)
        assertFalse(tracker.observe("task",-1,100,true))
        assertFalse(tracker.observe("task",100,100,true))
        assertFalse(tracker.observe("missing",0,0,true))
    }
    @Test fun `cache cannot grow with notification storm and evicted task cannot fake completion`() {
        val tracker=NotificationProgressTracker()
        for(index in 0..999)tracker.observe("task$index",50,100,true)
        assertEquals(128,tracker.size)
        assertFalse(tracker.observe("task0",100,100,true))
        assertEquals(128,tracker.size)
        tracker.clear();assertEquals(0,tracker.size)
    }
}
