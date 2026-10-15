package dev.mott.app

import dev.mott.app.ui.ConfirmStep
import dev.mott.app.ui.ConfirmTap
import dev.mott.app.ui.MesasPane
import dev.mott.app.ui.WidthClass
import dev.mott.app.ui.mesasPaneFor
import dev.mott.app.ui.mesasWidthClassFor
import dev.mott.app.ui.nextConfirm
import dev.mott.app.ui.tileColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// T2 Mesas without dialogs: pane choice per width class, tile column count
// and the inline two-tap confirm state machine. JVM-pure, no Compose.
class MesasLayoutTest {

    @Test
    fun `expanded is always split`() {
        assertEquals(MesasPane.SPLIT, mesasPaneFor(WidthClass.EXPANDED, hasSelection = false))
        assertEquals(MesasPane.SPLIT, mesasPaneFor(WidthClass.EXPANDED, hasSelection = true))
    }

    @Test
    fun `compact shows list without selection and detail with selection`() {
        assertEquals(MesasPane.LIST, mesasPaneFor(WidthClass.COMPACT, hasSelection = false))
        assertEquals(MesasPane.DETAIL, mesasPaneFor(WidthClass.COMPACT, hasSelection = true))
    }

    @Test
    fun `medium shows list without selection and detail with selection`() {
        assertEquals(MesasPane.LIST, mesasPaneFor(WidthClass.MEDIUM, hasSelection = false))
        assertEquals(MesasPane.DETAIL, mesasPaneFor(WidthClass.MEDIUM, hasSelection = true))
    }

    @Test
    fun `mesas width class splits from 720dp of section width`() {
        assertEquals(WidthClass.COMPACT, mesasWidthClassFor(360))
        assertEquals(WidthClass.MEDIUM, mesasWidthClassFor(600))
        assertEquals(WidthClass.MEDIUM, mesasWidthClassFor(719))
        assertEquals(WidthClass.EXPANDED, mesasWidthClassFor(720))
        assertEquals(WidthClass.EXPANDED, mesasWidthClassFor(1100))
    }

    @Test
    fun `tile columns follow n times 150 plus gaps of 12 within available width`() {
        assertEquals(1, tileColumns(296)) // 2 tiles need 312
        assertEquals(2, tileColumns(320)) // 312 <= 320
        assertEquals(2, tileColumns(360)) // 3 tiles need 474
        assertEquals(2, tileColumns(411))
        assertEquals(3, tileColumns(600)) // 4 tiles need 636
        assertEquals(5, tileColumns(840)) // 5 tiles need 798, 6 need 960
        assertEquals(6, tileColumns(1200)) // 6 tiles need 960, capped at 6
    }

    @Test
    fun `tile columns degrade to one for tiny or non-positive width`() {
        assertEquals(1, tileColumns(149))
        assertEquals(1, tileColumns(0))
        assertEquals(1, tileColumns(-10))
    }

    @Test
    fun `first tap on the primary action arms the confirm without executing`() {
        val t = nextConfirm(ConfirmStep.IDLE, ConfirmTap.PRIMARY)
        assertEquals(ConfirmStep.ARMED, t.step)
        assertFalse(t.execute)
    }

    @Test
    fun `second tap on the primary action executes and resets`() {
        val t = nextConfirm(ConfirmStep.ARMED, ConfirmTap.PRIMARY)
        assertEquals(ConfirmStep.IDLE, t.step)
        assertTrue(t.execute)
    }

    @Test
    fun `cancel disarms without executing`() {
        val armed = nextConfirm(ConfirmStep.ARMED, ConfirmTap.CANCEL)
        assertEquals(ConfirmStep.IDLE, armed.step)
        assertFalse(armed.execute)
        val idle = nextConfirm(ConfirmStep.IDLE, ConfirmTap.CANCEL)
        assertEquals(ConfirmStep.IDLE, idle.step)
        assertFalse(idle.execute)
    }
}
