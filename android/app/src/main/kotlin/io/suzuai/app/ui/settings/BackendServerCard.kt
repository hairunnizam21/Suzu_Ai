package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

@Composable
fun BackendServerCard() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()

    val savedUrl by app.settings.serverUrl.collectAsState(initial = "")
    val savedToken by app.settings.serverToken.collectAsState(initial = "")
    val savedMaxIter by app.settings.defaultMaxIterations.collectAsState(initial = 100)

    var url by remember { mutableStateOf(savedUrl) }
    var token by remember { mutableStateOf(savedToken) }
    var maxIterText by remember { mutableStateOf(savedMaxIter.toString()) }
    var tokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(savedUrl, savedToken, savedMaxIter) {
        if (url.isBlank()) url = savedUrl
        if (token.isBlank()) token = savedToken
        if (maxIterText.isBlank() || maxIterText == "100") maxIterText = savedMaxIter.toString()
    }

    Card(title = "Backend Server (HTTP/SSE)", icon = "🌐") {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL", color = SuzuColors.Muted) },
            placeholder = { Text("http://143.198.211.224:8765", color = SuzuColors.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Server token", color = SuzuColors.Muted) },
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
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = maxIterText,
            onValueChange = { txt ->
                maxIterText = txt.filter { it.isDigit() }.take(4)
            },
            label = { Text("Default max iterations (1 – 500)", color = SuzuColors.Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    val n = maxIterText.toIntOrNull()?.coerceIn(1, 500) ?: 100
                    app.settings.setServer(url = url, token = token, maxIter = n)
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
