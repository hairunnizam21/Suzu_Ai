package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Result returned by a save action — feeds the inline status text and the
 * toast popup so the user gets explicit confirmation that values landed.
 */
sealed interface SaveOutcome {
    data class Ok(val message: String = "Tersimpan") : SaveOutcome
    data class Fail(val message: String) : SaveOutcome
    data object Idle : SaveOutcome
}

/**
 * Stateful Save button used by every Settings card.
 *
 * Behaviour:
 *  - tap → calls [onSave] inside the card's scope and waits for the result
 *  - shows a spinner while running; disables itself
 *  - on success: toast + inline tick for 3s
 *  - on failure: toast + inline cross + persistent error text
 *
 * The card stays the source of truth for what to persist; this component only
 * cares about reporting the outcome.
 */
@Composable
fun SaveButton(
    label: String = "Save",
    enabled: Boolean = true,
    onSave: suspend () -> SaveOutcome,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<SaveOutcome>(SaveOutcome.Idle) }

    // Auto-clear success message after 3s so the card returns to idle state.
    LaunchedEffect(status) {
        if (status is SaveOutcome.Ok) {
            delay(3_000)
            status = SaveOutcome.Idle
        }
    }

    Button(
        onClick = {
            if (busy) return@Button
            scope.launch {
                busy = true
                status = SaveOutcome.Idle
                val result = runCatching { onSave() }.getOrElse {
                    SaveOutcome.Fail(it.message ?: it.toString())
                }
                busy = false
                status = result
                val msg = when (result) {
                    is SaveOutcome.Ok -> "✓ ${result.message}"
                    is SaveOutcome.Fail -> "✗ ${result.message}"
                    SaveOutcome.Idle -> ""
                }
                if (msg.isNotEmpty()) {
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }
            }
        },
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = SuzuColors.AccentCyan,
            contentColor = SuzuColors.Background,
            disabledContainerColor = SuzuColors.SurfaceVariant,
            disabledContentColor = SuzuColors.Muted,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = SuzuColors.Background,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }

    when (val s = status) {
        is SaveOutcome.Ok -> {
            Spacer(Modifier.height(8.dp))
            Text("✓ ${s.message}", color = SuzuColors.AccentCyan)
        }
        is SaveOutcome.Fail -> {
            Spacer(Modifier.height(8.dp))
            Text("✗ ${s.message}", color = SuzuColors.AccentRed)
        }
        SaveOutcome.Idle -> Unit
    }
}
