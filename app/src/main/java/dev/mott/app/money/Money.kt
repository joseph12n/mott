package dev.mott.app.money

import kotlin.math.abs

object Money {
    fun formatCents(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val whole = abs(cents) / 100
        val fraction = (abs(cents) % 100).toString().padStart(2, '0')
        return "$sign$whole.$fraction"
    }
}
