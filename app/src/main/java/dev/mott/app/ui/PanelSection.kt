package dev.mott.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.TodayResult
import dev.mott.app.domain.Tab
import dev.mott.app.ui.order.OrderEmptyState
import dev.mott.app.ui.order.OrderScreenHeader
import dev.mott.app.ui.order.OrderUiState
import kotlin.math.roundToLong

// Panel section: the day's summary in Figma card language. KPI hero
// (Ventas hoy with count-up, Mesas abiertas, En curso) from SalesRepo plus
// a hand-rolled Canvas bar chart of the last days' per-day totals (no
// chart library, lean requirement). All data fail-soft: the repo serves
// cache or explicit empty offline, so this screen states instead of
// crashing.
@Composable
fun PanelSection(
    salesRepo: SalesRepo,
    orderState: OrderUiState,
    modifier: Modifier = Modifier,
    shopName: String? = null,
) {
    var today by remember { mutableStateOf(TodayResult.empty()) }
    var recent by remember { mutableStateOf(emptyList<dev.mott.app.data.Sale>()) }
    var openTabs by remember { mutableStateOf(emptyList<Tab>()) }
    LaunchedEffect(Unit) {
        today = salesRepo.today()
        recent = salesRepo.recent(50)
        openTabs = salesRepo.openTabs()
    }
    val kpis = remember(today, openTabs) { computePanelKpis(today, openTabs) }
    val buckets = remember(recent) { bucketSalesByDay(recent, days = 7) }
    val tableLabel = remember(orderState.tables) {
        orderState.tables.associate { it.id to it.label }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OrderScreenHeader(title = "Panel", isOffline = orderState.isOffline, shopName = shopName)
        PanelHero(kpis = kpis)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MittCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mesas abiertas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = kpis.openCount.toString(), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "en curso ahora",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MittCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = "En curso",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                MittMoneyText(cents = kpis.inProgressCents, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "por cobrar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Ventas por día", style = MaterialTheme.typography.titleLarge)
                MittLivePill(text = "Últimos 7 días", live = false)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (buckets.isEmpty()) {
                OrderEmptyState(
                    title = "Sin ventas todavía",
                    hint = "Cuando se cierren cuentas, el gráfico de los últimos días aparece acá.",
                )
            } else {
                SalesBars(buckets = buckets)
            }
        }
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Órdenes recientes", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            if (recent.isEmpty()) {
                Text(
                    text = "Todavía no hay órdenes cerradas hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                recent.take(7).forEach { sale ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Mesa ${tableLabel[sale.tableId] ?: sale.tableId}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        MittMoneyText(cents = sale.totalCents)
                    }
                }
            }
        }
    }
}

// Hero KPI: today's collected sales. The figure counts up on change
// (purpose: feedback that fresh hub data landed, ease-out under 600ms);
// with system animations off it snaps to the final value.
@Composable
private fun PanelHero(kpis: PanelKpis, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val animatedTotal by animateFloatAsState(
        targetValue = kpis.salesTotalCents.toFloat(),
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 600),
    )
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Ventas hoy", style = MaterialTheme.typography.labelLarge)
            MittMoneyText(
                cents = animatedTotal.roundToLong(),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "${kpis.salesCount} órdenes cobradas",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// Hand-rolled daily-totals bars: one rounded bar per DayBucket plus value
// and day labels. Static by design, so reduced-motion needs no fallback.
@Composable
private fun SalesBars(buckets: List<DayBucket>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val valueColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueStyle = MaterialTheme.typography.labelSmall
    val labelStyle = MaterialTheme.typography.labelSmall
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp),
    ) {
        val max = buckets.maxOf { it.totalCents }.coerceAtLeast(1L).toFloat()
        val labelPad = 22.dp.toPx()
        val valuePad = 20.dp.toPx()
        val chartH = size.height - labelPad - valuePad
        val slot = size.width / buckets.size
        val barW = slot * 0.52f
        buckets.forEachIndexed { i, bucket ->
            val frac = bucket.totalCents.toFloat() / max
            val barH = (chartH * frac).coerceAtLeast(if (bucket.totalCents > 0) 4.dp.toPx() else 0f)
            val left = slot * i + (slot - barW) / 2f
            val top = valuePad + (chartH - barH)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = CornerRadius(barW / 3f, barW / 3f),
            )
            val valueLayout = measurer.measure(
                text = shortMoney(bucket.totalCents),
                style = valueStyle,
            )
            drawText(
                textLayoutResult = valueLayout,
                color = valueColor,
                topLeft = Offset(
                    slot * i + (slot - valueLayout.size.width) / 2f,
                    (valuePad - valueLayout.size.height).coerceAtLeast(0f),
                ),
            )
            val labelLayout = measurer.measure(text = bucket.label, style = labelStyle)
            drawText(
                textLayoutResult = labelLayout,
                color = labelColor,
                topLeft = Offset(
                    slot * i + (slot - labelLayout.size.width) / 2f,
                    size.height - labelPad + 4.dp.toPx(),
                ),
            )
        }
    }
}

// Compact axis figure: "$ 1.2k" style so values fit above narrow bars.
// Full precision stays in the KPI hero and recent list.
private fun shortMoney(cents: Long): String {
    if (cents < 100_000L) return "$ ${(cents / 100)}"
    val thousands = cents / 100_000.0
    val trimmed = if (thousands >= 10) {
        thousands.toInt().toString()
    } else {
        ((thousands * 10).toInt() / 10.0).toString()
    }
    return "$ ${trimmed}k"
}