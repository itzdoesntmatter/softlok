// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.softlok.domain.TimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: DashboardUiState,
    onSettings: () -> Unit,
    onAddApps: () -> Unit,
    onEdit: (String) -> Unit,
    onSetCode: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Softlok", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = onSettings) { Text("Settings") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.permissions.allGranted) {
                item { PermissionCard(state.permissions) }
            }

            if (!state.security.hasCode) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "No override code set",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Extra time cannot be granted until you create one.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onSetCode) { Text("Create code") }
                        }
                    }
                }
            }

            item {
                Text(
                    "Today",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (state.rules.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "No apps limited yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Choose the apps that tend to eat your day.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                items(state.rules, key = { it.rule.packageName }) { row ->
                    RuleCard(row = row, onClick = { onEdit(row.rule.packageName) })
                }
            }

            item {
                Button(
                    onClick = onAddApps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text("Add app")
                }
            }
        }
    }
}

@Composable
private fun RuleCard(row: RuleUi, onClick: () -> Unit) {
    val total = (row.usedMillis + row.remainingMillis).coerceAtLeast(1)
    val fraction = (row.usedMillis.toFloat() / total).coerceIn(0f, 1f)
    val done = row.remainingMillis <= 0
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(row.rule.packageName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.rule.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${TimeText.clock(row.usedMillis)} used of " +
                            TimeText.clock(row.rule.dailyLimitMillis + row.extraMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (done) "Limit reached" else "${TimeText.clock(row.remainingMillis)} left",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (done) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionCard(permissions: PermissionState) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Keep limits working",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Softlok needs accessibility access to see the active app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                name = "Accessibility",
                granted = permissions.accessibility,
                onOpen = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (granted) "On" else "Off",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (!granted) {
            TextButton(onClick = onOpen) { Text("Open") }
        }
    }
}
