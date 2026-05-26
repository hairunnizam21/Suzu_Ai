package io.suzuai.app.ui.sidebar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.suzuai.app.data.db.ChatEntity
import io.suzuai.app.ui.theme.SuzuColors

/**
 * Sidebar layout (top → bottom):
 *
 *  1.  Suzu logo + app title (header)
 *  2.  [+ New Chat] button (prominent)
 *  3.  HISTORY label
 *  4.  Scrollable list of past chats — tap to resume, long-press for rename/delete
 *  5.  Divider
 *  6.  [⚙ Settings] row at the very bottom
 */
@Composable
fun Sidebar(
    chats: List<ChatEntity>,
    activeChatId: String?,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var renaming by remember { mutableStateOf<ChatEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(SuzuColors.Surface)) {

        // Header — animated slide in from the left
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(durationMillis = 280),
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Suzu",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = SuzuColors.AccentCyan,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "lite",
                    style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.Muted),
                )
            }
        }

        // New chat button — animated slide-in
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { -it / 4 },
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
            ) + fadeIn(animationSpec = tween(220)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SuzuColors.AccentCyan.copy(alpha = 0.12f))
                    .border(1.dp, SuzuColors.AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onNewChat)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = SuzuColors.AccentCyan,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "New chat",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = SuzuColors.AccentCyan,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // History label — fade in
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(durationMillis = 350, delayMillis = 100)),
        ) {
            Text(
                "Recent",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SuzuColors.Muted,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 4.dp),
            )
        }

        // Scrollable history list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (chats.isEmpty()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400, delayMillis = 200)),
                ) {
                    Text(
                        "No chats yet — tap \"New chat\" to start.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = tween(durationMillis = 220),
                            ) + fadeIn(animationSpec = tween(220)),
                        ) {
                            ChatRow(
                                chat = chat,
                                active = chat.id == activeChatId,
                                onOpen = { onOpenChat(chat.id) },
                                onRename = { renaming = chat },
                                onDelete = { onDelete(chat.id) },
                            )
                        }
                    }
                }
            }
        }

        Divider(color = SuzuColors.Border.copy(alpha = 0.5f), thickness = 1.dp)

        // Config JSON — quick import/export so the user doesn't have to open
        // the Settings screen for routine setup.
        Spacer(Modifier.height(8.dp))
        ConfigJsonCard()
        Spacer(Modifier.height(8.dp))

        // Settings row (bottom) — animated, more compact
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = SuzuColors.Muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.bodyMedium.copy(color = SuzuColors.OnSurface),
            )
        }
    }

    val chatToRename = renaming
    if (chatToRename != null) {
        var newTitle by remember(chatToRename.id) { mutableStateOf(chatToRename.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) onRename(chatToRename.id, newTitle.trim())
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            containerColor = SuzuColors.Surface,
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatEntity,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) SuzuColors.SurfaceVariant else SuzuColors.Surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = if (active) SuzuColors.AccentCyan else SuzuColors.Muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            chat.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (active) SuzuColors.OnSurface else SuzuColors.Muted,
            ),
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "More",
                    tint = SuzuColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}
