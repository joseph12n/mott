package dev.mott.app

import dev.mott.app.ui.CONTENT_MAX_WIDTH_DP
import dev.mott.app.ui.NavLayout
import dev.mott.app.ui.WidthClass
import dev.mott.app.ui.adaptiveColumns
import dev.mott.app.ui.navLayoutFor
import dev.mott.app.ui.widthClassFor
import org.junit.Assert.assertEquals
import org.junit.Test

// T1 adaptive shell contract: window width class, nav layout choice and the
// shared adaptive column helper. JVM-pure, no Compose.
class WindowWidthTest {

    @Test
    fun `width class is compact below 600`() {
        for (dp in listOf(0, 320, 360, 411, 599)) {
            assertEquals("width $dp", WidthClass.COMPACT, widthClassFor(dp))
        }
    }

    @Test
    fun `width class is medium from 600 to 839`() {
        assertEquals(WidthClass.MEDIUM, widthClassFor(600))
        assertEquals(WidthClass.MEDIUM, widthClassFor(839))
    }

    @Test
    fun `width class is expanded from 840`() {
        assertEquals(WidthClass.EXPANDED, widthClassFor(840))
        assertEquals(WidthClass.EXPANDED, widthClassFor(1200))
    }

    @Test
    fun `negative width degrades to compact`() {
        assertEquals(WidthClass.COMPACT, widthClassFor(-5))
    }

    @Test
    fun `only expanded uses the side rail`() {
        assertEquals(NavLayout.BOTTOM_STRIP, navLayoutFor(WidthClass.COMPACT))
        assertEquals(NavLayout.BOTTOM_STRIP, navLayoutFor(WidthClass.MEDIUM))
        assertEquals(NavLayout.SIDE_RAIL, navLayoutFor(WidthClass.EXPANDED))
    }

    @Test
    fun `content max width matches the master 1400`() {
        assertEquals(1400, CONTENT_MAX_WIDTH_DP)
    }

    @Test
    fun `adaptive columns at reference widths with 150dp tiles`() {
        assertEquals(2, adaptiveColumns(320, 150))
        assertEquals(2, adaptiveColumns(360, 150))
        assertEquals(2, adaptiveColumns(411, 150))
        assertEquals(3, adaptiveColumns(600, 150))
        assertEquals(5, adaptiveColumns(840, 150))
        assertEquals(6, adaptiveColumns(1200, 150))
    }

    @Test
    fun `adaptive columns honors the exact fit boundary`() {
        // 2*150 + 12 = 312 fits exactly, 311 does not.
        assertEquals(2, adaptiveColumns(312, 150))
        assertEquals(1, adaptiveColumns(311, 150))
    }

    @Test
    fun `adaptive columns clamps to maxColumns`() {
        assertEquals(6, adaptiveColumns(5000, 100))
        assertEquals(3, adaptiveColumns(5000, 100, maxColumns = 3))
    }

    @Test
    fun `adaptive columns never drops below one`() {
        assertEquals(1, adaptiveColumns(100, 150))
        assertEquals(1, adaptiveColumns(150, 150))
    }

    @Test
    fun `adaptive columns defends against non-positive input`() {
        assertEquals(1, adaptiveColumns(0, 150))
        assertEquals(1, adaptiveColumns(-10, 150))
        assertEquals(1, adaptiveColumns(360, 0))
        assertEquals(1, adaptiveColumns(360, -150))
        assertEquals(1, adaptiveColumns(360, 150, maxColumns = 0))
    }

    @Test
    fun `adaptive columns respects a custom gap`() {
        // 3*100 + 2*0 = 300 fits with no gap, 3*100 + 2*12 = 324 does not.
        assertEquals(3, adaptiveColumns(300, 100, gapDp = 0))
        assertEquals(2, adaptiveColumns(300, 100))
    }
}
