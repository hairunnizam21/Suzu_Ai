package io.suzuai.app.ui.sidebar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import io.suzuai.app.SuzuApp
import io.suzuai.app.data.ConfigJson
import io.suzuai.app.data.SuzuConfig
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

/**
 * Collapsible "Config JSON" card embedded in the sidebar. Lets the user
 * snapshot their current settings to JSON, paste a saved JSON to apply
 * everything at once, and copy/share the snapshot. Replaces the need to
 * tour the Settings screen for routine setup.
 */
@Composable
fun ConfigJsonCard() {
    val app = SuzuApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            kotlinx.coroutines.delay(2500)
            statusMsg = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SuzuColors.SurfaceVariant)
            .border(1.dp, SuzuColors.Border, RoundedCornerShape(12.dp)),
    ) {
        // Header — tap to expand/collapse
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!open) {
                        // Pre-fill with current snapshot when opening
                        scope.launch {
                            jsonText = ConfigJson.encodeToString(
                                SuzuConfig.serializer(),
                                app.settings.exportConfig(),
                            )
                        }
                    }
                    open = !open
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.Code,
                contentDescription = null,
                tint = SuzuColors.AccentCyan,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Config JSON",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SuzuColors.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (open) "▾" else "▸",
                color = SuzuColors.Muted,
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                Text(
                    "Paste a Suzu config JSON to apply every setting at once. The Export button snapshots your current values.",
                    style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.Muted),
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuzuColors.CodeBackground)
                        .border(1.dp, SuzuColors.Border, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.OnSurface),
                    cursorBrush = SolidColor(SuzuColors.AccentCyan),
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActionChip(label = "Export", icon = Icons.Outlined.Save, onClick = {
                        scope.launch {
                            jsonText = ConfigJson.encodeToString(
                                SuzuConfig.serializer(),
                                app.settings.exportConfig(),
                            )
                            statusMsg = "Snapshot ready — copy below"
                        }
                    })
                    Spacer(Modifier.width(6.dp))
                    ActionChip(label = "Copy", icon = Icons.Outlined.ContentCopy, onClick = {
                        copyToClipboard(ctx, "suzu-config", jsonText)
                        Toast.makeText(ctx, "Copied JSON", Toast.LENGTH_SHORT).show()
                    })
                    Spacer(Modifier.width(6.dp))
                    ActionChip(label = "Apply", icon = Icons.Outlined.ContentPaste, onClick = {
                        scope.launch {
                            val ok = runCatching {
                                val cfg = ConfigJson.decodeFromString(SuzuConfig.serializer(), jsonText)
                                app.settings.importConfig(cfg)
                                cfg
                            }
                            if (ok.isSuccess) {
                                statusMsg = "✓ Config applied"
                                Toast.makeText(ctx, "✓ Config applied", Toast.LENGTH_SHORT).show()
                            } else {
                                val msg = ok.exceptionOrNull()?.message ?: "Invalid JSON"
                                statusMsg = "✗ $msg"
                                Toast.makeText(ctx, "✗ $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                }
                statusMsg?.let { msg ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (msg.startsWith("✗")) SuzuColors.AccentRed else SuzuColors.AccentCyan,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SuzuColors.AccentCyan.copy(alpha = 0.12f))
            .border(1.dp, SuzuColors.AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = SuzuColors.AccentCyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = SuzuColors.AccentCyan,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
