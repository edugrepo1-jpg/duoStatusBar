package io.github.kvmy666.duostatusbar.fx

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EventsTest {
    @Before fun reset() { Events.changed = null; Events.clear() }
    @Test fun `wrap preserves base and contains one event block even when repeated`() {
        Events.record(123, "bateria 20%")
        val first = Events.wrap("diagnóstico original")
        assertEquals(first, Events.wrap(first))
        assertTrue(first.startsWith("diagnóstico original"))
        assertTrue(first.contains("123 bateria 20%"))
        assertTrue(Events.wrap(null).contains(Events.MARKER))
    }
    @Test fun `buffer retains most recent 400 lines and normalizes embedded newlines`() {
        repeat(405) { Events.record(it.toLong(), "evento\n$it") }
        val lines = Events.snapshot().lines()
        assertEquals(400, lines.size)
        assertEquals("5 evento 5", lines.first())
        assertEquals("404 evento 404", lines.last())
    }
    @Test fun `broken listener cannot break the caller`() {
        Events.changed = { error("indisponível") }
        Events.record(1, "evento")
        assertEquals("1 evento", Events.snapshot())
    }
}
