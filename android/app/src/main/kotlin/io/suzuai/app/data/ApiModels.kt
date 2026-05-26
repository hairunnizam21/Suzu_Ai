package io.suzuai.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/* ---------------------------------------------------------------------------
 *  Request DTOs                                                              *
 * --------------------------------------------------------------------------- */

@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    /** "anthropic" or "openai_compat" */
    val kind: String,
    @SerialName("base_url") val baseUrl: String? = null,
    val model: String,
    @SerialName("api_keys") val apiKeys: List<String> = emptyList(),
    @SerialName("extra_headers") val extraHeaders: Map<String, String> = emptyMap(),
)

/**
 * Optional SSH target. When the user fills in their box's credentials the
 * agent's shell tool runs commands over SSH against this host instead of on
 * the FastAPI server's local filesystem.
 */
@Serializable
data class SshTarget(
    val host: String,
    val port: Int = 22,
    val user: String,
    @SerialName("auth_mode") val authMode: String = "password",
    val password: String? = null,
    @SerialName("private_key") val privateKey: String? = null,
    val workspace: String? = null,
)

@Serializable
data class ApiMessage(
    val role: String,
    val content: String = "",
    @SerialName("tool_calls") val toolCalls: List<JsonObject> = emptyList(),
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val name: String? = null,
)

@Serializable
data class ChatRequest(
    @SerialName("chat_id") val chatId: String? = null,
    val title: String? = null,
    val provider: ProviderProfile,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    /** Pass 0 for unlimited iterations. */
    @SerialName("max_iterations") val maxIterations: Int = 100,
    /** Omitted when the user toggles Unlimited — provider keeps its own default. */
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Float = 0.7f,
    @SerialName("enabled_tools") val enabledTools: List<String>? = null,
    @SerialName("ssh_target") val sshTarget: SshTarget? = null,
    val messages: List<ApiMessage>,
)

@Serializable
data class InjectRequest(
    val content: String,
)

@Serializable
data class RunStatus(
    @SerialName("chat_id") val chatId: String,
    val done: Boolean,
    val cancelled: Boolean,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("last_event_at") val lastEventAt: Long,
    @SerialName("history_size") val historySize: Int,
    val subscribers: Int,
)

/** Result of a server reachability + auth probe. */
sealed interface PingResult {
    data class Ok(val version: String) : PingResult
    data class Error(val message: String) : PingResult
}

/* ---------------------------------------------------------------------------
 *  Response / event DTOs                                                     *
 * --------------------------------------------------------------------------- */

@Serializable
data class ConfigResponse(
    @SerialName("server_version") val serverVersion: String,
    @SerialName("default_model") val defaultModel: String,
    @SerialName("default_max_iterations") val defaultMaxIterations: Int,
    @SerialName("available_tools") val availableTools: List<String>,
)

/**
 * Discriminated SSE payloads.
 *
 * We don't use `polymorphic` here because providers occasionally drop fields
 * or emit unexpected `null`s. Parsing is defensive — every consumer reads the
 * raw [JsonObject] via [SseEvent.raw] and converts via [SseEvent.parse].
 */
data class SseEvent(val raw: JsonObject) {
    val type: String get() = raw["type"]?.asStringOrNull().orEmpty()

    companion object {
        fun parse(json: kotlinx.serialization.json.Json, line: String): SseEvent? {
            if (line.isBlank()) return null
            val element: JsonElement = runCatching { json.parseToJsonElement(line) }.getOrNull() ?: return null
            val obj = element.asJsonObjectOrNull() ?: return null
            return SseEvent(obj)
        }
    }
}
