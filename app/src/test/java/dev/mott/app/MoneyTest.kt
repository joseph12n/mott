package dev.mott.app

import dev.mott.app.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MoneyTest(private val cents: Long, private val expected: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun cases(): Collection<Array<Any>> = listOf(
            arrayOf(1250L, "12.50"),
            arrayOf(5L, "0.05"),
            arrayOf(-199L, "-1.99")
        )
    }

    @Test
    fun formatCents_rendersWholeAndFraction() {
        assertEquals(expected, Money.formatCents(cents))
    }
}
