package dev.mott.app

import dev.mott.app.ui.INLINE_FORM_MIN_DP
import dev.mott.app.ui.TWO_PANE_MIN_DP
import dev.mott.app.ui.inlineFormFor
import dev.mott.app.ui.twoPaneFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// T3 shared responsive rules for section bodies. JVM-pure, no Compose.
class ResponsiveLayoutTest {

    @Test
    fun `two panes start at the documented breakpoint`() {
        assertEquals(720, TWO_PANE_MIN_DP)
        assertFalse(twoPaneFor(320))
        assertFalse(twoPaneFor(411))
        assertFalse(twoPaneFor(719))
        assertTrue(twoPaneFor(720))
        assertTrue(twoPaneFor(1100))
    }

    @Test
    fun `inline forms start at the documented breakpoint`() {
        assertEquals(420, INLINE_FORM_MIN_DP)
        assertFalse(inlineFormFor(320))
        assertFalse(inlineFormFor(360))
        assertFalse(inlineFormFor(419))
        assertTrue(inlineFormFor(420))
        assertTrue(inlineFormFor(840))
    }
}
