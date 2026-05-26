package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.flow.first

/**
 * Agent iteration cap — with an Unlimited toggle that maps to `max_iterations=0`
 * on the wire, telling the server to skip the iteration cap entirely.
 */
@Composable
fun IterationsCard() {
    val app = SuzuApp.instance
    val savedIter by app.settings.defaultMaxIterations.collectAsState(initial = 100)
    val savedUnlimited by app.settings.defaultMaxIterationsUnlimited.collectAsState(initial = false)

    var text by remember { mutableStateOf(savedIter.toString()) }
    var unlimited by remember { mutableStateOf(savedUnlimited) }

    LaunchedEffect(savedIter) { text = savedIter.toString() }
    LaunchedEffect(savedUnlimited) { unlimited = savedUnlimited }

    Card(title = "Agent iterations", icon = "🔁") {
        Text(
            "Berapa banyak tool call agent boleh buat dalam satu turn sebelum berhenti. " +
                "Hidupkan Unlimited untuk biarkan agent berjalan sampai dia putuskan sendiri dah selesai.",
            color = SuzuColors.Muted,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Unlimited", color = SuzuColors.OnSurface, modifier = Modifier.weight(1f))
            Switch(
                checked = unlimited,
                onCheckedChange = { unlimited = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SuzuColors.Background,
                    checkedTrackColor = SuzuColors.AccentCyan,
                    uncheckedThumbColor = SuzuColors.OnSurface,
                    uncheckedTrackColor = SuzuColors.Border,
                ),
            )
        }

        if (!unlimited) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { txt -> text = txt.filter { it.isDigit() }.take(5) },
                label = { Text("Max iterations (1 – 10000)", color = SuzuColors.Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        }
        Spacer(Modifier.height(12.dp))

        SaveButton(label = "Save iterations") {
            if (!unlimited && (text.toIntOrNull() ?: 0) <= 0) {
                return@SaveButton SaveOutcome.Fail("Iterations must be a positive number")
            }
            val n = text.toIntOrNull()?.coerceIn(1, 10_000) ?: 100
            app.settings.setDefaultMaxIterations(n, unlimited)
            // Verify by re-reading
            val readBack = app.settings.defaultMaxIterations.first()
            val unlimRead = app.settings.defaultMaxIterationsUnlimited.first()
            if (unlimRead == unlimited && (unlimited || readBack == n)) {
                SaveOutcome.Ok(if (unlimited) "Unlimited iterations active" else "Saved: $n iterations")
            } else {
                SaveOutcome.Fail("Verifikasi gagal — sila cuba lagi")
            }
        }
    }
}
