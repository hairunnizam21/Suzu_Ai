package io.suzuai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.suzuai.app.ui.theme.SuzuColors

/**
 * Single-screen Settings layout: header + scrolling list of cards.
 *
 * Each card is self-contained: it reads its own slice of [SuzuApp.settings] and
 * persists with its own Save button. That keeps state local and avoids the
 * "form dirty across the whole screen" problem.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SuzuColors.Background)
            .statusBarsPadding(),
    ) {
        // ---- Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Menu, contentDescription = "Back", tint = SuzuColors.OnSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Suzu_Ai",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SuzuColors.AccentCyan,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text("Settings", color = SuzuColors.Muted)
            }
            // Refresh button kept for visual symmetry — it currently just no-ops.
            IconButton(onClick = { /* no-op */ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = SuzuColors.OnSurface)
            }
        }
        HorizontalDivider(color = SuzuColors.Border)

        // ---- Scrollable body
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            AiProviderCard()
            IterationsCard()
            RemoteShellCard()
            AppearanceCard()
            AboutCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}


