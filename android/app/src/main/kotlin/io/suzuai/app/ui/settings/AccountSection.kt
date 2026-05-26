package io.suzuai.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.suzuai.app.SuzuApp
import io.suzuai.app.ui.theme.SuzuColors
import kotlinx.coroutines.launch

@Composable
fun AccountSection() {
    val app = SuzuApp.instance
    val scope = rememberCoroutineScope()
    val savedName by app.settings.accountName.collectAsState(initial = "")
    val savedEmail by app.settings.accountEmail.collectAsState(initial = "")
    var name by remember { mutableStateOf(savedName) }
    var email by remember { mutableStateOf(savedEmail) }
    LaunchedEffect(savedName) { name = savedName }
    LaunchedEffect(savedEmail) { email = savedEmail }

    Column {
        Text(
            "Account",
            style = MaterialTheme.typography.titleMedium.copy(color = SuzuColors.AccentCyan),
        )
        Text(
            "Used only locally to label the “you” bubble and the device. Not synced anywhere.",
            style = MaterialTheme.typography.bodySmall.copy(color = SuzuColors.Muted),
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = suzuFieldColors(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = suzuFieldColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = { scope.launch { app.settings.setAccount(name, email) } },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuzuColors.AccentCyan,
                    contentColor = Color.Black,
                ),
            ) { Text("Save") }
        }
    }
}

@Composable
internal fun suzuFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SuzuColors.OnSurface,
    unfocusedTextColor = SuzuColors.OnSurface,
    focusedBorderColor = SuzuColors.AccentCyan,
    unfocusedBorderColor = SuzuColors.Border,
    focusedLabelColor = SuzuColors.AccentCyan,
    unfocusedLabelColor = SuzuColors.Muted,
    cursorColor = SuzuColors.AccentCyan,
)
