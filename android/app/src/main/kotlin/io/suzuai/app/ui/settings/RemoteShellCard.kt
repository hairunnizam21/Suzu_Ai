package io.suzuai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

@Composable
fun RemoteShellCard() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()

    val savedHost by app.settings.sshHost.collectAsState(initial = "")
    val savedPort by app.settings.sshPort.collectAsState(initial = 22)
    val savedUser by app.settings.sshUser.collectAsState(initial = "")
    val savedAuth by app.settings.sshAuthMode.collectAsState(initial = "password")
    val savedPwd by app.settings.sshPassword.collectAsState(initial = "")
    val savedKey by app.settings.sshPrivateKey.collectAsState(initial = "")
    val savedWs by app.settings.sshWorkspace.collectAsState(initial = "")

    var host by remember { mutableStateOf(savedHost) }
    var portText by remember { mutableStateOf(savedPort.toString()) }
    var user by remember { mutableStateOf(savedUser) }
    var authMode by remember { mutableStateOf(savedAuth) }
    var password by remember { mutableStateOf(savedPwd) }
    var privateKey by remember { mutableStateOf(savedKey) }
    var workspace by remember { mutableStateOf(savedWs) }
    var pwdVisible by remember { mutableStateOf(false) }

    LaunchedEffect(savedHost, savedPort, savedUser, savedAuth, savedPwd, savedKey, savedWs) {
        if (host.isBlank()) host = savedHost
        if (portText == "22") portText = savedPort.toString()
        if (user.isBlank()) user = savedUser
        authMode = savedAuth
        if (password.isBlank()) password = savedPwd
        if (privateKey.isBlank()) privateKey = savedKey
        if (workspace.isBlank()) workspace = savedWs
    }

    Card(title = "Remote Shell (SSH)", icon = "🔌") {
        Text(
            "Optional. When filled, agent runs `shell` tool over SSH against this box instead of the backend server's local shell.",
            color = SuzuColors.Muted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host / IP", color = SuzuColors.Muted) },
            placeholder = { Text("143.198.211.224", color = SuzuColors.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Port", color = SuzuColors.Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
                colors = fieldColors(),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Username", color = SuzuColors.Muted) },
                placeholder = { Text("root", color = SuzuColors.Muted) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
        }
        Spacer(Modifier.height(12.dp))

        // Auth mode tabs
        Row(modifier = Modifier.fillMaxWidth()) {
            AuthTab(label = "Password", selected = authMode == "password") { authMode = "password" }
            Spacer(Modifier.width(8.dp))
            AuthTab(label = "Private Key", selected = authMode == "key") { authMode = "key" }
        }
        Spacer(Modifier.height(12.dp))

        if (authMode == "password") {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = SuzuColors.Muted) },
                singleLine = true,
                visualTransformation = if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { pwdVisible = !pwdVisible }) {
                        Text(if (pwdVisible) "🙈" else "👁", color = SuzuColors.Muted)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        } else {
            OutlinedTextField(
                value = privateKey,
                onValueChange = { privateKey = it },
                label = { Text("Private key (paste contents of id_rsa)", color = SuzuColors.Muted) },
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = workspace,
            onValueChange = { workspace = it },
            label = { Text("Workspace directory", color = SuzuColors.Muted) },
            placeholder = { Text("/root/suzu-workspace", color = SuzuColors.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(16.dp))

        SaveButton(label = "Save SSH settings") {
            if (host.isNotBlank() && user.isBlank()) {
                return@SaveButton SaveOutcome.Fail("Username kena diisi bila host dah diisi")
            }
            if (authMode == "password" && host.isNotBlank() && password.isBlank()) {
                return@SaveButton SaveOutcome.Fail("Password kosong")
            }
            if (authMode == "key" && host.isNotBlank() && privateKey.isBlank()) {
                return@SaveButton SaveOutcome.Fail("Private key kosong")
            }
            app.settings.setSsh(
                host = host,
                port = portText.toIntOrNull() ?: 22,
                user = user,
                authMode = authMode,
                password = password,
                privateKey = privateKey,
                workspace = workspace,
            )
            val readHost = kotlinx.coroutines.flow.first(app.settings.sshHost)
            val readUser = kotlinx.coroutines.flow.first(app.settings.sshUser)
            if (readHost == host.trim() && readUser == user.trim()) {
                if (host.isBlank()) SaveOutcome.Ok("SSH dikosongkan (guna server tempatan)")
                else SaveOutcome.Ok("Tersimpan: $user@$host:${portText.ifBlank { "22" }}")
            } else {
                SaveOutcome.Fail("Verifikasi gagal")
            }
        }
    }
}

@Composable
private fun AuthTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) SuzuColors.AccentCyan else SuzuColors.SurfaceVariant
    val content = if (selected) SuzuColors.Background else SuzuColors.OnSurface
    val border = if (selected) SuzuColors.AccentCyan else SuzuColors.Border
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontWeight = FontWeight.SemiBold)
    }
}
