package io.suzuai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.chat.ChatScreen
import io.suzuai.app.ui.settings.SettingsScreen
import io.suzuai.app.ui.sidebar.Sidebar
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val app = SuzuApp.instance
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val chats by app.repository.observeChats().collectAsState(initial = emptyList())

    var screen by remember { mutableStateOf<Screen>(Screen.Chat(chatId = null)) }

    LaunchedEffect(Unit) {
        // Surface the most recent chat on cold start
        if (screen is Screen.Chat && (screen as Screen.Chat).chatId == null) {
            val mostRecent = chats.firstOrNull()
            if (mostRecent != null) screen = Screen.Chat(chatId = mostRecent.id)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SuzuColors.Surface,
                drawerTonalElevation = 0.dp,
            ) {
                Sidebar(
                    chats = chats,
                    activeChatId = (screen as? Screen.Chat)?.chatId,
                    onNewChat = {
                        screen = Screen.Chat(chatId = null)
                        scope.launch { drawerState.close() }
                    },
                    onOpenChat = { id ->
                        screen = Screen.Chat(chatId = id)
                        scope.launch { drawerState.close() }
                    },
                    onRename = { id, title ->
                        scope.launch { app.repository.renameChat(id, title) }
                    },
                    onDelete = { id ->
                        scope.launch {
                            app.repository.deleteChat(id)
                            if ((screen as? Screen.Chat)?.chatId == id) {
                                screen = Screen.Chat(chatId = null)
                            }
                        }
                    },
                    onOpenSettings = {
                        screen = Screen.Settings
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SuzuColors.Background),
        ) {
            when (val s = screen) {
                is Screen.Chat -> ChatScreen(
                    chatId = s.chatId,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onChatCreated = { newId -> screen = Screen.Chat(chatId = newId) },
                )
                Screen.Settings -> SettingsScreen(
                    onBack = { screen = Screen.Chat(chatId = chats.firstOrNull()?.id) },
                )
            }
        }
    }
}

sealed interface Screen {
    data class Chat(val chatId: String?) : Screen
    data object Settings : Screen
}
