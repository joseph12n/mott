package dev.mott.app

import dev.mott.app.ui.order.FakeOrderCatalog
import dev.mott.app.ui.order.OrderCatalog
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.domain.Product
import dev.mott.app.ui.order.TableRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderViewModelTest {

    private val catalog = FakeOrderCatalog()
    private fun viewModel(offline: Boolean = false) = OrderViewModel(catalog, offline)

    @Test
    fun initialState_loadsCatalogTablesAndProducts() {
        val state = viewModel().state.value
        assertEquals(catalog.listTables(), state.tables)
        assertEquals(catalog.listProducts().map { it.id }, state.products.map { it.id })
        assertNull(state.selectedTableId)
        assertTrue(state.lines.isEmpty())
        assertEquals(0L, state.runningTotalCents)
        assertNull(state.error)
    }

    @Test
    fun selectTable_setsSelectedTableId() {
        val vm = viewModel()
        vm.selectTable("t3")
        val state = vm.state.value
        assertEquals("t3", state.selectedTableId)
        assertEquals("MESA 3", state.selectedTable?.label)
    }

    @Test
    fun toggleLine_addsThenRemovesLine() {
        val vm = viewModel()
        vm.toggleLine("p1")
        assertEquals(mapOf("p1" to 1), vm.state.value.lines)
        vm.toggleLine("p1")
        assertTrue(vm.state.value.lines.isEmpty())
        assertEquals(0L, vm.state.value.runningTotalCents)
    }

    @Test
    fun increment_decrementAdjustQtyAndTotal() {
        val vm = viewModel()
        vm.increment("p1")
        vm.increment("p1")
        vm.increment("p2")
        val p1 = catalog.listProducts().single { it.id == "p1" }
        val p2 = catalog.listProducts().single { it.id == "p2" }
        assertEquals(mapOf("p1" to 2, "p2" to 1), vm.state.value.lines)
        assertEquals(p1.priceCents * 2 + p2.priceCents, vm.state.value.runningTotalCents)

        vm.decrement("p1")
        assertEquals(mapOf("p1" to 1, "p2" to 1), vm.state.value.lines)
        assertEquals(p1.priceCents + p2.priceCents, vm.state.value.runningTotalCents)

        vm.decrement("p1")
        assertEquals(mapOf("p2" to 1), vm.state.value.lines)
    }

    @Test
    fun toggleLine_ignoresUnavailableProductWithError() {
        val vm = viewModel()
        val unavailable = catalog.listProducts().first { !it.available }
        vm.toggleLine(unavailable.id)
        assertTrue(vm.state.value.lines.isEmpty())
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun toggleLine_ignoresUnknownProduct() {
        val vm = viewModel()
        vm.toggleLine("no-such-product")
        assertTrue(vm.state.value.lines.isEmpty())
        assertNull(vm.state.value.error)
    }

    @Test
    fun commit_withoutTable_returnsNullWithError() {
        val vm = viewModel()
        vm.increment("p1")
        assertNull(vm.commit())
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.lastOrder)
    }

    @Test
    fun commit_withoutLines_returnsNullWithError() {
        val vm = viewModel()
        vm.selectTable("t1")
        assertNull(vm.commit())
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.lastOrder)
    }

    @Test
    fun commit_withTableAndLines_returnsClosedTabPayload() {
        val vm = viewModel()
        vm.selectTable("t1")
        vm.increment("p1")
        vm.increment("p1")
        vm.increment("p2")
        val payload = vm.commit()
        assertNotNull(payload)
        assertEquals("t1", payload!!.tableId)
        assertEquals(2, payload.lines.size)
        val p1 = catalog.listProducts().single { it.id == "p1" }
        val p2 = catalog.listProducts().single { it.id == "p2" }
        assertEquals(p1.priceCents * 2 + p2.priceCents, payload.totalCents)
        assertEquals(payload.totalCents, vm.state.value.runningTotalCents)
        assertNull(vm.state.value.error)
        assertEquals(payload, vm.state.value.lastOrder)
    }

    @Test
    fun offlineFlag_passthroughAndSetter() {
        assertEquals(false, viewModel(offline = false).state.value.isOffline)
        assertEquals(true, viewModel(offline = true).state.value.isOffline)
        val vm = viewModel()
        vm.setOffline(true)
        assertEquals(true, vm.state.value.isOffline)
        vm.setOffline(false)
        assertEquals(false, vm.state.value.isOffline)
    }

    @Test
    fun commit_worksAgainstCustomCatalog() {
        val custom = object : OrderCatalog {
            override fun listTables() = listOf(TableRef("bar", "BARRA", occupied = false))
            override fun listProducts() = listOf(Product("c1", "Café", 350, available = true))
        }
        val vm = OrderViewModel(custom)
        vm.selectTable("bar")
        vm.increment("c1")
        val payload = vm.commit()
        assertNotNull(payload)
        assertEquals(350L, payload!!.totalCents)
        assertEquals("Café", payload.lines.single().name)
    }

    @Test
    fun startNewOrder_clearsSelectionAndLines() {
        val vm = viewModel()
        vm.selectTable("t1")
        vm.increment("p1")
        vm.commit()
        assertNotNull(vm.state.value.lastOrder)
        vm.startNewOrder()
        val state = vm.state.value
        assertNull(state.selectedTableId)
        assertTrue(state.lines.isEmpty())
        assertEquals(0L, state.runningTotalCents)
        assertNull(state.lastOrder)
        assertNull(state.error)
    }
}
