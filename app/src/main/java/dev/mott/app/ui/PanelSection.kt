package dev.mott.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mott.app.data.ExpensesRepo
import dev.mott.app.data.SalesRepo
import dev.mott.app.ui.order.OfflineBanner
import dev.mott.app.ui.order.OrderEmptyState
import dev.mott.app.ui.order.OrderScreenHeader
import dev.mott.app.ui.order.OrderUiState
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

// Panel section: the day's summary in Figma card language. KPI hero
// (Ventas hoy with count-up) plus Ordenes / Ticket promedio / En mesas
// abiertas from the hub today aggregate and open tabs, an hourly area
// chart (client-side LOCAL-hour buckets from GET /api/sales, like the
// mitt web Panel), top-5 products, per-table bars, recent orders with a
// net footer, and the Conectar/Proveedores CTAs. Data loads through
// loadPanelState: every sales failure lands in a named Failed state
// rendered as an error card with retry, so nothing escapes the
// LaunchedEffect and the screen states instead of crashing. Gastos load
// fail-soft to zero on top (a gastos outage must not blank the Panel).
// Supplier accounts are OMITTED on purpose: the hub exposes no supplier
// ledger (no purchases/balances per supplier), so there is nothing
// honest to chart; Proveedores owns the directory instead.
@Composable
fun PanelSection(
    salesRepo: SalesRepo,
    orderState: OrderUiState,
    modifier: Modifier = Modifier,
    shopName: String? = null,
    onNavigateConnection: () -> Unit = {},
    onNavigateSuppliers: () -> Unit = {},
    expensesRepo: ExpensesRepo? = null,
    // T3 shell: the ShellHeader owns the title/subtitle, and its
    // Refrescar bumps refreshSignal to re-trigger this load.
    showHeader: Boolean = true,
    refreshSignal: Int = 0,
) {
    var panelState by remember { mutableStateOf<LoadState<PanelData>>(LoadState.Loading) }
    var expensesTodayCents by remember { mutableStateOf(0L) }
    var reloadToken by remember { mutableStateOf(0) }
    LaunchedEffect(reloadToken, refreshSignal) {
        panelState = loadPanelState(salesRepo)
        expensesTodayCents = runCatching {
            val items = expensesRepo?.list() ?: emptyList()
            expensesTodayTotal(items, LocalDate.now(), ZoneId.systemDefault())
        }.getOrDefault(0L)
    }
    val tableLabel = remember(orderState.tables) {
        orderState.tables.associate { it.id to it.label }
    }
    val ctas = remember(onNavigateConnection, onNavigateSuppliers) {
        panelCtas(onConnectMobile = onNavigateConnection, onViewSuppliers = onNavigateSuppliers)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Shell-owned title: skip our own header but keep the offline
        // banner so the offline state never goes silent.
        if (showHeader) {
            OrderScreenHeader(title = "Panel", isOffline = orderState.isOffline, shopName = shopName)
        } else if (orderState.isOffline) {
            OfflineBanner()
        }
        when (val loaded = panelState) {
            LoadState.Loading -> {
                Text(
                    text = "CARGANDO...",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is LoadState.Failed -> {
                MittLoadErrorCard(
                    reason = loaded.reason,
                    onRetry = { reloadToken++ },
                    onRePair = onNavigateConnection,
                )
            }
            is LoadState.Ready -> {
                val data = loaded.data
                val kpis = remember(data) { computePanelKpis(data.today, data.openTabs) }
                val avgTicket = remember(kpis) {
                    averageTicketCents(kpis.salesTotalCents, kpis.salesCount)
                }
                val hours = remember(data.recent) { bucketSalesByHour(data.recent) }
                val top = remember(data.recent) { topProducts(data.recent) }
                val byTable = remember(data.recent, tableLabel) {
                    salesByTable(data.recent) { id -> tableLabel[id] ?: id }
                }
                val net = remember(kpis, expensesTodayCents) {
                    netCents(kpis.salesTotalCents, expensesTodayCents)
                }
                PanelHero(kpis = kpis)
                MittPrimaryButton(label = "Conectar app móvil", onClick = ctas.onConnectMobile)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MittCard(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Órdenes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = kpis.salesCount.toString(), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "cerradas hoy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MittCard(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ticket promedio",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MittMoneyText(cents = avgTicket, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "por orden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "En mesas abiertas",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MittMoneyText(cents = kpis.inProgressCents, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "${kpis.openCount} mesas en curso",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Ventas por hora", style = MaterialTheme.typography.titleLarge)
                        MittLivePill(text = "Hoy", live = false)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (hourlySalesTotal(hours) == 0L) {
                        OrderEmptyState(
                            title = "Sin ventas todavía",
                            hint = "Cuando se cierren cuentas, el gráfico por hora aparece acá.",
                        )
                    } else {
                        HourlyAreaChart(buckets = hours)
                    }
                }
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Más pedidos", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (top.isEmpty()) {
                        Text(
                            text = "Todavía no hay ventas registradas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val maxTop = top.first().qty.coerceAtLeast(1)
                        top.forEachIndexed { i, product ->
                            TopProductRow(rank = i + 1, product = product, maxQty = maxTop)
                            if (i < top.lastIndex) Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Compras por mesa", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${byTable.size} mesas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Total cobrado de cada mesa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (byTable.isEmpty()) {
                        Text(
                            text = "Aún no hay ventas. Cerrá la primera cuenta desde Mesas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val maxTable = byTable.first().totalCents.coerceAtLeast(1L)
                        byTable.take(7).forEachIndexed { i, row ->
                            TableSalesRow(row = row, maxTotal = maxTable, first = i == 0)
                            if (i < minOf(byTable.size, 7) - 1) Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Órdenes recientes", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (data.recent.isEmpty()) {
                        Text(
                            text = "Todavía no hay órdenes cerradas hoy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        data.recent.take(7).forEach { sale ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Mesa ${tableLabel[sale.tableId] ?: sale.tableId}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatSaleHour(sale.closedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${sale.items.sumOf { it.qty }} ítems",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MittMoneyText(cents = sale.totalCents)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Gastos de hoy: ${mittMoneyLabel(expensesTodayCents)} · " +
                            "Neto: ${mittMoneyLabel(net)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Pagos a proveedores: $ 0.00 (el hub no registra pagos).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Proveedores", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Compras y contactos del bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MittSecondaryButton(label = "Ver proveedores", onClick = ctas.onViewSuppliers)
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

// Hand-rolled hourly area chart: one point per HourBucket (11h-22h)
// joined by a brand polyline over a brand gradient fill, with a mute
// hour axis and money gridlines. Static by design, so reduced-motion
// needs no fallback. Every color reads the scheme (brand/sun/berry
// tokens land in primary/secondary/error), never a hardcoded hex, so
// dark mode and hub branding repaints come for free.
@Composable
private fun HourlyAreaChart(buckets: List<HourBucket>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.primary
    val fillTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val fillBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp),
    ) {
        val gutter = 44.dp.toPx()
        val labelPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartW = size.width - gutter
        val chartH = size.height - labelPad - topPad
        val baseY = topPad + chartH
        val max = buckets.maxOf { it.totalCents }.coerceAtLeast(1L).toFloat()
        fun x(i: Int): Float = (gutter + chartW * (i + 0.5f) / buckets.size).toFloat()
        fun y(value: Long): Float = topPad + chartH * (1f - value.toFloat() / max)
        // Money gridlines at 0, half and full scale.
        listOf(0f, 0.5f, 1f).forEach { frac ->
            val gy = topPad + chartH * (1f - frac)
            drawLine(color = grid, start = Offset(gutter, gy), end = Offset(size.width, gy))
            val tag = measurer.measure(text = shortMoney((max * frac).toLong()), style = labelStyle)
            drawText(
                textLayoutResult = tag,
                color = labelColor,
                topLeft = Offset(
                    (gutter - tag.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                    (gy - tag.size.height / 2f).coerceAtLeast(0f),
                ),
            )
        }
        // Gradient area under the polyline.
        val area = Path().apply {
            moveTo(x(0), y(buckets.first().totalCents))
            buckets.forEachIndexed { i, bucket -> if (i > 0) lineTo(x(i), y(bucket.totalCents)) }
            lineTo(x(buckets.lastIndex), baseY)
            lineTo(x(0), baseY)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(colors = listOf(fillTop, fillBottom)),
        )
        // Brand polyline plus a dot per hour so sparse days stay readable.
        val stroke = Path().apply {
            moveTo(x(0), y(buckets.first().totalCents))
            buckets.forEachIndexed { i, bucket -> if (i > 0) lineTo(x(i), y(bucket.totalCents)) }
        }
        drawPath(
            path = stroke,
            color = line,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        buckets.forEachIndexed { i, bucket ->
            drawCircle(color = line, radius = 3.dp.toPx(), center = Offset(x(i), y(bucket.totalCents)))
        }
        // Hour axis, every service hour like the web master.
        buckets.forEachIndexed { i, bucket ->
            val tag = measurer.measure(text = bucket.label, style = labelStyle)
            drawText(
                textLayoutResult = tag,
                color = labelColor,
                topLeft = Offset(
                    x(i) - tag.size.width / 2f,
                    size.height - labelPad + 4.dp.toPx(),
                ),
            )
        }
    }
}

// Top-product row: rank + name, quantity, and a brand progress bar scaled
// to the leader. Scheme colors only; the track is the sunk surface.
@Composable
private fun TopProductRow(rank: Int, product: TopProduct, maxQty: Int, modifier: Modifier = Modifier) {
    val fraction = (product.qty.toFloat() / maxQty).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$rank. ${product.name}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${product.qty} u.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// Per-table horizontal bar: label, bar scaled to the leader, money
// value. The leader bar is sun (secondary), the rest brand (primary),
// mirroring the web "Compras por mesa" first-bar highlight.
@Composable
private fun TableSalesRow(row: TableSales, maxTotal: Long, first: Boolean, modifier: Modifier = Modifier) {
    val fraction = (row.totalCents.toFloat() / maxTotal).coerceIn(0f, 1f)
    val bar = if (first) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(84.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bar),
            )
        }
        MittMoneyText(cents = row.totalCents, style = MaterialTheme.typography.labelLarge)
    }
}

// Compact axis figure: "$ 1.2k" style so values fit narrow gutters.
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
