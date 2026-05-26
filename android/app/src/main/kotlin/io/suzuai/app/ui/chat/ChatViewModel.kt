package io.suzuai.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.suzuai.app.SuzuApp
import io.suzuai.app.data.ApiMessage
import io.suzuai.app.data.ChatRepository
import io.suzuai.app.data.ChatRequest
import io.suzuai.app.data.ProviderProfile
import io.suzuai.app.data.SettingsStore
import io.suzuai.app.data.SuzuClient
import io.suzuai.app.data.asJsonObjectOrNull
import io.suzuai.app.data.asStringOrNull
import io.suzuai.app.data.asIntOrNull
import io.suzuai.app.data.asBooleanOrNull
import io.suzuai.app.data.db.MessageEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One ViewModel manages a single chat (creating or resuming). The UI listens
 * to [state] and [messages]. Sending a turn streams events from the server
 * and incrementally appends a live "assistant" message + zero or more
 * "tool" messages.
 */
class ChatViewModel(initialChatId: String?) : ViewModel() {

    private val app = SuzuApp.instance
    private val repo: ChatRepository = app.repository
    private val client: SuzuClient = app.client
    private val settings: SettingsStore = app.settings
    private val json: Json = client.json

    private val _state = MutableStateFlow(ChatUiState(chatId = initialChatId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _liveDelta = MutableStateFlow<LiveDelta?>(null)
    val liveDelta: StateFlow<LiveDelta?> = _liveDelta.asStateFlow()

    /** Persisted messages for the current chat (or empty until first send). */
    val messages: StateFlow<List<MessageEntity>> = _state
        .map { it.chatId }
        .let { chatIds ->
            kotlinx.coroutines.flow.flow {
                emit(emptyList<MessageEntity>())
                chatIds.collect { id ->
                    if (id == null) emit(emptyList())
                    else repo.observeMessages(id).collect { msgs -> emit(msgs) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val activeId = settings.activeProviderId.first()
            val profiles = settings.providerProfiles.first()
            val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
            _state.value = _state.value.copy(activeProvider = active, profilesAvailable = profiles.size)
        }
    }

    fun refreshActiveProvider() {
        viewModelScope.launch {
            val activeId = settings.activeProviderId.first()
            val profiles = settings.providerProfiles.first()
            val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
            _state.value = _state.value.copy(activeProvider = active, profilesAvailable = profiles.size)
        }
    }

    fun cancel() {
        streamJob?.cancel()
        streamJob = null
        _state.value = _state.value.copy(busy = false)
        _liveDelta.value = null
    }

    fun send(prompt: String, onCreatedNewChat: (String) -> Unit = {}) {
        if (prompt.isBlank() || _state.value.busy) return
        val provider = _state.value.activeProvider
        if (provider == null) {
            _state.value = _state.value.copy(error = "No provider configured. Open Settings → API Providers.")
            return
        }
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val chatId = _state.value.chatId
                // Build the message list from persisted history (if any) + the new prompt
                val historyMsgs: List<ApiMessage> = if (chatId != null) {
                    repo.observeMessages(chatId).first().map(::toApiMessage)
                } else emptyList()
                val newUser = ApiMessage(role = "user", content = prompt)

                val request = ChatRequest(
                    chatId = chatId,
                    title = if (chatId == null) prompt.lineSequence().first().take(60) else null,
                    provider = provider,
                    maxIterations = settings.defaultMaxIterations.first(),
                    messages = historyMsgs + newUser,
                )

                // Persist the user message *now* so the UI shows it immediately
                val ensuredChatId = chatId ?: java.util.UUID.randomUUID().toString().replace("-", "")
                if (chatId == null) {
                    repo.ensureChat(ensuredChatId, title = request.title.orEmpty().ifBlank { "New chat" })
                    _state.value = _state.value.copy(chatId = ensuredChatId)
                    onCreatedNewChat(ensuredChatId)
                }
                repo.appendMessage(ensuredChatId, role = "user", content = prompt)

                consumeStream(request, ensuredChatId)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: t.toString())
            } finally {
                _state.value = _state.value.copy(busy = false)
                _liveDelta.value = null
            }
        }
    }

    private suspend fun consumeStream(request: ChatRequest, ensuredChatId: String) {
        val assistantBuf = StringBuilder()
        val pendingToolCalls = mutableListOf<JsonObject>()
        var serverChatId: String = ensuredChatId

        client.streamChat(request).collect { ev ->
            val obj = ev.raw
            when (obj["type"].asStringOrNull()) {
                "chat" -> {
                    val id = obj["chat_id"].asStringOrNull() ?: return@collect
                    serverChatId = id
                    if (id != ensuredChatId) {
                        repo.ensureChat(id, title = request.title.orEmpty().ifBlank { "New chat" })
                        _state.value = _state.value.copy(chatId = id)
                    }
                }
                "delta" -> {
                    val text = obj["text"].asStringOrNull() ?: return@collect
                    assistantBuf.append(text)
                    _liveDelta.value = LiveDelta.Text(assistantBuf.toString())
                }
                "tool_call" -> {
                    val id = obj["id"].asStringOrNull().orEmpty()
                    val name = obj["name"].asStringOrNull().orEmpty()
                    val input = obj["input"].asJsonObjectOrNull() ?: JsonObject(emptyMap())
                    val tc = buildJsonObject {
                        put("id", id); put("name", name)
                        put("input", input)
                    }
                    pendingToolCalls += tc
                    _liveDelta.value = LiveDelta.ToolStart(name = name, input = input)
                }
                "tool_result" -> {
                    val flushed = assistantBuf.toString()
                    if (flushed.isNotEmpty() || pendingToolCalls.isNotEmpty()) {
                        repo.appendMessage(
                            serverChatId,
                            role = "assistant",
                            content = flushed,
                            toolCallsJson = json.encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()),
                                pendingToolCalls,
                            ),
                        )
                        assistantBuf.clear()
                        pendingToolCalls.clear()
                    }
                    val id = obj["id"].asStringOrNull()
                    val name = obj["name"].asStringOrNull()
                    val output = obj["output"].asStringOrNull().orEmpty()
                    val isError = obj["is_error"].asBooleanOrNull() ?: false
                    repo.appendMessage(
                        serverChatId,
                        role = "tool",
                        content = if (isError) "[ERROR]\n$output" else output,
                        toolUseId = id,
                        toolName = name,
                    )
                    _liveDelta.value = null
                }
                "done" -> {
                    if (assistantBuf.isNotEmpty() || pendingToolCalls.isNotEmpty()) {
                        repo.appendMessage(
                            serverChatId,
                            role = "assistant",
                            content = assistantBuf.toString(),
                            toolCallsJson = json.encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()),
                                pendingToolCalls,
                            ),
                        )
                    }
                    _liveDelta.value = null
                }
                "error" -> {
                    val kind = obj["kind"].asStringOrNull().orEmpty()
                    val message = obj["message"].asStringOrNull().orEmpty()
                    val full = if (kind.isNotEmpty()) "[$kind] $message" else message
                    if (assistantBuf.isNotEmpty() || pendingToolCalls.isNotEmpty()) {
                        repo.appendMessage(
                            serverChatId,
                            role = "assistant",
                            content = assistantBuf.toString(),
                            toolCallsJson = json.encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()),
                                pendingToolCalls,
                            ),
                        )
                        assistantBuf.clear()
                        pendingToolCalls.clear()
                    }
                    repo.appendMessage(serverChatId, role = "assistant", content = "⚠️ $full")
                    _state.value = _state.value.copy(error = full)
                    _liveDelta.value = null
                }
                "iteration" -> {
                    val n = obj["n"].asIntOrNull() ?: 0
                    val max = obj["max"].asIntOrNull() ?: 0
                    _liveDelta.value = LiveDelta.Iteration(n = n, max = max)
                }
                else -> { /* ignore unknown event types */ }
            }
        }
    }

    private fun toApiMessage(e: MessageEntity): ApiMessage {
        val toolCalls = runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()),
                e.toolCallsJson.ifBlank { "[]" },
            )
        }.getOrDefault(emptyList())
        return ApiMessage(
            role = e.role,
            content = e.content,
            toolCalls = toolCalls,
            toolUseId = e.toolUseId,
            name = e.toolName,
        )
    }

    private inline fun <T, R> kotlinx.coroutines.flow.StateFlow<T>.map(crossinline transform: (T) -> R)
        : kotlinx.coroutines.flow.Flow<R> =
        kotlinx.coroutines.flow.flow {
            this@map.collect { emit(transform(it)) }
        }
}

data class ChatUiState(
    val chatId: String?,
    val busy: Boolean = false,
    val error: String? = null,
    val activeProvider: ProviderProfile? = null,
    val profilesAvailable: Int = 0,
)

sealed interface LiveDelta {
    data class Text(val text: String) : LiveDelta
    data class ToolStart(val name: String, val input: JsonObject) : LiveDelta
    data class Iteration(val n: Int, val max: Int) : LiveDelta
}
