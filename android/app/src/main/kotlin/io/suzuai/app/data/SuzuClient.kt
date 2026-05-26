package io.suzuai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP / SSE client for the Suzu server.
 *
 * Streaming is implemented manually instead of relying on `okhttp-sse` because
 * we need direct access to the raw payload (so we can apply the defensive
 * `asJsonObjectOrNull` helpers in [SseEvent.parse]).
 */
class SuzuClient(private val settings: SettingsStore) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)        // streaming = no read timeout
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private suspend fun baseUrlOrThrow(): String {
        val raw = settings.serverUrl.first().trim().trimEnd('/')
        require(raw.isNotBlank()) { "Set server URL in Settings → Server config" }
        require(raw.toHttpUrlOrNull() != null) { "Server URL is not a valid URL: $raw" }
        return raw
    }

    private suspend fun authHeader(): String {
        val token = settings.serverToken.first()
        require(token.isNotBlank()) { "Set server token in Settings → Server config" }
        return "Bearer $token"
    }

    /** Throws on network/auth error. */
    suspend fun fetchConfig(): ConfigResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${baseUrlOrThrow()}/v1/config")
            .header("Authorization", authHeader())
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code}: ${resp.message}" }
            val body = resp.body?.string() ?: error("empty body")
            json.decodeFromString(ConfigResponse.serializer(), body)
        }
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrlOrThrow()}/health").get().build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Probe both reachability and auth in one call. Returns a [PingResult]
     * the Settings UI can use to render an explicit success/failure toast.
     */
    suspend fun ping(): PingResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrlOrThrow()}/v1/config"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader())
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val cfg = runCatching {
                        json.decodeFromString(ConfigResponse.serializer(), body)
                    }.getOrNull()
                    PingResult.Ok(version = cfg?.serverVersion ?: "unknown")
                } else {
                    PingResult.Error("HTTP ${resp.code} ${resp.message}")
                }
            }
        }.getOrElse { t -> PingResult.Error(t.message ?: t.toString()) }
    }

    /** POST `/v1/chats/{id}/inject` — push a follow-up message into a live run. */
    suspend fun inject(chatId: String, content: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(InjectRequest.serializer(), InjectRequest(content))
            val req = Request.Builder()
                .url("${baseUrlOrThrow()}/v1/chats/$chatId/inject")
                .header("Authorization", authHeader())
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** POST `/v1/chats/{id}/cancel` — cancel a live run. */
    suspend fun cancelRun(chatId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrlOrThrow()}/v1/chats/$chatId/cancel")
                .header("Authorization", authHeader())
                .post("".toRequestBody(JSON_MEDIA))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** GET `/v1/runs` — list runs the server thinks are live. */
    suspend fun listRuns(): List<RunStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrlOrThrow()}/v1/runs")
                .header("Authorization", authHeader())
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList<RunStatus>()
                val body = resp.body?.string().orEmpty()
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(RunStatus.serializer()),
                    body,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Reconnect to an in-flight run via GET `/v1/chats/{id}/stream`. */
    fun resumeStream(chatId: String): Flow<SseEvent> = flow {
        val req = Request.Builder()
            .url("${baseUrlOrThrow()}/v1/chats/$chatId/stream")
            .header("Authorization", authHeader())
            .header("Accept", "text/event-stream")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            handleStreamResponse(resp).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stream the agent. Emits one [SseEvent] per server event. Cancelling the
     * collecting coroutine cancels the underlying HTTP call cleanly.
     */
    fun streamChat(request: ChatRequest): Flow<SseEvent> = flow {
        val body = json.encodeToString(ChatRequest.serializer(), request)
        val req = Request.Builder()
            .url("${baseUrlOrThrow()}/v1/chat")
            .header("Authorization", authHeader())
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(req).execute().use { resp ->
            handleStreamResponse(resp).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    private fun handleStreamResponse(resp: Response): Flow<SseEvent> = callbackFlow {
        if (!resp.isSuccessful) {
            val errBody = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
            send(syntheticError("http_${resp.code}", "HTTP ${resp.code}: ${resp.message}\n$errBody"))
            close()
            return@callbackFlow
        }
        val source = resp.body?.source()
        if (source == null) {
            send(syntheticError("empty_body", "Server returned no body"))
            close()
            return@callbackFlow
        }
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8LineStrict()
                if (line.isBlank() || !line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                val ev = SseEvent.parse(json, payload) ?: continue
                trySend(ev)
            }
        } catch (t: Throwable) {
            trySend(syntheticError("stream_io", t.message ?: t.toString()))
        }
        close()
        awaitClose { /* no-op — body autoclosed by caller's `use` */ }
    }

    private fun syntheticError(kind: String, message: String): SseEvent {
        val obj = json.parseToJsonElement(
            """{"type":"error","kind":${escape(kind)},"message":${escape(message)}}"""
        )
        @Suppress("UNCHECKED_CAST")
        return SseEvent(obj.asJsonObjectOrNull() as JsonObject)
    }

    private fun escape(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
