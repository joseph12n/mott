package dev.mott.app.ui

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.mott.app.money.Money
import dev.mott.app.ui.order.OrderUiState
import dev.mott.app.ui.theme.FigureStyle
import dev.mott.app.ui.theme.MittButtonHeight
import kotlin.math.roundToLong

// Shared visual vocabulary, translated from the Figma web master
// (mottandmittdesing, read-only reference) to Material3 mobile:
// .card -> MittCard, .pill -> status pills, .btn -> Mitt*Button,
// .num (tabular figures) -> MittMoneyText over Money.formatCents.
// English identifiers; every user-visible string stays Spanish.

// Money label without duplicating the formatter: Money.formatCents is
// the single source of truth, this only adds the "$ " prefix the web
// master renders before every figure.
fun mittMoneyLabel(cents: Long): String = "$ ${Money.formatCents(cents)}"

// Live KPIs for the Tables header, computed from already-loaded state.
// openCount comes from table occupancy flags, draftTotalCents is the
// current draft running total, availableCount from product flags.
// No fetching: the caller passes the OrderUiState it already holds.
data class TablesKpis(
    val openCount: Int,
    val draftTotalCents: Long,
    val availableCount: Int,
)

fun computeTablesKpis(state: OrderUiState): TablesKpis = TablesKpis(
    openCount = state.tables.count { it.occupied },
    draftTotalCents = state.runningTotalCents,
    availableCount = state.products.count { it.available },
)

// True when the user disabled system animations: count-ups and motion
// snap to final values instead of tweening.
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

// Figma Header: display title plus muted sub line.
@Composable
fun MittSectionTitle(
    title: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Theme-level "live" primitive for the CONECTADO pill: the web .live
// class (index.css:68-69) pulses a brand ring 0->8px every 1.8s. This
// only animates the shadow ring; T3 decides which pill uses it.
@Composable
fun Modifier.livePulse(
    color: Color = MaterialTheme.colorScheme.tertiary,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || rememberReducedMotion()) return this
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-ring",
    )
    return this.graphicsLayer {
        shadowElevation = ring
        shape = CircleShape
        clip = false
        ambientShadowColor = color
        spotShadowColor = color
    }
}

// Status is never color-only: every pill pairs a dot with a text label.
// Tones follow the web master: success/brand for the good state,
// danger/berry for the attention state.
@Composable
private fun MittPill(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tone.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = tone)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = tone,
            )
        }
    }
}

@Composable
fun MittStatusPill(occupied: Boolean, modifier: Modifier = Modifier) {
    val tone = if (occupied) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    MittPill(
        text = if (occupied) "Ocupada" else "Libre",
        tone = tone,
        modifier = modifier,
    )
}

@Composable
fun MittStockPill(available: Boolean, modifier: Modifier = Modifier) {
    val tone = if (available) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }
    MittPill(
        text = if (available) "Disponible" else "Sin stock",
        tone = tone,
        modifier = modifier,
    )
}

// Neutral status tag with a dot, never color-only: for chart ranges,
// server state and other one-word facts that are neither occupancy nor
// stock. Tone follows the web master: brand/success for live, muted for
// plain context.
@Composable
fun MittLivePill(
    text: String,
    live: Boolean = true,
    modifier: Modifier = Modifier,
) {
    MittPill(
        text = text,
        tone = if (live) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

// Tabular money figure: monospace digits via FigureStyle so columns of
// prices align, exactly like the web .num class.
@Composable
fun MittMoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = FigureStyle,
    color: Color = Color.Unspecified,
) {
    Text(
        text = mittMoneyLabel(cents),
        style = style,
        color = color,
        modifier = modifier,
    )
}

// Figma .card: tonal surface, 1px line border, no shadow. Separation
// comes from the border, never from elevation.
@Composable
fun MittCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(Modifier.padding(16.dp), content = content) },
    )
}

@Composable
fun MittClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(Modifier.padding(16.dp), content = content) },
    )
}

// Figma .btn: 12dp radius, fixed 40dp height (MittButtonHeight); press
// scale handled by Material3 ripple.
@Composable
fun MittPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MittButtonHeight),
    ) {
        Text(label)
    }
}

@Composable
fun MittDangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.error,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MittButtonHeight),
    ) {
        Text(label)
    }
}

@Composable
fun MittSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MittButtonHeight),
    ) {
        Text(label)
    }
}

// Named load-failure card in Figma card language: reason title, guidance
// line, RETRY always, plus a re-pair shortcut when the hub rejected the
// token (server restart rotates it). Shared by Panel, Mesas and Gastos.
@Composable
fun MittLoadErrorCard(
    reason: LoadFailureReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRePair: (() -> Unit)? = null,
) {
    MittCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = reason.title(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = reason.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        MittPrimaryButton(label = "REINTENTAR", onClick = onRetry)
        if (reason.showRePair() && onRePair != null) {
            Spacer(modifier = Modifier.height(8.dp))
            MittSecondaryButton(label = "IR A CONEXIÓN", onClick = onRePair)
        }
    }
}

// Panel-style KPI block: hero money card plus two count cards, like the
// web Panel grid collapsed for a phone. The draft total counts up on
// change; with system animations off every value snaps instantly.
@Composable
fun MittKpiRow(kpis: TablesKpis, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val animatedOpen by animateIntAsState(
        targetValue = kpis.openCount,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 500),
    )
    val animatedAvailable by animateIntAsState(
        targetValue = kpis.availableCount,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 500),
    )
    val animatedTotal by animateFloatAsState(
        targetValue = kpis.draftTotalCents.toFloat(),
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 600),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "En curso",
                    style = MaterialTheme.typography.labelLarge,
                )
                MittMoneyText(
                    cents = animatedTotal.roundToLong(),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "pedido en curso por cobrar",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MittCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mesas abiertas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = animatedOpen.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "ocupadas ahora",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MittCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Productos disponibles",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = animatedAvailable.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "listos para anotar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
