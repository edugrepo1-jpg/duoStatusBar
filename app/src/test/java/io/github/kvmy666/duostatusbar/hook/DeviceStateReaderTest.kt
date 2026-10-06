package io.github.kvmy666.duostatusbar.hook

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The silent-vs-DND boundary.
 *
 * A silenced ringer is not DND: the moon inside the ring means "Do Not Disturb is on", and a phone
 * merely put on silent used to draw it anyway (user-reported). These cases pin the decision off the
 * phone so the conflation cannot come back.
 */
class DeviceStateReaderTest {

    @Test
    fun `no filter reads as DND off, never on`() {
        assertFalse(DeviceStateReader.interruptionFilterIsDnd(null))
    }

    @Test
    fun `INTERRUPTION_FILTER_ALL is DND off`() {
        assertFalse(DeviceStateReader.interruptionFilterIsDnd(NotificationManager.INTERRUPTION_FILTER_ALL))
    }

    @Test
    fun `every active zen filter reads as DND on`() {
        assertTrue(DeviceStateReader.interruptionFilterIsDnd(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertTrue(DeviceStateReader.interruptionFilterIsDnd(NotificationManager.INTERRUPTION_FILTER_ALARMS))
        assertTrue(DeviceStateReader.interruptionFilterIsDnd(NotificationManager.INTERRUPTION_FILTER_NONE))
    }
}
