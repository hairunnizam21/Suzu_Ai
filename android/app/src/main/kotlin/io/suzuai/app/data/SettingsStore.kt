package io.suzuai.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "suzu_settings")

/**
 * Flat DataStore-backed settings.
 *
 * The whole config fits on one Settings screen so we keep one preference key per
 * field rather than serialising a tree. The agent backend (HTTP/SSE) and the
 * SSH target are two independent objects: HTTP carries the actual chat traffic,
 * SSH (optional) gives the agent a place to run shell tools against.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        // AI provider
        val AiKind = stringPreferencesKey("ai_kind") // "anthropic" | "openai" | "openrouter" | "afiqstore" | "custom"
        val AiBaseUrl = stringPreferencesKey("ai_base_url")
        val AiModel = stringPreferencesKey("ai_model")
        val AiApiKey = stringPreferencesKey("ai_api_key")
        val AiMaxTokensUnlimited = booleanPreferencesKey("ai_max_tokens_unlimited")
        val AiMaxTokens = intPreferencesKey("ai_max_tokens")
        val AiTemperature = floatPreferencesKey("ai_temperature")

        // Backend HTTP/SSE server
        val ServerUrl = stringPreferencesKey("server_url")
        val ServerToken = stringPreferencesKey("server_token")
        val ServerMaxIter = intPreferencesKey("server_max_iter")

        // SSH remote shell (optional — used by the agent's shell tool when filled)
        val SshHost = stringPreferencesKey("ssh_host")
        val SshPort = intPreferencesKey("ssh_port")
        val SshUser = stringPreferencesKey("ssh_user")
        val SshAuthMode = stringPreferencesKey("ssh_auth_mode") // "password" | "key"
        val SshPassword = stringPreferencesKey("ssh_password")
        val SshPrivateKey = stringPreferencesKey("ssh_private_key")
        val SshWorkspace = stringPreferencesKey("ssh_workspace")

        // Appearance
        val ThemeStyle = stringPreferencesKey("theme_style") // "minimal" | "glass" | "hacker" | "material"
        val ThemeMode = stringPreferencesKey("theme_mode") // "light" | "dark" | "system"
    }

    /* ---------- AI provider ---------- */

    val aiKind: Flow<String> = context.dataStore.data.map { it[Keys.AiKind] ?: "openai_compat" }
    val aiBaseUrl: Flow<String> = context.dataStore.data.map { it[Keys.AiBaseUrl].orEmpty() }
    val aiModel: Flow<String> = context.dataStore.data.map { it[Keys.AiModel].orEmpty() }
    val aiApiKey: Flow<String> = context.dataStore.data.map { it[Keys.AiApiKey].orEmpty() }
    val aiMaxTokensUnlimited: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AiMaxTokensUnlimited] ?: true }
    val aiMaxTokens: Flow<Int> =
        context.dataStore.data.map { it[Keys.AiMaxTokens] ?: 16384 }
    val aiTemperature: Flow<Float> =
        context.dataStore.data.map { it[Keys.AiTemperature] ?: 1.0f }

    suspend fun setAi(
        kind: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        maxTokensUnlimited: Boolean,
        maxTokens: Int,
        temperature: Float,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AiKind] = kind
            prefs[Keys.AiBaseUrl] = baseUrl.trim()
            prefs[Keys.AiModel] = model.trim()
            prefs[Keys.AiApiKey] = apiKey.trim()
            prefs[Keys.AiMaxTokensUnlimited] = maxTokensUnlimited
            prefs[Keys.AiMaxTokens] = maxTokens.coerceIn(256, 2_000_000)
            prefs[Keys.AiTemperature] = temperature.coerceIn(0f, 2f)
        }
    }

    /* ---------- Backend HTTP/SSE server ---------- */

    val serverUrl: Flow<String> = context.dataStore.data.map { it[Keys.ServerUrl].orEmpty() }
    val serverToken: Flow<String> = context.dataStore.data.map { it[Keys.ServerToken].orEmpty() }
    val defaultMaxIterations: Flow<Int> =
        context.dataStore.data.map { it[Keys.ServerMaxIter] ?: 100 }

    suspend fun setServer(url: String, token: String, maxIter: Int) {
        context.dataStore.edit {
            it[Keys.ServerUrl] = url.trim()
            it[Keys.ServerToken] = token.trim()
            it[Keys.ServerMaxIter] = maxIter.coerceIn(1, 500)
        }
    }

    /* ---------- SSH ---------- */

    val sshHost: Flow<String> = context.dataStore.data.map { it[Keys.SshHost].orEmpty() }
    val sshPort: Flow<Int> = context.dataStore.data.map { it[Keys.SshPort] ?: 22 }
    val sshUser: Flow<String> = context.dataStore.data.map { it[Keys.SshUser].orEmpty() }
    val sshAuthMode: Flow<String> =
        context.dataStore.data.map { it[Keys.SshAuthMode] ?: "password" }
    val sshPassword: Flow<String> = context.dataStore.data.map { it[Keys.SshPassword].orEmpty() }
    val sshPrivateKey: Flow<String> = context.dataStore.data.map { it[Keys.SshPrivateKey].orEmpty() }
    val sshWorkspace: Flow<String> =
        context.dataStore.data.map { it[Keys.SshWorkspace].orEmpty() }

    /** True when the user has filled in enough SSH info to attempt a connection. */
    val sshConfigured: Flow<Boolean> = combine(sshHost, sshUser) { host, user ->
        host.isNotBlank() && user.isNotBlank()
    }

    suspend fun setSsh(
        host: String,
        port: Int,
        user: String,
        authMode: String,
        password: String,
        privateKey: String,
        workspace: String,
    ) {
        context.dataStore.edit {
            it[Keys.SshHost] = host.trim()
            it[Keys.SshPort] = port.coerceIn(1, 65535)
            it[Keys.SshUser] = user.trim()
            it[Keys.SshAuthMode] = if (authMode == "key") "key" else "password"
            it[Keys.SshPassword] = password
            it[Keys.SshPrivateKey] = privateKey
            it[Keys.SshWorkspace] = workspace.trim()
        }
    }

    /* ---------- Appearance ---------- */

    val themeStyle: Flow<String> = context.dataStore.data.map { it[Keys.ThemeStyle] ?: "hacker" }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.ThemeMode] ?: "dark" }

    suspend fun setTheme(style: String, mode: String) {
        context.dataStore.edit {
            it[Keys.ThemeStyle] = style
            it[Keys.ThemeMode] = mode
        }
    }
}
