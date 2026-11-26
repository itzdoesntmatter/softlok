// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.softlok.data.VerifyResult
import app.softlok.domain.CodeHasher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeSetupScreen(
    hasCode: Boolean,
    onBack: () -> Unit,
    onVerifyCode: suspend (String) -> VerifyResult,
    onSaveCode: suspend (String) -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var unlocked by rememberSaveable { mutableStateOf(!hasCode) }
    var oldCode by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (hasCode) "Change override code" else "Create override code") },
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
            if (!unlocked) {
                Text(
                    "Enter your current code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Changing the code is a protection change, so it needs the current one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = oldCode,
                    onValueChange = {
                        oldCode = it.filter { char -> char.isDigit() }.take(32)
                        error = null
                    },
                    label = { Text("Current code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        checking = true
                        error = null
                        scope.launch {
                            when (val result = onVerifyCode(oldCode)) {
                                VerifyResult.Success -> unlocked = true
                                is VerifyResult.Wrong ->
                                    error = "Wrong code. ${result.attemptsLeft} attempts left."
                                is VerifyResult.Locked ->
                                    error = "Too many attempts. Try again shortly."
                                VerifyResult.Expired, VerifyResult.NoCode -> unlocked = true
                            }
                            checking = false
                        }
                    },
                    enabled = oldCode.length >= 16 && !checking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (checking) "Checking..." else "Continue")
                }
                return@Column
            }

            val code = remember { CodeHasher.generate() }
            var confirm by rememberSaveable { mutableStateOf("") }

            Text(
                "Save this code",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This is shown once. Write it down or put it in a password manager. " +
                    "You need it to grant extra time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            SelectionContainer {
                Text(
                    CodeHasher.group(code),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                },
            ) {
                Text("Copy code")
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.filter { char -> char.isDigit() }.take(32) },
                label = { Text("Type the code to confirm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        onSaveCode(code)
                        saving = false
                        onDone()
                    }
                },
                enabled = confirm == code && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving..." else "Save code")
            }
        }
    }
}
