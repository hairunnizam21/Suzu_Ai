package io.suzuai.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.suzuai.app.ui.chat.PendingAttachment
import io.suzuai.app.ui.theme.SuzuColors
import java.io.File
import java.io.FileOutputStream

/**
 * Attach button — opens the system file picker (any MIME type) and copies
 * the chosen file into the app's cache so we have a stable path to upload.
 */
@Composable
fun AttachButton(
    onPicked: (File, mimeType: String?, displayName: String?) -> Unit,
) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val info = copyToCache(ctx, uri)
            if (info != null) onPicked(info.file, info.mimeType, info.displayName)
        }
    }
    IconButton(onClick = { launcher.launch("*/*") }) {
        Icon(
            Icons.Outlined.AttachFile,
            contentDescription = "Attach",
            tint = SuzuColors.Muted,
        )
    }
}

/** Horizontal strip of staged attachments shown above the composer. */
@Composable
fun AttachmentStrip(
    items: List<PendingAttachment>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items.size) { idx ->
            val pa = items[idx]
            AttachmentChip(
                name = pa.displayName,
                mime = pa.mimeType,
                onRemove = { onRemove(idx) },
            )
        }
    }
}

@Composable
private fun AttachmentChip(name: String, mime: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SuzuColors.SurfaceVariant)
            .border(1.dp, SuzuColors.Border, RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            iconForMime(mime) + " " + name,
            color = SuzuColors.OnSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 4.dp),
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove",
                tint = SuzuColors.Muted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun iconForMime(mime: String): String = when {
    mime.startsWith("image/") -> "🖼"
    mime.startsWith("video/") -> "🎬"
    mime.startsWith("audio/") -> "🎵"
    mime.contains("zip") || mime.contains("tar") || mime.contains("gzip") -> "🗜"
    mime == "application/vnd.android.package-archive" -> "📦"
    mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") -> "📄"
    else -> "📎"
}

private data class PickedFile(val file: File, val mimeType: String, val displayName: String)

private fun copyToCache(ctx: Context, uri: Uri): PickedFile? {
    val resolver = ctx.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    var displayName = "upload.bin"
    runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) {
                displayName = cursor.getString(nameIdx) ?: displayName
            }
        }
    }
    val outDir = File(ctx.cacheDir, "uploads").apply { mkdirs() }
    val out = File(outDir, "${System.currentTimeMillis()}-$displayName")
    return runCatching {
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        PickedFile(out, mime, displayName)
    }.getOrNull()
}
