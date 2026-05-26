package io.suzuai.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.suzuai.app.data.db.MessageEntity
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun MessageBubble(message: MessageEntity) {
    when (message.role) {
        "user" -> UserBubble(message)
        "tool" -> ToolBubble(message)
        else -> AssistantBubble(message)
    }
}

@Composable
private fun UserBubble(m: MessageEntity) {
    // User messages float to the right, like Claude / iMessage. They're the
    // shortest things in the chat so a plain rounded bubble is enough — no
    // header, no copy footer (the long-press menu can still copy).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp,
                    ),
                )
                .background(SuzuColors.UserBubble)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                m.content,
                style = MaterialTheme.typography.bodyMedium.copy(color = SuzuColors.OnSurface),
            )
        }
    }
}

@Composable
private fun AssistantBubble(m: MessageEntity) {
    // Assistant replies are left-aligned and full-width: prose flows as plain
    // text, fenced code blocks render as separate cards with their own copy
    // button (and a Preview button when the language is renderable).
    var previewBlock by remember(m.id) { mutableStateOf<PreviewableBlock?>(null) }
    val segments = remember(m.content) { splitIntoSegments(m.content) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        for (seg in segments) {
            when (seg) {
                is Segment.Text -> if (seg.text.isNotBlank()) {
                    Text(
                        seg.text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = SuzuColors.OnSurface),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                is Segment.Code -> {
                    CodeBlock(
                        language = seg.language,
                        code = seg.code,
                        previewable = isPreviewable(seg.language),
                        onPreview = { previewBlock = PreviewableBlock(seg.language, seg.code) },
                    )
                }
            }
        }
        if (m.toolCallsJson.length > 2) {
            ToolCallChips(rawJson = m.toolCallsJson)
        }
    }

    val pb = previewBlock
    if (pb != null) {
        PreviewDialog(
            title = pb.language ?: "code",
            html = buildPreviewHtml(pb.language, pb.code),
            onDismiss = { previewBlock = null },
        )
    }
}

@Composable
private fun ToolBubble(m: MessageEntity) {
    val icon = when (m.toolName) {
        "shell" -> Icons.Outlined.Terminal
        "read", "write", "edit", "grep", "ls" -> Icons.Outlined.Folder
        "apk_decompile", "apk_recompile", "apk_sign" -> Icons.Outlined.Build
        else -> Icons.Outlined.Terminal
    }
    val isError = m.content.startsWith("[ERROR]")
    BubbleContainer(
        bg = SuzuColors.ToolBubble,
        accent = if (isError) SuzuColors.AccentRed else SuzuColors.AccentCyan,
        leadingIcon = icon,
        header = "tool · ${m.toolName.orEmpty().ifBlank { "result" }}",
        body = m.content,
        codeStyle = true,
    )
}

@Composable
private fun BubbleContainer(
    bg: Color,
    accent: Color,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    header: String,
    body: String,
    codeStyle: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, SuzuColors.Border, RoundedCornerShape(12.dp))
            .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(leadingIcon, contentDescription = null, tint = accent, modifier = Modifier.padding(end = 6.dp))
            Text(
                header,
                style = MaterialTheme.typography.labelSmall.copy(color = accent),
            )
            Spacer(Modifier.weight(1f))
            CopyButton(
                copied = copied,
                onClick = {
                    copyToClipboard(ctx, header, body)
                    copied = true
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        if (codeStyle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SuzuColors.CodeBackground)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.OnSurface),
                )
            }
        } else {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium.copy(color = SuzuColors.OnSurface),
            )
        }
        extra()
    }
}

@Composable
private fun CodeBlock(
    language: String?,
    code: String,
    previewable: Boolean,
    onPreview: () -> Unit,
) {
    // A self-contained code card: header strip with language label + copy
    // (and Preview when applicable), then a monospace body in a darker box.
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SuzuColors.CodeBackground)
            .border(1.dp, SuzuColors.Border, RoundedCornerShape(10.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(SuzuColors.SurfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = language?.lowercase() ?: "code",
                style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.AccentCyan),
                modifier = Modifier.weight(1f),
            )
            if (previewable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onPreview)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        "▶ preview",
                        style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.AccentCyan),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            CopyButton(
                copied = copied,
                onClick = {
                    copyToClipboard(ctx, language ?: "code", code)
                    copied = true
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.OnSurface),
            )
        }
    }
}

@Composable
private fun CopyButton(copied: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        if (copied) {
            Icon(Icons.Outlined.Done, contentDescription = null, tint = SuzuColors.AccentGreen, modifier = Modifier.width(14.dp).height(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("copied", style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.AccentGreen))
        } else {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = SuzuColors.Muted, modifier = Modifier.width(14.dp).height(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("copy", style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.Muted))
        }
    }
}

