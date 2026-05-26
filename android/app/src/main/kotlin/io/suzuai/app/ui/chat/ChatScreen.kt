package io.suzuai.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.suzuai.app.ui.theme.SuzuColors
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(
    chatId: String?,
    onOpenDrawer: () -> Unit,
    onChatCreated: (String) -> Unit,
) {
    val vm: ChatViewModel = viewModel(
        key = "chat-${chatId.orEmpty()}",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(chatId) as T
        },
    )
    val state by vm.state.collectAsState()
    val messages by vm.messages.collectAsState(initial = emptyList())
    val liveDelta by vm.liveDelta.collectAsState()
    val attachments by vm.pendingAttachments.collectAsState()
    val listState = rememberLazyListState()

    // Initial jump-to-bottom when an old chat is opened — no animation so it
    // feels instant; the user shouldn't have to scroll manually.
    var initialScrollDone by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(messages.size, chatId) {
        if (!initialScrollDone && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
            initialScrollDone = true
        }
    }

    // Subsequent updates animate smoothly to the latest event.
    LaunchedEffect(messages.size, liveDelta) {
        if (!initialScrollDone) return@LaunchedEffect
        val target = messages.size + (if (liveDelta != null) 1 else 0) - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SuzuColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Menu, contentDescription = "Sidebar", tint = SuzuColors.OnSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Suzu_Ai",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SuzuColors.AccentCyan,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                val status = when {
                    state.busy -> "thinking…"
                    state.activeProvider != null -> "${state.activeProvider!!.name} · ${state.activeProvider!!.model}"
                    else -> "no provider"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.Muted),
                )
            }
        }

        // Messages
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && liveDelta == null) {
                EmptyChatHint(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                ) {
                    items(messages, key = { it.id }) { m -> MessageBubble(message = m) }
                    if (liveDelta != null) {
                        item(key = "live") { LiveDeltaBubble(delta = liveDelta!!) }
                    }
                }
            }
        }

        // Error banner
        AnimatedVisibility(visible = state.error != null) {
            val err = state.error ?: ""
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SuzuColors.AccentRed.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "⚠️  $err",
                    style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.AccentRed),
                )
            }
        }

        // Input bar
        Composer(
            busy = state.busy,
            attachments = attachments,
            onSend = { vm.send(it, onChatCreated) },
            onInject = { vm.inject(it) },
            onStop = vm::cancel,
            onAttach = { f, mime, name -> vm.addAttachment(f, mime, name) },
            onRemoveAttachment = vm::removeAttachment,
        )
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Suzu_Ai",
            style = MaterialTheme.typography.displayLarge.copy(color = SuzuColors.AccentCyan),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "// devin-style agent on your phone",
            style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Configure your server + API providers in Settings,\nthen ask anything below.",
            style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Composer(
    busy: Boolean,
    attachments: List<PendingAttachment>,
    onSend: (String) -> Unit,
    onInject: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: (java.io.File, String?, String?) -> Boolean,
    onRemoveAttachment: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (busy) {
            // Hint banner — clarifies that typing while busy injects rather
            // than starting a new turn. Helps users understand the new flow.
            Text(
                "Agent sedang kerja. Hantar mesej untuk tambah arahan / pembetulan.",
                style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.AccentCyan),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Pending attachments strip — chips above the input bar
        if (attachments.isNotEmpty()) {
            AttachmentStrip(
                items = attachments,
                onRemove = onRemoveAttachment,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SuzuColors.Surface)
                .border(1.dp, SuzuColors.Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachButton(
                onPicked = { f, mime, name -> onAttach(f, mime, name) },
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 200.dp)
                    .padding(vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = SuzuColors.OnSurface),
                cursorBrush = SolidColor(SuzuColors.AccentCyan),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            if (busy) "Tambah arahan…" else "Ask anything…",
                            style = MaterialTheme.typography.bodyLarge.copy(color = SuzuColors.Muted),
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(4.dp))

            // Send button is always available — when busy it injects mid-flight.
            val canSend = text.isNotBlank()
            IconButton(
                onClick = {
                    if (!canSend) return@IconButton
                    val toSend = text.trim()
                    text = ""
                    if (busy) onInject(toSend) else onSend(toSend)
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (canSend) SuzuColors.AccentCyan else SuzuColors.SurfaceVariant),
            ) {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = if (busy) "Inject" else "Send",
                    tint = if (canSend) Color.Black else SuzuColors.Muted,
                )
            }
            if (busy) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SuzuColors.AccentRed),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = Color.Black)
                }
            }
        }
    }
}
