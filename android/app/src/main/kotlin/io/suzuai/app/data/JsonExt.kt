package io.suzuai.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Defensive JSON helpers — every accessor returns `null` instead of throwing
 * when the element is missing, `JsonNull`, or the wrong type.
 *
 * Background: in the previous build, calling `.jsonObject` on what turned out
 * to be `JsonNull` crashed the UI with
 *
 *     Element class kotlinx.serialization.json.JsonNull (Kotlin reflection
 *     is not available) is not a JsonObject
 *
 * because OpenAI-compatible providers occasionally emit `tool_calls: null` or
 * `function_call: null`. These helpers neutralise that whole class of bugs.
 */

fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
    when (this) {
        null, is JsonNull -> null
        is JsonObject -> this
        else -> runCatching { this.jsonObject }.getOrNull()
    }

fun JsonElement?.asJsonArrayOrNull(): JsonArray? =
    when (this) {
        null, is JsonNull -> null
        is JsonArray -> this
        else -> runCatching { this.jsonArray }.getOrNull()
    }

fun JsonElement?.asPrimitiveOrNull(): JsonPrimitive? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> this
        else -> runCatching { this.jsonPrimitive }.getOrNull()
    }

fun JsonElement?.asStringOrNull(): String? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> this.contentOrNull
        else -> runCatching { this.jsonPrimitive.contentOrNull }.getOrNull()
    }

fun JsonElement?.asIntOrNull(): Int? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> this.intOrNull
        else -> runCatching { this.jsonPrimitive.intOrNull }.getOrNull()
    }

fun JsonElement?.asBooleanOrNull(): Boolean? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> this.booleanOrNull
        else -> runCatching { this.jsonPrimitive.boolean }.getOrNull()
    }
