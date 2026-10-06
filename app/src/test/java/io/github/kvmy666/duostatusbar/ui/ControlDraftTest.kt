package io.github.kvmy666.duostatusbar.ui
import org.junit.Assert.*
import org.junit.Test

class ControlDraftTest {
    @Test fun `recompositions and delayed saved values cannot interrupt dragging`() {
        var draft = ControlDraft(100f)
        repeat(200) { draft = draft.move(100f + it, 60f..1000f).external(100f) }
        assertEquals(299f, draft.value, 0f)
        assertTrue(draft.editing)
        draft = draft.finish().external(299f)
        assertFalse(draft.editing)
        assertEquals(299f, draft.value, 0f)
        assertEquals(60f, draft.external(60f).value, 0f)
    }
    @Test fun `scale height size and thickness endpoints remain selectable`() {
        for (range in listOf(0f..100f, 60f..1000f, 1f..300f)) {
            val draft = ControlDraft(range.start)
            assertEquals(range.start, draft.move(-10000f, range).value, 0f)
            assertEquals(range.endInclusive, draft.move(10000f, range).value, 0f)
        }
    }
}