@Composable
private fun ToolCallChips(rawJson: String) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val items = remember(rawJson) {
        runCatching {
            json.parseToJsonElement(rawJson).let { el ->
                val arr = (el as? kotlinx.serialization.json.JsonArray) ?: return@let emptyList<Pair<String, String>>()
                arr.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullSafe() ?: "?"
                    val input = (obj["input"] as? JsonObject)?.toString() ?: "{}"
                    name to input
                }
            }
        }.getOrDefault(emptyList())
    }
    if (items.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    items.forEach { (name, input) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SuzuColors.SurfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = SuzuColors.AccentCyan, modifier = Modifier.width(14.dp).height(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "$name $input",
                style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.OnSurface),
            )
        }
    }
}

@Composable
fun LiveDeltaBubble(delta: LiveDelta) {
    when (delta) {
        is LiveDelta.Text -> {
            // Streaming text + animated blinking caret to show progress.
            val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "caret")
            val alpha by infinite.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(durationMillis = 600),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "caret-alpha",
            )
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    delta.text,
                    style = MaterialTheme.typography.bodyMedium.copy(color = SuzuColors.OnSurface),
                )
                Text(
                    "▌",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = SuzuColors.AccentCyan.copy(alpha = alpha),
                    ),
                )
            }
        }
        is LiveDelta.ToolStart -> {
            ToolRunningBubble(name = delta.name, input = delta.input.toString())
        }
        is LiveDelta.Iteration -> {
            ThinkingDots(label = "iteration ${delta.n}${if (delta.max > 0) "/${delta.max}" else ""}")
        }
    }
}

@Composable
private fun ThinkingDots(label: String) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 900),
        ),
        label = "dots-phase",
    )
    val visibleDots = phase.toInt().coerceIn(0, 3)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$label · thinking" + ".".repeat(visibleDots),
            style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.AccentCyan),
        )
    }
}

@Composable
private fun ToolRunningBubble(name: String, input: String) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SuzuColors.ToolBubble)
            .border(1.dp, SuzuColors.AccentYellow.copy(alpha = alpha), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Terminal,
                contentDescription = null,
                tint = SuzuColors.AccentYellow.copy(alpha = alpha),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "running tool · $name",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SuzuColors.AccentYellow,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            input.take(200),
            style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
        )
    }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** A code block we can render in the canvas WebView. */
data class PreviewableBlock(val language: String?, val code: String)

/**
 * Single segment of an assistant message: either prose [Text] or a fenced
 * code block [Code]. Used by [AssistantBubble] so prose renders as flowing
 * text and code renders as a card with its own copy button.
 */
sealed interface Segment {
    data class Text(val text: String) : Segment
    data class Code(val language: String?, val code: String) : Segment
}

/**
 * Split a markdown-flavoured assistant reply into prose / code segments by
 * walking triple-backtick fences. Anything outside a fence becomes [Text],
 * each fence becomes [Code]. The split is intentionally tolerant — a
 * mismatched fence just folds back into prose.
 */
fun splitIntoSegments(text: String): List<Segment> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<Segment>()
    val fence = Regex("```([a-zA-Z0-9_+\\-]+)?[ \\t]*\\r?\\n([\\s\\S]*?)```")
    var cursor = 0
    fence.findAll(text).forEach { m ->
        if (m.range.first > cursor) {
            out.add(Segment.Text(text.substring(cursor, m.range.first).trim('\n')))
        }
        val lang = m.groupValues[1].ifBlank { null }
        out.add(Segment.Code(language = lang, code = m.groupValues[2]))
        cursor = m.range.last + 1
    }
    if (cursor < text.length) {
        out.add(Segment.Text(text.substring(cursor).trim('\n')))
    }
    return out.filter {
        when (it) {
            is Segment.Text -> it.text.isNotEmpty()
            is Segment.Code -> true
        }
    }
}

/**
 * Find triple-backtick fenced code blocks whose language matches [isPreviewable].
 * Returns them in document order. Falls back to detecting raw `<html>` /
 * `<svg>` blocks even without fences.
 */
fun extractPreviewableBlocks(text: String): List<PreviewableBlock> {
    if (text.isBlank()) return emptyList()
    val out = mutableListOf<PreviewableBlock>()
    val fence = Regex("```([a-zA-Z0-9]+)?\\s*\\n([\\s\\S]*?)```")
    fence.findAll(text).forEach { m ->
        val lang = m.groupValues[1].ifBlank { null }
        val body = m.groupValues[2]
        if (isPreviewable(lang)) {
            out.add(PreviewableBlock(language = lang, code = body))
        } else if (lang == null) {
            // Sometimes the agent forgets to mark the language
            val trimmed = body.trim()
            if (trimmed.startsWith("<!doctype", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true) ||
                trimmed.startsWith("<svg", ignoreCase = true)
            ) {
                out.add(PreviewableBlock(language = "html", code = body))
            }
        }
    }
    return out
}

@Composable
private fun PreviewChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SuzuColors.AccentCyan.copy(alpha = 0.15f))
            .border(1.dp, SuzuColors.AccentCyan, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "▶ $label",
            style = MaterialTheme.typography.labelMedium.copy(color = SuzuColors.AccentCyan),
        )
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()
