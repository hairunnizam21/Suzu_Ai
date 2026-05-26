package io.suzuai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

private val STYLES = listOf(
    "minimal" to ("Minimal Pro" to "Clean, focused"),
    "glass" to ("Glassmorphism" to "Soft, premium"),
    "hacker" to ("Hacker Terminal" to "Mono, terminal"),
    "material" to ("Material You" to "Android, friendly"),
)

private val MODES = listOf(
    "light" to "Light",
    "dark" to "Dark",
    "system" to "System",
)

@Composable
fun AppearanceCard() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()
    val savedStyle by app.settings.themeStyle.collectAsState(initial = "hacker")
    val savedMode by app.settings.themeMode.collectAsState(initial = "dark")

    Card(title = "Appearance", icon = "🎨") {
        Text("Style", color = SuzuColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
        // 2 x 2 grid of style cards
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                STYLES.take(2).forEachIndexed { idx, (key, labels) ->
                    StyleTile(
                        title = labels.first,
                        subtitle = labels.second,
                        selected = savedStyle == key,
                        onClick = { scope.launch { app.settings.setTheme(key, savedMode) } },
                        modifier = Modifier.weight(1f),
                    )
                    if (idx == 0) Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                STYLES.drop(2).forEachIndexed { idx, (key, labels) ->
                    StyleTile(
                        title = labels.first,
                        subtitle = labels.second,
                        selected = savedStyle == key,
                        onClick = { scope.launch { app.settings.setTheme(key, savedMode) } },
                        modifier = Modifier.weight(1f),
                    )
                    if (idx == 0) Spacer(Modifier.width(8.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("Mode", color = SuzuColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MODES.forEachIndexed { idx, (key, label) ->
                ModeChip(
                    label = label,
                    selected = savedMode == key,
                    onClick = { scope.launch { app.settings.setTheme(savedStyle, key) } },
                    modifier = Modifier.weight(1f),
                )
                if (idx < MODES.lastIndex) Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Pilihan visual style aktif disimpan, namun rendering visual penuh untuk semua 4 style sedang dalam pembangunan — sementara ini app guna Hacker Terminal.",
            color = SuzuColors.Muted,
        )
    }
}

@Composable
private fun StyleTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) SuzuColors.AccentCyan else SuzuColors.Border
    val bg = if (selected) SuzuColors.SurfaceVariant else SuzuColors.Surface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Text(title, color = SuzuColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = SuzuColors.Muted)
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) SuzuColors.AccentCyan else SuzuColors.SurfaceVariant
    val content = if (selected) SuzuColors.Background else SuzuColors.OnSurface
    val border = if (selected) SuzuColors.AccentCyan else SuzuColors.Border
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontWeight = FontWeight.SemiBold)
    }
}
