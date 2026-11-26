// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.softlok.BuildConfig
import app.softlok.domain.TimeText
import kotlinx.coroutines.delay

private const val SOURCE_URL = "https://github.com/itzdoesntmatter/softlok"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onChangeCode: () -> Unit,
    onStartRecovery: () -> Unit,
    onCancelRecovery: () -> Unit,
    onRefreshPermissions: () -> Unit,
) {
    val context = LocalContext.current
    var showRecoveryConfirm by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.security.recoveryDueAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    val dueAt = state.security.recoveryDueAt
    val recoveryDue = dueAt != null && now >= dueAt

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingCard("Override code") {
                Text(
                    if (state.security.hasCode) {
                        "A code is set. Changing it requires the current code."
                    } else {
                        "No code is set. Extra time cannot be granted."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onChangeCode) {
                    Text(if (state.security.hasCode) "Change code" else "Create code")
                }
            }

            SettingCard("Recovery") {
                when {
                    recoveryDue -> {
                        Text(
                            "The 48-hour wait is over. Set a new override code now.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onChangeCode) { Text("Set new code") }
                    }
                    dueAt != null -> {
                        Text(
                            "A reset is pending. A new code can be set in " +
                                "${TimeText.countdown(dueAt - now)}. Limits stay active meanwhile.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onCancelRecovery) { Text("Cancel recovery") }
                    }
                    state.security.hasCode -> {
                        Text(
                            "Forgot the code? A reset keeps protection on for 48 hours. " +
                                "That delay is the point, but it guarantees you can get back in.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { showRecoveryConfirm = true }) {
                            Text("Start 48-hour reset")
                        }
                    }
                    else -> {
                        Text(
                            "Set an override code before recovery can be configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingCard("Permissions") {
                Text(
                    "Accessibility access must stay on for limits to work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                PermissionSettingRow(
                    name = "Accessibility",
                    granted = state.permissions.accessibility,
                    onOpen = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRefreshPermissions) { Text("Refresh status") }
            }

            SettingCard("About") {
                Text(
                    "Softlok ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "AGPL-3.0-or-later. No network permission. Rules and usage stay on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android cannot stop the device owner from uninstalling an app or revoking " +
                        "permissions. Softlok is friction, not an unbreakable lock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)),
                        )
                    },
                ) {
                    Text("View source on GitHub")
                }
            }
        }
    }

    if (showRecoveryConfirm) {
        AlertDialog(
            onDismissRequest = { showRecoveryConfirm = false },
            title = { Text("Start a 48-hour reset?") },
            text = {
                Text(
                    "Limits stay active during the wait. After 48 hours you can set a new " +
                        "override code without the old one.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRecoveryConfirm = false
                        onStartRecovery()
                    },
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun PermissionSettingRow(name: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f))
        Text(
            if (granted) "On" else "Off",
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.labelMedium,
        )
        if (!granted) {
            TextButton(onClick = onOpen) { Text("Open") }
        }
    }
}
