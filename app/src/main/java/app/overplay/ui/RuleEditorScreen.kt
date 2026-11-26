// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.overplay.data.RuleEntity
import app.overplay.domain.Limits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    packageName: String,
    label: String,
    existing: RuleUi?,
    onBack: () -> Unit,
    onSave: suspend (RuleEntity, String?) -> ActionResult,
    onDelete: suspend (String, String) -> ActionResult,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var minutesText by rememberSaveable {
        mutableStateOf(
            ((existing?.rule?.dailyLimitMillis ?: Limits.minutesToMillis(30)) / 60_000L).toString(),
        )
    }
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val minutes = minutesText.toIntOrNull()
    val valid = minutes != null && minutes in Limits.MIN_LIMIT_MINUTES..Limits.MAX_LIMIT_MINUTES
    val newLimit = minutes
        ?.takeIf { it in Limits.MIN_LIMIT_MINUTES..Limits.MAX_LIMIT_MINUTES }
        ?.let(Limits::minutesToMillis)
        ?: 0L
    val needsCode = existing != null && newLimit > existing.rule.dailyLimitMillis

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Set limit" else "Edit limit") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName, size = 48)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = minutesText,
                onValueChange = { input ->
                    minutesText = input.filter { it.isDigit() }.take(4)
                    error = null
                },
                label = { Text("Daily limit in minutes") },
                suffix = { Text("min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!valid && minutesText.isNotEmpty()) {
                Text(
                    "Use ${Limits.MIN_LIMIT_MINUTES} to ${Limits.MAX_LIMIT_MINUTES} minutes.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(15, 30, 45, 60, 90, 120).forEach { preset ->
                    FilterChip(
                        selected = minutes == preset,
                        onClick = {
                            minutesText = preset.toString()
                            error = null
                        },
                        label = { Text("${preset}m") },
                    )
                }
            }

            if (needsCode) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Raising the limit needs your override code.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { char -> char.isDigit() }.take(32) },
                    label = { Text("Override code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (!valid) return@Button
                    val needsCodeNow = needsCode
                    if (needsCodeNow && code.length < 16) {
                        error = "Enter the full override code."
                        return@Button
                    }
                    saving = true
                    error = null
                    scope.launch {
                        val result = onSave(
                            RuleEntity(
                                packageName = packageName,
                                label = label,
                                dailyLimitMillis = newLimit,
                            ),
                            if (needsCodeNow) code else null,
                        )
                        saving = false
                        if (result.success) onDone() else error = result.message
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving..." else "Save limit")
            }

            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove app limit", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDelete && existing != null) {
        DeleteDialog(
            label = existing.rule.label,
            onDismiss = { showDelete = false },
            onConfirm = { deleteCode ->
                scope.launch {
                    val result = onDelete(packageName, deleteCode)
                    if (result.success) {
                        showDelete = false
                        onDone()
                    } else {
                        showDelete = false
                        error = result.message
                    }
                }
            },
        )
    }
}

@Composable
private fun DeleteDialog(
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $label?") },
        text = {
            Column {
                Text("This deletes the limit. Enter your override code to confirm.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { char -> char.isDigit() }.take(32) },
                    label = { Text("Override code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) },
                enabled = code.length >= 16,
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
