package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

/**
 * Backend FastAPI server configuration: base URL + bearer token.
 *
 * Both fields are required for SuzuClient to function. The error
 * "Set server URL in Settings → Server config" is thrown by
 * [io.suzuai.app.data.SuzuClient.baseUrlOrThrow] when the URL is blank.
 */
@Composable
fun ServerConfigCard() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()

    val savedUrl by app.settings.serverUrl.collectAsState(initial = "")
    val savedToken by app.settings.serverToken.collectAsState(initial = "")
    val savedMaxIter by app.settings.defaultMaxIterations.collectAsState(initial = 100)

    var url by remember { mutableStateOf(savedUrl) }
    var token by remember { mutableStateOf(savedToken) }
    var tokenVisible by remember { mutableStateOf(false) }

    // Hydrate fields once persisted values arrive (DataStore reads are async).
    LaunchedEffect(savedUrl) { if (url.isBlank()) url = savedUrl }
    LaunchedEffect(savedToken) { if (token.isBlank()) token = savedToken }

    Card(title = "Server config", icon = "🖥️") {
        Text(
            "Backend FastAPI agent. Run install.sh on your VPS to get the SUZU_TOKEN.",
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

        Button(
            onClick = {
                scope.launch {
                    app.settings.setServer(
                        url = url,
                        token = token,
                        maxIter = savedMaxIter,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuzuColors.AccentCyan,
                contentColor = SuzuColors.Background,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Save server settings", fontWeight = FontWeight.SemiBold)
        }
    }
}
