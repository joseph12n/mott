package dev.mott.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mott.app.ui.theme.ThemeMode

// Master Sidebar width (App.tsx lg:w-64).
private val RailWidth = 256.dp

private const val FALLBACK_SHOP_NAME = "mitt"

// Rail title: the hub brand name, trimmed, or "mitt" when there is none.
fun shopDisplayName(shopName: String?): String =
    shopName?.trim().orEmpty().ifEmpty { FALLBACK_SHOP_NAME }

// Responsive app chrome. Compact/Medium (< 840dp): slim one-row header on
// top, section content, scrollable pill strip at the bottom. Expanded
// (>= 840dp): permanent 256dp rail on the left (logo, nav, status, actions)
// with a title header and width-capped content on the right. The layout is
// picked from the available width through the pure widthClassFor /
// navLayoutFor helpers. [content] receives a Modifier that already carries
// the remaining size (and the 1400dp cap on wide windows).
@Composable
fun AdaptiveShell(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
    mesasOpenCount: Int,
    pill: ConnectionPillState,
    themeMode: ThemeMode,
    onRefresh: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    shopName: String?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val layout = navLayoutFor(widthClassFor(maxWidth.value.toInt()))
        when (layout) {
            NavLayout.BOTTOM_STRIP -> Column(modifier = Modifier.fillMaxSize()) {
                CompactHeader(
                    section = selected,
                    pill = pill,
                    onRefresh = onRefresh,
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    content(Modifier.fillMaxSize())
                }
                SectionNavStrip(
                    selected = selected,
                    onSelect = onSelect,
                    mesasOpenCount = mesasOpenCount,
                )
            }

            NavLayout.SIDE_RAIL -> Row(modifier = Modifier.fillMaxSize()) {
                SideRail(
                    selected = selected,
                    onSelect = onSelect,
                    mesasOpenCount = mesasOpenCount,
                    pill = pill,
                    themeMode = themeMode,
                    onRefresh = onRefresh,
                    onThemeModeChange = onThemeModeChange,
                    shopName = shopName,
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionTitleBlock(
                        section = selected,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        content(Modifier.widthIn(max = CONTENT_MAX_WIDTH_DP.dp).fillMaxSize())
                    }
                }
            }
        }
    }
}

// Compact header: ONE row. Title + subtitle take all the leftover width and
// ellipsize; the pill and the 48dp refresh button keep their natural size,
// so nothing overlaps the title even at 320dp.
@Composable
private fun CompactHeader(
    section: AppSection,
    pill: ConnectionPillState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = section.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MittLivePill(
                text = pill.text,
                live = pill.live,
                modifier = Modifier.livePulse(enabled = pill.live),
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                RefreshGlyph(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "Refrescar" },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// Expanded main header: display title plus muted subtitle (master Header).
@Composable
private fun SectionTitleBlock(section: AppSection, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = section.label,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = section.subtitle(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Permanent left rail (master sidebar): logo tile + shop name, vertical nav,
// then the CONECTADO pill, REFRESCAR and the theme cycle button. The upper
// part scrolls on short landscape windows so the action block stays reachable.
@Composable
private fun SideRail(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
    mesasOpenCount: Int,
    pill: ConnectionPillState,
    themeMode: ThemeMode,
    onRefresh: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    shopName: String?,
) {
    val name = shopDisplayName(shopName)
    Column(
        modifier = Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Servidor local",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (section in AppSection.entries) {
                    SectionNavRailItem(
                        section = section,
                        selected = section == selected,
                        onClick = { onSelect(section) },
                        mesasOpenCount = mesasOpenCount,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MittLivePill(
                text = pill.text,
                live = pill.live,
                modifier = Modifier.livePulse(enabled = pill.live),
            )
            MittSecondaryButton(
                label = "REFRESCAR",
                onClick = onRefresh,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            MittSecondaryButton(
                label = "TEMA: ${themeMode.label.uppercase()}",
                onClick = { onThemeModeChange(nextThemeMode(themeMode)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
