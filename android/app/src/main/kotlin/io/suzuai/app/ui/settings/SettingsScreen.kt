package io.suzuai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.suzuai.app.ui.theme.SuzuColors

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SuzuColors.Background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = SuzuColors.OnSurface,
                )
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = SuzuColors.AccentCyan,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Divider(color = SuzuColors.Border)
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            AccountSection()
            SectionDivider()
            ProvidersSection()
            SectionDivider()
            ServerSection()
        }
    }
}

@Composable
private fun SectionDivider() {
    Divider(color = SuzuColors.Border, modifier = Modifier.padding(vertical = 16.dp))
}
