package io.github.kvmy666.duostatusbar.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiagnosticRefreshTest {
    @Test fun `fresh module response is distinguished from saved snapshot without blocking sleep`()=runBlocking {
        var stored="old";var waits=0;var requests=0
        val report=DiagnosticRefresh.await("fallback",{stored},{requests++},{waits++;if(waits==3)stored="new runtime evidence"})
        assertEquals(1,requests);assertEquals(3,waits)
        assertTrue(report.contains("refreshStatus=fresh_response_observed"));assertTrue(report.contains("new runtime evidence"))
    }
    @Test fun `timeout retains and labels cached module evidence instead of claiming a fresh capture`()=runBlocking {
        var waits=0
        val report=DiagnosticRefresh.await("saved",{"saved"},{},{waits++},attempts=3)
        assertEquals(3,waits);assertTrue(report.contains("cached_response_freshness_unconfirmed"));assertTrue(report.contains("saved"))
    }
    @Test fun `missing module evidence explains injection and does not demand root`()=runBlocking {
        val report=DiagnosticRefresh.await("",{""},{},{},attempts=1)
        assertTrue(report.contains("refreshStatus=missing"));assertTrue(report.contains("rootRequired=false"))
        assertTrue(report.contains("In module mode check LSPosed scope and injection"))
        assertTrue(report.contains("standalone root mode check the explicit start button"))
    }
    @Test fun `refresh failure includes type without sensitive exception message`()=runBlocking {
        val report=DiagnosticRefresh.await("cached",{"cached"},{throw SecurityException("personal arbitrary content")},{},attempts=1)
        assertTrue(report.contains("SecurityException"));assertFalse(report.contains("personal arbitrary content"))
    }
    @Test fun `screen cancellation is respected instead of exporting a stale report`()=runBlocking {
        try {DiagnosticRefresh.await("cached",{"cached"},{},{throw CancellationException("left screen")});fail("cancellation swallowed")}
        catch(expected:CancellationException){assertEquals("left screen",expected.message)}
    }
}
