package dev.mott.app.ui

// Pure window-width helpers for the adaptive shell. No Compose, no Android
// types: the width class derives from BoxWithConstraints.maxWidth and is
// unit-tested on the JVM (the window-size-class artifact is not a project
// dependency).

// Material window width classes: Compact < 600dp, Medium 600..839dp,
// Expanded >= 840dp.
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

// Navigation chrome per width class: horizontally scrollable bottom strip
// on phones and small tablets, permanent side rail once there is room.
enum class NavLayout { BOTTOM_STRIP, SIDE_RAIL }

// Master main-content max width (App.tsx max-w-[1400px]).
const val CONTENT_MAX_WIDTH_DP = 1400

private const val MEDIUM_MIN_DP = 600
private const val EXPANDED_MIN_DP = 840

fun widthClassFor(widthDp: Int): WidthClass = when {
    widthDp >= EXPANDED_MIN_DP -> WidthClass.EXPANDED
    widthDp >= MEDIUM_MIN_DP -> WidthClass.MEDIUM
    else -> WidthClass.COMPACT
}

fun navLayoutFor(widthClass: WidthClass): NavLayout = when (widthClass) {
    WidthClass.EXPANDED -> NavLayout.SIDE_RAIL
    WidthClass.COMPACT, WidthClass.MEDIUM -> NavLayout.BOTTOM_STRIP
}

// Largest column count n in 1..maxColumns whose tiles plus gaps fit the
// available width: n * minTileDp + (n - 1) * gapDp <= availableDp.
// Never below 1; non-positive width, tile or maxColumns degrade to 1.
fun adaptiveColumns(
    availableDp: Int,
    minTileDp: Int,
    gapDp: Int = 12,
    maxColumns: Int = 6,
): Int {
    if (availableDp <= 0 || minTileDp <= 0 || maxColumns <= 1) return 1
    val gap = gapDp.coerceAtLeast(0)
    var best = 1
    for (n in 2..maxColumns) {
        // Long math so absurd inputs cannot overflow into a false fit.
        val needed = n.toLong() * minTileDp + (n - 1).toLong() * gap
        if (needed <= availableDp) best = n else break
    }
    return best
}
