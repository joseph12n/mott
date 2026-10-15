package dev.mott.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Shared responsive rules for section bodies. A section decides on its OWN
// available width (the side rail makes it narrower than the window), so the
// breakpoints below apply to BoxWithConstraints.maxWidth, never the window.

// Two panes fit side by side from this section width: two readable columns
// (>= 340dp each) plus the 16dp gap.
const val TWO_PANE_MIN_DP = 720

fun twoPaneFor(sectionWidthDp: Int): Boolean = sectionWidthDp >= TWO_PANE_MIN_DP

// A form row (field + action) keeps both on one line only when the field
// keeps a usable width next to a fixed-width action.
const val INLINE_FORM_MIN_DP = 420

fun inlineFormFor(sectionWidthDp: Int): Boolean = sectionWidthDp >= INLINE_FORM_MIN_DP

// Side-by-side panes on wide sections, stacked on narrow ones. Panes share
// the width equally and never overlap: stacked panes use the full width.
@Composable
fun TwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = 16.dp,
) {
    BoxWithConstraints(modifier = modifier) {
        if (twoPaneFor(maxWidth.value.toInt())) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) { first() }
                Column(modifier = Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                first()
                second()
            }
        }
    }
}

// Add-form layout for a container of formWidthDp: two fields side by side
// from INLINE_FORM_MIN_DP, stacked full width below it. The action joins the
// fields on the same line only when the container is two-pane wide
// (>= TWO_PANE_MIN_DP), because three controls in a row need that room;
// otherwise it sits under the fields at full width (>= 48dp tall).
@Composable
fun ResponsiveFormFields(
    formWidthDp: Int,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    action: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldsInline = inlineFormFor(formWidthDp)
    val actionInline = twoPaneFor(formWidthDp)
    Column(modifier = modifier.fillMaxWidth()) {
        if (fieldsInline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                first(Modifier.weight(2f))
                second(Modifier.weight(1f))
                if (actionInline) action(Modifier.width(200.dp))
            }
        } else {
            first(Modifier.fillMaxWidth())
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            second(Modifier.fillMaxWidth())
        }
        if (!actionInline) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            action(Modifier.fillMaxWidth())
        }
    }
}
