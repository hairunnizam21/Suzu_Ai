package io.suzuai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

/**
 * Single-screen Settings layout: header + status pill + scrolling list of cards.
 *
 * Each card is self-contained: it reads its own slice of [SuzuApp.settings] and
 * persists with its own Save button. That keeps state local and avoids the
 * "form dirty across the whole screen" problem.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()
    val serverUrl by app.settings.serverUrl.collectAsState(initial = "")
    val serverToken by app.settings.serverToken.collectAsState(initial = "")

    var connectionState by remember { mutableStateOf(ConnectionState.Unknown) }
    var testing by remember { mutableStateOf(false) }

    fun runHealthCheck() {
        if (testing) return
        testing = true
        connectionState = ConnectionState.Unknown
        scope.launch {
            connectionState = try {
                val ok = app.client.health()
                if (ok) ConnectionState.Connected else ConnectionState.Failed
            } catch (_: Throwable) {
                ConnectionState.Failed
            } finally {
                testing = false
            }
        }
    }

    // First time we have both a URL and token, probe automatically.
    LaunchedEffect(serverUrl, serverToken) {
        if (serverUrl.isNotBlank() && serverToken.isNotBlank() && connectionState == ConnectionState.Unknown) {
            runHealthCheck()
        }
    }

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
            IconButton(onClick = ::runHealthCheck) {
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
            // Status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    ConnectionState.Connected -> SuzuColors.AccentGreen
                                    ConnectionState.Failed -> SuzuColors.AccentRed
                                    ConnectionState.Unknown -> SuzuColors.Muted
                                },
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (connectionState) {
                            ConnectionState.Connected -> "Connected"
                            ConnectionState.Failed -> "Not connected"
                            ConnectionState.Unknown -> if (testing) "Testing…" else "Belum cuba"
                        },
                        color = SuzuColors.OnSurface,
                    )
                }
                OutlinedButton(
                    onClick = ::runHealthCheck,
                    enabled = !testing && serverUrl.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuzuColors.AccentCyan),
                ) {
                    Text("Test", color = SuzuColors.AccentCyan, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))

            AiProviderCard()
            BackendServerCard()
            RemoteShellCard()
            AppearanceCard()
            AboutCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class ConnectionState { Unknown, Connected, Failed }
