package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

@Composable
fun ServerSection() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()
    val savedUrl by app.settings.serverUrl.collectAsState(initial = "")
    val savedToken by app.settings.serverToken.collectAsState(initial = "")
    val maxIter by app.settings.defaultMaxIterations.collectAsState(initial = 100)

    var url by remember { mutableStateOf(savedUrl) }
    var token by remember { mutableStateOf(savedToken) }
    var maxIterText by remember { mutableStateOf(maxIter.toString()) }
    var revealToken by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savedUrl) { url = savedUrl }
    LaunchedEffect(savedToken) { token = savedToken }
    LaunchedEffect(maxIter) { maxIterText = maxIter.toString() }

    Column {
        Text(
            "Server config",
            style = MaterialTheme.typography.titleMedium.copy(color = SuzuColors.AccentCyan),
        )
        Text(
            "URL and bearer token of your self-hosted Suzu server. Run install.sh on the server to get the token.",
            style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
            modifier = Modifier.padding(vertical = 8.dp),
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL  (e.g. http://192.168.1.10:8765)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = suzuFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Server token (SUZU_TOKEN)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = suzuFieldColors(),
            visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { revealToken = !revealToken }) {
                    Text(if (revealToken) "hide" else "show", color = SuzuColors.AccentCyan)
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = maxIterText,
            onValueChange = { maxIterText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Default max iterations  (was hard-capped at 12, now configurable)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = suzuFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                probe = "…"
                scope.launch {
                    val ok = runCatching { app.client.health() }.getOrDefault(false)
                    probe = if (ok) "reachable ✓" else "unreachable ✗"
                }
            }) { Text(probe ?: "Test", color = SuzuColors.AccentCyan) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val parsed = maxIterText.toIntOrNull()?.coerceIn(1, 500) ?: 100
                    scope.launch {
                        app.settings.setServer(url, token)
                        app.settings.setDefaultMaxIterations(parsed)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuzuColors.AccentCyan,
                    contentColor = Color.Black,
                ),
            ) { Text("Save") }
        }
    }
}
