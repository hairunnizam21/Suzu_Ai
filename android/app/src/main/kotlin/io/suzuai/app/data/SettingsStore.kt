package io.suzuai.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "suzu_settings")

/**
 * DataStore-backed settings: server URL/token, account name, provider profiles,
 * and the currently active provider id.
 *
 * Provider profiles are serialised as JSON into a single preference key so we
 * don't have to maintain a separate table or migrate schemas.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val ServerUrl = stringPreferencesKey("server_url")
        val ServerToken = stringPreferencesKey("server_token")
        val AccountName = stringPreferencesKey("account_name")
        val AccountEmail = stringPreferencesKey("account_email")
        val ProviderProfiles = stringPreferencesKey("provider_profiles_json")
        val ActiveProviderId = stringPreferencesKey("active_provider_id")
        val DefaultMaxIterations = stringPreferencesKey("default_max_iterations")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[Keys.ServerUrl].orEmpty() }
    val serverToken: Flow<String> = context.dataStore.data.map { it[Keys.ServerToken].orEmpty() }
    val accountName: Flow<String> = context.dataStore.data.map { it[Keys.AccountName].orEmpty() }
    val accountEmail: Flow<String> = context.dataStore.data.map { it[Keys.AccountEmail].orEmpty() }
    val defaultMaxIterations: Flow<Int> =
        context.dataStore.data.map { it[Keys.DefaultMaxIterations]?.toIntOrNull() ?: 100 }

    val providerProfiles: Flow<List<ProviderProfile>> =
        context.dataStore.data.map { prefs -> decodeProfiles(prefs[Keys.ProviderProfiles]) }
    val activeProviderId: Flow<String?> =
        context.dataStore.data.map { it[Keys.ActiveProviderId]?.takeIf { id -> id.isNotBlank() } }

    suspend fun setServer(url: String, token: String) {
        context.dataStore.edit {
            it[Keys.ServerUrl] = url.trim()
            it[Keys.ServerToken] = token.trim()
        }
    }

    suspend fun setAccount(name: String, email: String) {
        context.dataStore.edit {
            it[Keys.AccountName] = name.trim()
            it[Keys.AccountEmail] = email.trim()
        }
    }

    suspend fun setDefaultMaxIterations(value: Int) {
        context.dataStore.edit {
            it[Keys.DefaultMaxIterations] = value.coerceIn(1, 500).toString()
        }
    }

    suspend fun upsertProfile(profile: ProviderProfile) {
        context.dataStore.edit { prefs ->
            val list = decodeProfiles(prefs[Keys.ProviderProfiles]).toMutableList()
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) list[idx] = profile else list += profile
            prefs[Keys.ProviderProfiles] = json.encodeToString(profileSerializer, list)
            if (prefs[Keys.ActiveProviderId].isNullOrBlank()) {
                prefs[Keys.ActiveProviderId] = profile.id
            }
        }
    }

    suspend fun deleteProfile(id: String) {
        context.dataStore.edit { prefs ->
            val list = decodeProfiles(prefs[Keys.ProviderProfiles]).filterNot { it.id == id }
            prefs[Keys.ProviderProfiles] = json.encodeToString(profileSerializer, list)
            if (prefs[Keys.ActiveProviderId] == id) {
                prefs[Keys.ActiveProviderId] = list.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun setActiveProvider(id: String) {
        context.dataStore.edit { it[Keys.ActiveProviderId] = id }
    }

    private fun decodeProfiles(raw: String?): List<ProviderProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(profileSerializer, raw) }.getOrDefault(emptyList())
    }

    private val profileSerializer = kotlinx.serialization.builtins.ListSerializer(ProviderProfile.serializer())

    @Suppress("UNUSED_PARAMETER")
    private fun unusedPrefsRef(p: Preferences) = Unit
}
