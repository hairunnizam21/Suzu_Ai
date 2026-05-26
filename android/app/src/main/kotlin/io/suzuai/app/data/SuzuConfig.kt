package io.suzuai.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable Suzu config — what the user sees in the sidebar's "Config JSON"
 * feature. Importing this populates every Settings field at once so the user
 * never has to navigate the Settings screen for routine setup.
 *
 * Designed to be hand-editable: keys are explicit and grouped by section.
 */
@Serializable
data class SuzuConfig(
    val server: ServerConfig = ServerConfig(),
    val ai: AiConfig = AiConfig(),
    val ssh: SshConfig = SshConfig(),
    val agent: AgentConfig = AgentConfig(),
)

@Serializable
data class ServerConfig(
    val url: String = "",
    val token: String = "",
)

@Serializable
data class AiConfig(
    /** "anthropic" | "openai" | "openrouter" | "afiqstore" | "groq" | "together" | "custom" */
    val kind: String = "openai_compat",
    @SerialName("base_url") val baseUrl: String = "",
    val model: String = "",
    @SerialName("api_key") val apiKey: String = "",
    @SerialName("max_tokens_unlimited") val maxTokensUnlimited: Boolean = true,
    @SerialName("max_tokens") val maxTokens: Int = 16384,
    val temperature: Float = 1.0f,
)

@Serializable
data class SshConfig(
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    @SerialName("auth_mode") val authMode: String = "password",
    val password: String = "",
    @SerialName("private_key") val privateKey: String = "",
    val workspace: String = "",
)

@Serializable
data class AgentConfig(
    @SerialName("max_iterations") val maxIterations: Int = 100,
    @SerialName("max_iterations_unlimited") val maxIterationsUnlimited: Boolean = false,
)

/** Pretty-printing JSON for export so the file is human-readable. */
val ConfigJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
