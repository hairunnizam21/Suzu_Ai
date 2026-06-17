package io.suzuai.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.suzuai.app.ui.theme.SuzuColors

@Composable
fun AboutCard() {
    val ctx = LocalContext.current
    Card(title = "About", icon = "ℹ️") {
        Text("Suzu_Ai · v0.1.0 (debug)", color = SuzuColors.OnSurface)
        Spacer(Modifier.height(4.dp))
        Text("Self-hosted Devin-style coding agent for Android.", color = SuzuColors.Muted)
        Spacer(Modifier.height(12.dp))
        Text(
            "github.com/hairunnizam21/Suzu_Ai",
            color = SuzuColors.AccentCyan,
            modifier = Modifier.clickable {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hairunnizam21/Suzu_Ai"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}
