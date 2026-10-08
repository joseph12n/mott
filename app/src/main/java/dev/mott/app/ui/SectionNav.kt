package dev.mott.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Bottom navigation across the seven Figma sections. Standard Material3
// NavigationBar (48dp+ targets, ripple press feedback, no hover states)
// with minimal hand-drawn Canvas glyphs: the Material icons artifact is
// not a project dependency and the lean build adds none, so each tab gets
// a small geometric mark in the shared 12dp-radius vocabulary instead of
// a new library. Mesas carries the open-table badge like the master
// sidebar (App.tsx:86-88): count only, hidden at zero.
@Composable
fun SectionNav(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
    mesasOpenCount: Int = 0,
) {
    NavigationBar(modifier = modifier) {
        for (section in AppSection.entries) {
            val isSelected = section == selected
            val badgeText = if (section == AppSection.MESAS) mesasBadgeText(mesasOpenCount) else null
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(section) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeText != null) {
                                Badge { Text(badgeText) }
                            }
                        },
                    ) {
                        SectionGlyph(section = section, selected = isSelected)
                    }
                },
                label = { Text(section.label) },
            )
        }
    }
}

@Composable
private fun SectionGlyph(section: AppSection, selected: Boolean) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = Modifier.size(24.dp)) {
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
