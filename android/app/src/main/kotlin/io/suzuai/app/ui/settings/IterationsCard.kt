package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

/**
 * Tiny card that only exposes the agent's `max_iterations` setting.
 *
 * The full Backend Server (URL/Token) configuration intentionally lives in the
 * settings store but is no longer surfaced in the UI — the user asked for a
 * simpler Settings page that only carries the iteration count plus the AI
 * provider + SSH details.
 */
@Composable
fun IterationsCard() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()
    val saved by app.settings.defaultMaxIterations.collectAsState(initial = 100)

    var text by remember { mutableStateOf(saved.toString()) }
    LaunchedEffect(saved) { if (text == "100") text = saved.toString() }

    Card(title = "Agent iterations", icon = "🔁") {
        Text(
            "Berapa banyak tool call agent boleh buat dalam satu turn sebelum berhenti.",
            color = SuzuColors.Muted,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { txt -> text = txt.filter { it.isDigit() }.take(4) },
            label = { Text("Max iterations (1 – 500)", color = SuzuColors.Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    val n = text.toIntOrNull()?.coerceIn(1, 500) ?: 100
                    app.settings.setDefaultMaxIterations(n)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuzuColors.AccentCyan,
                contentColor = SuzuColors.Background,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Save iterations", fontWeight = FontWeight.SemiBold)
        }
    }
}
