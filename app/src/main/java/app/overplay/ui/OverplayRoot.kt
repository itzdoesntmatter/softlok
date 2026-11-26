// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OverplayRoot(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    var route by rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled = route != "home") {
        route = if (route.startsWith("new/")) "picker" else "home"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Surface
        }

        when {
            route == "picker" -> AppPickerScreen(
                state = state,
                loadApps = viewModel::launchableApps,
                onBack = { route = "home" },
                onPick = { app ->
                    route = "new/${Uri.encode(app.packageName)}/${Uri.encode(app.label)}"
                },
                onEditExisting = { packageName ->
                    route = "edit/${Uri.encode(packageName)}"
                },
            )

            route == "settings" -> SettingsScreen(
                state = state,
                onBack = { route = "home" },
                onChangeCode = { route = "code" },
                onStartRecovery = viewModel::startRecovery,
                onCancelRecovery = viewModel::cancelRecovery,
                onRefreshPermissions = viewModel::refresh,
            )

            route == "code" -> CodeSetupScreen(
                hasCode = state.security.hasCode,
                onBack = { route = "home" },
                onVerifyCode = viewModel::verifyCode,
                onSaveCode = viewModel::setCode,
                onDone = { route = "home" },
            )

            route.startsWith("new/") -> {
                val rest = route.removePrefix("new/")
                val packageName = Uri.decode(rest.substringBefore('/'))
                val label = Uri.decode(rest.substringAfter('/', "App"))
                val existing = state.rules.firstOrNull { it.rule.packageName == packageName }
                RuleEditorScreen(
                    packageName = packageName,
                    label = existing?.rule?.label ?: label,
                    existing = existing,
                    onBack = { route = "picker" },
                    onSave = viewModel::saveRule,
                    onDelete = viewModel::deleteRule,
                    onDone = { route = "home" },
                )
            }

            route.startsWith("edit/") -> {
                val packageName = Uri.decode(route.removePrefix("edit/"))
                val existing = state.rules.firstOrNull { it.rule.packageName == packageName }
                if (existing == null) {
                    route = "home"
                } else {
                    RuleEditorScreen(
                        packageName = existing.rule.packageName,
                        label = existing.rule.label,
                        existing = existing,
                        onBack = { route = "home" },
                        onSave = viewModel::saveRule,
                        onDelete = viewModel::deleteRule,
                        onDone = { route = "home" },
                    )
                }
            }

            else -> HomeScreen(
                state = state,
                onSettings = { route = "settings" },
                onAddApps = { route = "picker" },
                onEdit = { packageName -> route = "edit/${Uri.encode(packageName)}" },
                onSetCode = { route = "code" },
            )
        }
    }
}
