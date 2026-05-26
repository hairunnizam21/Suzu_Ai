package io.suzuai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.data.ProviderProfile
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ProvidersSection() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()
    val profiles by app.settings.providerProfiles.collectAsState(initial = emptyList())
    val activeId by app.settings.activeProviderId.collectAsState(initial = null)
    var editing by remember { mutableStateOf<ProviderProfile?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "API Providers",
                style = MaterialTheme.typography.titleMedium.copy(color = SuzuColors.AccentCyan),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { adding = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuzuColors.AccentCyan,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
        }
        Text(
            "One profile per LLM provider. You can attach multiple API keys per profile — keys are tried in order and the next key is used automatically on quota or auth errors.",
            style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (profiles.isEmpty()) {
            Text(
                "No providers configured yet.",
                style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
            )
        } else {
            profiles.forEach { p ->
                ProviderRow(
                    profile = p,
                    active = p.id == activeId,
                    onActivate = { scope.launch { app.settings.setActiveProvider(p.id) } },
                    onEdit = { editing = p },
                    onDelete = { scope.launch { app.settings.deleteProfile(p.id) } },
                )
            }
        }
    }

    if (adding) {
        ProviderEditorDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = {
                scope.launch { app.settings.upsertProfile(it) }
                adding = false
            },
        )
    }
    val toEdit = editing
    if (toEdit != null) {
        ProviderEditorDialog(
            initial = toEdit,
            onDismiss = { editing = null },
            onSave = {
                scope.launch { app.settings.upsertProfile(it) }
                editing = null
            },
        )
    }
}

@Composable
private fun ProviderRow(
    profile: ProviderProfile,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SuzuColors.Surface)
            .border(
                width = 1.dp,
                color = if (active) SuzuColors.AccentCyan else SuzuColors.Border,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onActivate)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = SuzuColors.AccentCyan)
        } else {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = SuzuColors.Muted)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.name.ifBlank { profile.id },
                style = MaterialTheme.typography.bodyLarge.copy(color = SuzuColors.OnSurface),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${profile.kind} · ${profile.model} · ${profile.apiKeys.size} key(s)",
                style = MaterialTheme.typography.labelSmall.copy(color = SuzuColors.Muted),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = SuzuColors.Muted)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = SuzuColors.AccentRed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorDialog(
    initial: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: "openai_compat") }
    var kindExpanded by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    val keys = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            initial?.apiKeys?.let { addAll(it) }
            if (isEmpty()) add("")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add provider" else "Edit provider") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = suzuFieldColors(),
                )

                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = kindExpanded,
                    onExpandedChange = { kindExpanded = !kindExpanded },
                ) {
                    OutlinedTextField(
                        value = kind,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = suzuFieldColors(),
                    )
                    DropdownMenu(
                        expanded = kindExpanded,
                        onDismissRequest = { kindExpanded = false },
                    ) {
                        listOf("anthropic", "openai_compat").forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k) },
                                onClick = { kind = k; kindExpanded = false },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (leave blank for default)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = suzuFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = suzuFieldColors(),
                )

                Spacer(Modifier.height(12.dp))
                Text("API keys (tried in order — auto-fallback on quota/auth errors)",
                    style = MaterialTheme.typography.labelMedium.copy(color = SuzuColors.Muted))

                keys.forEachIndexed { idx, k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = k,
                            onValueChange = { keys[idx] = it },
                            modifier = Modifier.weight(1f).padding(top = 6.dp),
                            label = { Text("Key #${idx + 1}") },
                            singleLine = true,
                            colors = suzuFieldColors(),
                        )
                        IconButton(onClick = { if (keys.size > 1) keys.removeAt(idx) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove key", tint = SuzuColors.AccentRed)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { keys.add("") }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = SuzuColors.AccentCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("Add key", color = SuzuColors.AccentCyan)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleaned = keys.map { it.trim() }.filter { it.isNotEmpty() }
                if (name.isBlank() || model.isBlank() || cleaned.isEmpty()) return@TextButton
                onSave(
                    ProviderProfile(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        kind = kind,
                        baseUrl = baseUrl.trim().ifBlank { null },
                        model = model.trim(),
                        apiKeys = cleaned,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = SuzuColors.Surface,
    )
}
