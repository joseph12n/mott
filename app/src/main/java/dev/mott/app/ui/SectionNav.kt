package dev.mott.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Navigation across the seven Figma sections, in the master nav vocabulary
// (App.tsx:75-90): pill items with a minimal Canvas glyph + label, the
// active item filled with ink (onSurface) and paper (surface) text, the
// inactive ones muted. The Material icons artifact is not a project
// dependency, so each section keeps a small geometric mark drawn here.
// Mesas carries the open-table badge (count only, hidden at zero).
// [SectionNavStrip] is the compact/medium bottom bar (horizontally
// scrollable, selected item auto-scrolls into view); [SectionNavRailItem]
// is the full-width item stacked inside the expanded side rail.

private val NavItemMinHeight = 48.dp

@Composable
fun SectionNavStrip(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
    mesasOpenCount: Int = 0,
) {
    val listState = rememberLazyListState()
    val reducedMotion = rememberReducedMotion()
    val edgeGapPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    LaunchedEffect(selected) {
        val index = AppSection.entries.indexOf(selected)
        if (!listState.isFullyVisible(index)) {
            if (reducedMotion) {
                listState.scrollToItem(index, -edgeGapPx)
            } else {
                listState.animateScrollToItem(index, -edgeGapPx)
            }
        }
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Box {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(AppSection.entries, key = { it.route }) { section ->
                    SectionNavItem(
                        section = section,
                        selected = section == selected,
                        badgeText = badgeFor(section, mesasOpenCount),
                        onClick = { onSelect(section) },
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
fun SectionNavRailItem(
    section: AppSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mesasOpenCount: Int = 0,
) {
    SectionNavItem(
        section = section,
        selected = selected,
        badgeText = badgeFor(section, mesasOpenCount),
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        fillWidth = true,
    )
}

private fun badgeFor(section: AppSection, mesasOpenCount: Int): String? =
    if (section == AppSection.MESAS) mesasBadgeText(mesasOpenCount) else null

// True when the item at [index] is laid out entirely inside the viewport.
private fun LazyListState.isFullyVisible(index: Int): Boolean {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return info.offset >= 0 && info.offset + info.size <= layoutInfo.viewportSize.width
}

@Composable
private fun SectionNavItem(
    section: AppSection,
    selected: Boolean,
    badgeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.onSurface else Color.Transparent
    val content = if (selected) colors.surface else colors.onSurfaceVariant
    Row(
        modifier = modifier
            .heightIn(min = NavItemMinHeight)
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionGlyph(section = section, color = content)
        Text(
            text = section.label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
        )
        if (badgeText != null) {
            NavBadge(text = badgeText, selected = selected)
        }
    }
}

// Count badge: the number is the signal, color only reinforces it.
@Composable
private fun NavBadge(text: String, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.surface.copy(alpha = 0.2f) else colors.primary.copy(alpha = 0.16f)
    val content = if (selected) colors.surface else colors.primary
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

// Section glyph drawn on a Canvas in [color]; reused by the strip, the rail
// and any other shell chrome that needs the section mark.
@Composable
internal fun SectionGlyph(
    section: AppSection,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        when (section) {
            AppSection.PANEL -> drawBars(color)
            AppSection.MESAS -> drawGrid(color)
            AppSection.CATALOGO -> drawList(color)
            AppSection.PROVEEDORES -> drawTruck(color)
            AppSection.GASTOS -> drawWallet(color)
            AppSection.CONEXION -> drawQr(color)
            AppSection.PERSONALIZAR -> drawSliders(color)
        }
    }
}

// Refresh mark for the compact header: open circular arrow with a head.
@Composable
internal fun RefreshGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) { drawRefresh(color) }
}

private fun DrawScope.drawRefresh(color: Color) {
    val stroke = size.width / 12f
    val inset = size.width * 0.16f
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
        style = Stroke(width = stroke),
    )
    // Arrow head at the arc start (upper right), pointing clockwise.
    val head = Path().apply {
        moveTo(size.width * 0.86f, size.height * 0.1f)
        lineTo(size.width * 0.88f, size.height * 0.42f)
        lineTo(size.width * 0.58f, size.height * 0.32f)
        close()
    }
    drawPath(path = head, color = color)
}

// Panel: three ascending bars on a baseline.
private fun DrawScope.drawBars(color: Color) {
    val barW = size.width / 7f
    val heights = listOf(0.35f, 0.6f, 0.9f)
    heights.forEachIndexed { i, frac ->
        val left = barW * (1.2f + i * 2f)
        val top = size.height * (0.92f - frac * 0.75f)
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(barW, size.height * 0.92f - top),
            cornerRadius = CornerRadius(barW / 3f, barW / 3f),
        )
    }
    drawLine(
        color = color,
        start = Offset(size.width * 0.08f, size.height * 0.92f),
        end = Offset(size.width * 0.92f, size.height * 0.92f),
        strokeWidth = size.width / 14f,
    )
}

// Mesas: 2x2 table grid.
private fun DrawScope.drawGrid(color: Color) {
    val cell = size.width * 0.36f
    val gap = size.width * 0.12f
    val origin = (size.width - cell * 2f - gap) / 2f
    for (row in 0..1) {
        for (col in 0..1) {
            drawRoundRect(
                color = color,
                topLeft = Offset(origin + col * (cell + gap), origin + row * (cell + gap)),
                size = androidx.compose.ui.geometry.Size(cell, cell),
                cornerRadius = CornerRadius(cell / 4f, cell / 4f),
            )
        }
    }
}

// Catálogo: price-list rows with bullets.
private fun DrawScope.drawList(color: Color) {
    val rows = listOf(0.25f, 0.5f, 0.75f)
    rows.forEach { frac ->
        val y = size.height * frac
        drawCircle(color = color, radius = size.width / 16f, center = Offset(size.width * 0.16f, y))
        drawLine(
            color = color,
            start = Offset(size.width * 0.3f, y),
            end = Offset(size.width * 0.86f, y),
            strokeWidth = size.width / 12f,
        )
    }
}

// Gastos: wallet outline with clasp.
private fun DrawScope.drawWallet(color: Color) {
    val stroke = size.width / 14f
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.08f, size.height * 0.28f),
        size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.52f),
        cornerRadius = CornerRadius(size.width / 10f, size.width / 10f),
        style = Stroke(width = stroke),
    )
    drawCircle(
        color = color,
        radius = size.width / 22f,
        center = Offset(size.width * 0.72f, size.height * 0.54f),
    )
}

