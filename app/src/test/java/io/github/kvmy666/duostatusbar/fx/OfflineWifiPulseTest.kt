package io.github.kvmy666.duostatusbar.fx

import org.junit.Assert.*
import org.junit.Test

class OfflineWifiPulseTest {
    @Test fun `compact island follows the active foreground event instead of always preferring music`() {
        val items=listOf(IslandItem(SlotIcon.WIFI,"Wi-Fi"),IslandItem(SlotIcon.MEDIA,"Música"),IslandItem(SlotIcon.RECORD,"Gravação"))
        val state=IslandState(items=items,playback=PlaybackSnapshot(playing=true),foreground=SlotIcon.RECORD)
        assertEquals(SlotIcon.RECORD,state.primaryItem()?.icon)
        assertEquals(SlotIcon.MEDIA,state.copy(foreground=SlotIcon.MEDIA).primaryItem()?.icon)
        assertEquals(SlotIcon.MEDIA,state.copy(items=items.filter {it.icon!=SlotIcon.RECORD}).primaryItem()?.icon)
    }
    @Test fun `offline pulse completes twice each second and stays legible`() {
        assertEquals(1f,OfflineWifiPulse.opacity(0,true),.001f)
        assertEquals(.45f,OfflineWifiPulse.opacity(250,true),.001f)
        assertEquals(1f,OfflineWifiPulse.opacity(500,true),.001f)
        assertEquals(1f,OfflineWifiPulse.opacity(250,false),0f)
    }
    @Test fun `only the visible unblocked offline icon requests moving frames`() {
        assertTrue(OfflineWifiPulse.active(SlotIcon.WIFI_OFFLINE,false,true))
        assertFalse(OfflineWifiPulse.active(SlotIcon.WIFI_OFFLINE,true,true))
        assertFalse(OfflineWifiPulse.active(SlotIcon.WIFI_OFFLINE,false,false))
        assertFalse(OfflineWifiPulse.active(SlotIcon.WIFI,false,true))
        assertFalse(OfflineWifiPulse.active(null,false,true))
    }
}
