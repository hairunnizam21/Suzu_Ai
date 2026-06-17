package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.data.PingResult
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.flow.first

/**
 * Backend FastAPI server configuration: base URL + bearer token.
 *
 * Save flow now persists then immediately probes the server. Success surfaces
 * a toast like "✓ Tersambung ke server v0.2.0"; failure shows the underlying
 * HTTP / network error so the user can fix the URL or token.
 */
@Composable
fun ServerConfigCard() {
    val app = SuzuApp.instance

    val savedUrl by app.settings.serverUrl.collectAsState(initial = "")
    val savedToken by app.settings.serverToken.collectAsState(initial = "")
    val savedMaxIter by app.settings.defaultMaxIterations.collectAsState(initial = 100)

    var url by remember { mutableStateOf(savedUrl) }
    var token by remember { mutableStateOf(savedToken) }
    var tokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(savedUrl) { if (url.isBlank()) url = savedUrl }
    LaunchedEffect(savedToken) { if (token.isBlank()) token = savedToken }

    Card(title = "Server config", icon = "🖥️") {
        Text(
            "Backend FastAPI agent. Run install.sh on your VPS to get the SUZU_TOKEN. " +
                "Save akan check sambungan secara automatik.",
            color = SuzuColors.Muted,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL", color = SuzuColors.Muted) },
            placeholder = { Text("http://your-vps:8765", color = SuzuColors.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("SUZU_TOKEN", color = SuzuColors.Muted) },
            placeholder = { Text("paste token from install.sh output", color = SuzuColors.Muted) },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Text(if (tokenVisible) "🙈" else "👁", color = SuzuColors.Muted)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(16.dp))

        SaveButton(label = "Save & test connection") {
            // 1. Validation
            if (url.isBlank()) return@SaveButton SaveOutcome.Fail("Server URL kosong")
            if (token.isBlank()) return@SaveButton SaveOutcome.Fail("Token kosong")
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@SaveButton SaveOutcome.Fail("URL kena mula http:// atau https://")
            }

            // 2. Persist
            app.settings.setServer(
                url = url,
                token = token,
                maxIter = savedMaxIter,
            )

            // 3. Verify by reading back
            val readUrl = app.settings.serverUrl.first()
            val readToken = app.settings.serverToken.first()
            if (readUrl != url.trim() || readToken != token.trim()) {
                return@SaveButton SaveOutcome.Fail("DataStore tak simpan ikut yang dimasukkan")
            }

            // 4. Probe the server with the new credentials
            when (val ping = app.client.ping()) {
                is PingResult.Ok -> SaveOutcome.Ok("Tersambung ke server v${ping.version}")
                is PingResult.Error -> SaveOutcome.Fail("Tersimpan tapi sambungan gagal: ${ping.message}")
            }
        }
    }
}
