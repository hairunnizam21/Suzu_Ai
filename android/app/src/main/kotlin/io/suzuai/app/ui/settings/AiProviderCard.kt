package io.suzuai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

/** Provider kind → display label shown in the dropdown. */
private val PROVIDER_KINDS = listOf(
    "anthropic" to "Anthropic (Claude)",
    "openai" to "OpenAI",
    "openrouter" to "OpenRouter",
    "afiqstore" to "AfiqStoreAPI",
    "groq" to "Groq",
    "together" to "Together",
    "custom" to "Custom (OpenAI-compatible)",
)

private fun labelOf(kind: String): String =
    PROVIDER_KINDS.firstOrNull { it.first == kind }?.second ?: PROVIDER_KINDS.last().second

/** Sensible default Base URL per provider so the user just picks + pastes a key. */
private fun defaultBaseUrl(kind: String): String = when (kind) {
    "anthropic" -> "https://api.anthropic.com"
    "openai" -> "https://api.openai.com/v1"
    "openrouter" -> "https://openrouter.ai/api/v1"
    "afiqstore" -> "https://api.afiqstoreapi.cloud/v1"
    "groq" -> "https://api.groq.com/openai/v1"
    "together" -> "https://api.together.xyz/v1"
    else -> ""
}

private fun defaultModel(kind: String): String = when (kind) {
    "anthropic" -> "claude-3-5-sonnet-20241022"
    "openai" -> "gpt-4o"
    "openrouter" -> "anthropic/claude-3.5-sonnet"
    "afiqstore" -> "deepseek-v4-pro"
    "groq" -> "llama-3.3-70b-versatile"
    "together" -> "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo"
    else -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderCard() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()

    val savedKind by app.settings.aiKind.collectAsState(initial = "openai_compat")
    val savedBaseUrl by app.settings.aiBaseUrl.collectAsState(initial = "")
    val savedModel by app.settings.aiModel.collectAsState(initial = "")
    val savedKey by app.settings.aiApiKey.collectAsState(initial = "")
    val savedUnlimited by app.settings.aiMaxTokensUnlimited.collectAsState(initial = true)
    val savedMaxTokens by app.settings.aiMaxTokens.collectAsState(initial = 16384)
    val savedTemperature by app.settings.aiTemperature.collectAsState(initial = 1.0f)

    var kind by remember { mutableStateOf(savedKind) }
    var baseUrl by remember { mutableStateOf(savedBaseUrl) }
    var model by remember { mutableStateOf(savedModel) }
    var apiKey by remember { mutableStateOf(savedKey) }
    var unlimited by remember { mutableStateOf(savedUnlimited) }
    var maxTokens by remember { mutableStateOf(savedMaxTokens) }
    var maxTokensText by remember { mutableStateOf(savedMaxTokens.toString()) }
    var temperature by remember { mutableStateOf(savedTemperature) }
    var keyVisible by remember { mutableStateOf(false) }
    var kindMenuOpen by remember { mutableStateOf(false) }

    // When the saved values arrive from DataStore, prime the local fields once.
    LaunchedEffect(savedKind, savedBaseUrl, savedModel, savedKey, savedUnlimited, savedMaxTokens, savedTemperature) {
        kind = savedKind
        if (baseUrl.isBlank()) baseUrl = savedBaseUrl
        if (model.isBlank()) model = savedModel
        if (apiKey.isBlank()) apiKey = savedKey
        unlimited = savedUnlimited
        maxTokens = savedMaxTokens
        maxTokensText = savedMaxTokens.toString()
        temperature = savedTemperature
    }

    Card(title = "AI Provider", icon = "🤖") {
        // Provider kind dropdown
        ExposedDropdownMenuBox(
            expanded = kindMenuOpen,
            onExpandedChange = { kindMenuOpen = !kindMenuOpen },
        ) {
            OutlinedTextField(
                value = labelOf(kind),
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider", color = SuzuColors.Muted) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindMenuOpen) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = fieldColors(),
            )
            ExposedDropdownMenu(
                expanded = kindMenuOpen,
                onDismissRequest = { kindMenuOpen = false },
            ) {
                PROVIDER_KINDS.forEach { (k, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            kind = k
                            // Auto-fill defaults the first time you switch to a provider
                            if (baseUrl.isBlank()) baseUrl = defaultBaseUrl(k)
                            if (model.isBlank()) model = defaultModel(k)
                            kindMenuOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL", color = SuzuColors.Muted) },
            placeholder = { Text(defaultBaseUrl(kind).ifBlank { "https://…" }, color = SuzuColors.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model", color = SuzuColors.Muted) },
            placeholder = { Text(defaultModel(kind).ifBlank { "model name" }, color = SuzuColors.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key", color = SuzuColors.Muted) },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Text(if (keyVisible) "🙈" else "👁", color = SuzuColors.Muted)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(16.dp))

        // Max tokens with Unlimited toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Max tokens", color = SuzuColors.OnSurface, modifier = Modifier.weight(1f))
            Text(
                if (unlimited) "Unlimited" else maxTokens.toString(),
                color = SuzuColors.AccentCyan,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Unlimited", color = SuzuColors.Muted, modifier = Modifier.weight(1f))
            Switch(
                checked = unlimited,
                onCheckedChange = { unlimited = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SuzuColors.Background,
                    checkedTrackColor = SuzuColors.AccentCyan,
                    uncheckedThumbColor = SuzuColors.OnSurface,
                    uncheckedTrackColor = SuzuColors.Border,
                ),
            )
        }
        if (!unlimited) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { txt ->
                    maxTokensText = txt.filter { it.isDigit() }.take(8)
                    maxTokens = maxTokensText.toIntOrNull()?.coerceIn(256, 2_000_000) ?: maxTokens
                },
                label = { Text("Max tokens (256 – 2,000,000)", color = SuzuColors.Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Temperature", color = SuzuColors.OnSurface, modifier = Modifier.weight(1f))
            Text(
                "%.2f".format(temperature),
                color = SuzuColors.AccentCyan,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = temperature,
            onValueChange = { temperature = it },
            valueRange = 0f..2f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = SuzuColors.AccentCyan,
                activeTrackColor = SuzuColors.AccentCyan,
                inactiveTrackColor = SuzuColors.Border,
            ),
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    app.settings.setAi(
                        kind = kind,
                        baseUrl = baseUrl.ifBlank { defaultBaseUrl(kind) },
                        model = model.ifBlank { defaultModel(kind) },
                        apiKey = apiKey,
                        maxTokensUnlimited = unlimited,
                        maxTokens = maxTokens,
                        temperature = temperature,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuzuColors.AccentCyan,
                contentColor = SuzuColors.Background,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Save AI settings", fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = {
                scope.launch {
                    app.settings.setAi(
                        kind = kind,
                        baseUrl = baseUrl.ifBlank { defaultBaseUrl(kind) },
                        model = model.ifBlank { defaultModel(kind) },
                        apiKey = apiKey,
                        maxTokensUnlimited = unlimited,
                        maxTokens = maxTokens,
                        temperature = temperature,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuzuColors.AccentCyan,
                contentColor = SuzuColors.Background,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Save AI settings", fontWeight = FontWeight.SemiBold)
        }
        SaveButton(label = "Save & verify AI settings") {
            if (model.isBlank() && defaultModel(kind).isBlank()) {
                return@SaveButton SaveOutcome.Fail("Model kosong")
            }
            if (apiKey.isBlank()) return@SaveButton SaveOutcome.Fail("API key kosong")
            app.settings.setAi(
                kind = kind,
                baseUrl = baseUrl.ifBlank { defaultBaseUrl(kind) },
                model = model.ifBlank { defaultModel(kind) },
                apiKey = apiKey,
                maxTokensUnlimited = unlimited,
                maxTokens = maxTokens,
                temperature = temperature,
            )
            // Verify
            val readKey = kotlinx.coroutines.flow.first(app.settings.aiApiKey)
            val readModel = kotlinx.coroutines.flow.first(app.settings.aiModel)
            if (readKey == apiKey.trim() && readModel == (model.ifBlank { defaultModel(kind) }).trim()) {
                SaveOutcome.Ok("AI provider tersimpan: ${labelOf(kind)}")
            } else {
                SaveOutcome.Fail("Verifikasi DataStore gagal")
            }
        }
    }
}

/* ----------------------- shared building blocks ----------------------- */

@Composable
internal fun Card(title: String, icon: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SuzuColors.Surface)
            .border(1.dp, SuzuColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(icon, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = SuzuColors.AccentCyan,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SuzuColors.OnSurface,
    unfocusedTextColor = SuzuColors.OnSurface,
    cursorColor = SuzuColors.AccentCyan,
    focusedBorderColor = SuzuColors.AccentCyan,
    unfocusedBorderColor = SuzuColors.Border,
    focusedContainerColor = SuzuColors.SurfaceVariant,
    unfocusedContainerColor = SuzuColors.SurfaceVariant,
)

/** Re-export of [ExposedDropdownMenuBox]'s slot so callers can use a local extension. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(SuzuColors.Surface),
        content = content,
    )
}