// Conexión: QR mark, scan-first like the pairing screen.
private fun DrawScope.drawQr(color: Color) {
    val stroke = size.width / 14f
    val cell = size.width * 0.3f
    val pad = size.width * 0.1f
    drawRect(color = color, topLeft = Offset(pad, pad), size = androidx.compose.ui.geometry.Size(cell, cell), style = Stroke(width = stroke))
    drawRect(color = color, topLeft = Offset(size.width - pad - cell, pad), size = androidx.compose.ui.geometry.Size(cell, cell), style = Stroke(width = stroke))
    drawRect(color = color, topLeft = Offset(pad, size.height - pad - cell), size = androidx.compose.ui.geometry.Size(cell, cell), style = Stroke(width = stroke))
    drawCircle(color = color, radius = size.width / 18f, center = Offset(size.width * 0.72f, size.height * 0.72f))
}

// Proveedores: delivery box on wheels, same outline vocabulary as Gastos.
private fun DrawScope.drawTruck(color: Color) {
    val stroke = size.width / 14f
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.08f, size.height * 0.22f),
        size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.4f),
        cornerRadius = CornerRadius(size.width / 12f, size.width / 12f),
        style = Stroke(width = stroke),
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.64f, size.height * 0.42f),
        end = Offset(size.width * 0.86f, size.height * 0.42f),
        strokeWidth = stroke,
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.86f, size.height * 0.42f),
        end = Offset(size.width * 0.86f, size.height * 0.62f),
        strokeWidth = stroke,
    )
    drawCircle(color = color, radius = size.width / 13f, center = Offset(size.width * 0.28f, size.height * 0.74f))
    drawCircle(color = color, radius = size.width / 13f, center = Offset(size.width * 0.7f, size.height * 0.74f))
}

// Personalizar: two tune sliders with knobs, same line vocabulary as Catálogo.
private fun DrawScope.drawSliders(color: Color) {
    val stroke = size.width / 14f
    val rows = listOf(0.32f to 0.62f, 0.68f to 0.34f)
    rows.forEach { (lineFrac, knobFrac) ->
        val y = size.height * lineFrac
        drawLine(
            color = color,
            start = Offset(size.width * 0.1f, y),
            end = Offset(size.width * 0.9f, y),
            strokeWidth = stroke,
        )
        drawCircle(
            color = color,
            radius = size.width / 10f,
            center = Offset(size.width * knobFrac, y),
        )
    }
}
