package dev.mott.app.ui

// Pure layout rules for the Mesas section. No Compose, no Android types:
// the pane choice and the tile column count are unit-tested on the JVM.

// Which panes Mesas shows. LIST and DETAIL are single-pane (phones and
// small tablets); SPLIT puts the list and the detail side by side.
enum class MesasPane { LIST, DETAIL, SPLIT }

// Expanded windows always split; narrower ones show the detail full width
// while a table is selected and the list otherwise.
fun mesasPaneFor(widthClass: WidthClass, hasSelection: Boolean): MesasPane = when (widthClass) {
    WidthClass.EXPANDED -> MesasPane.SPLIT
    WidthClass.COMPACT, WidthClass.MEDIUM ->
        if (hasSelection) MesasPane.DETAIL else MesasPane.LIST
}

// Mesas decides on its own available width, not the window width: with the
// 256dp side rail the section is narrower than the window, so the Expanded
// window threshold (840) would never split. 720dp of section width fits two
// usable panes (a 2-column tile list plus the detail), which also covers a
// phone in landscape and tablets in portrait.
const val MESAS_SPLIT_MIN_DP = 720

fun mesasWidthClassFor(sectionWidthDp: Int): WidthClass =
    if (sectionWidthDp >= MESAS_SPLIT_MIN_DP) WidthClass.EXPANDED else widthClassFor(sectionWidthDp)

// Minimum width of one table tile; the tile itself is one 96dp+ tap target.
const val TILE_MIN_DP = 150

fun tileColumns(availableDp: Int): Int = adaptiveColumns(availableDp, minTileDp = TILE_MIN_DP)

// Inline two-tap confirm for destructive actions (no dialogs): the first tap
// on the primary action only arms it, the second one executes. Cancel
// disarms. State machine kept pure so the rule is testable.
enum class ConfirmStep { IDLE, ARMED }

enum class ConfirmTap { PRIMARY, CANCEL }

data class ConfirmTransition(val step: ConfirmStep, val execute: Boolean)

fun nextConfirm(step: ConfirmStep, tap: ConfirmTap): ConfirmTransition = when (tap) {
    ConfirmTap.CANCEL -> ConfirmTransition(ConfirmStep.IDLE, execute = false)
    ConfirmTap.PRIMARY -> when (step) {
        ConfirmStep.IDLE -> ConfirmTransition(ConfirmStep.ARMED, execute = false)
        ConfirmStep.ARMED -> ConfirmTransition(ConfirmStep.IDLE, execute = true)
    }
}
