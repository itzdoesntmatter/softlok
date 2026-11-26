// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.view.accessibility.AccessibilityManager
import app.overplay.OverplayApp
import app.overplay.data.RuleEntity
import app.overplay.data.SecurityState
import app.overplay.data.SecurityStore
import app.overplay.data.VerifyResult
import app.overplay.domain.TimeText
import app.overplay.service.AppLimitService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionState(
    val accessibility: Boolean = false,
) {
    val allGranted: Boolean get() = accessibility
}

data class RuleUi(
    val rule: RuleEntity,
    val usedMillis: Long,
    val extraMillis: Long,
    val remainingMillis: Long,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val rules: List<RuleUi> = emptyList(),
    val security: SecurityState = SecurityState(),
    val permissions: PermissionState = PermissionState(),
    val dayKey: String = "",
)

data class InstalledApp(
    val packageName: String,
    val label: String,
)

data class ActionResult(val success: Boolean, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as OverplayApp).container
    private val repository = container.repository
    private val securityStore = container.securityStore
    private val permissions = MutableStateFlow(readPermissions())

    private val dayKeys = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.map { TimeText.dayKey(it) }.distinctUntilChanged()

    private val usage = dayKeys.flatMapLatest { repository.usage(it) }

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.rules,
        usage,
        securityStore.state,
        permissions,
        dayKeys,
    ) { rules, usageRows, security, permissionState, dayKey ->
        val usageByPackage = usageRows.associateBy { it.packageName }
        val ruleRows = rules.map { rule ->
            val row = usageByPackage[rule.packageName]
            val used = row?.usedMillis ?: 0
            val extra = row?.extraMillis ?: 0
            RuleUi(
                rule = rule,
                usedMillis = used,
                extraMillis = extra,
                remainingMillis = TimeText.remaining(rule.dailyLimitMillis, used, extra),
            )
        }
        DashboardUiState(
            loading = false,
            rules = ruleRows,
            security = security,
            permissions = permissionState,
            dayKey = dayKey,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        permissions.value = readPermissions()
        viewModelScope.launch {
            securityStore.completeRecoveryIfDue(System.currentTimeMillis())
        }
    }

    fun startRecovery() {
        viewModelScope.launch { securityStore.startRecovery(System.currentTimeMillis()) }
    }

    fun cancelRecovery() {
        viewModelScope.launch { securityStore.cancelRecovery() }
    }

    suspend fun verifyCode(code: String): VerifyResult =
        securityStore.verify(code, System.currentTimeMillis())

    suspend fun setCode(code: String) {
        securityStore.setCode(code)
    }

    suspend fun saveRule(rule: RuleEntity, code: String?): ActionResult {
        val existing = repository.rule(rule.packageName)
        val needsCode = existing != null && rule.dailyLimitMillis > existing.dailyLimitMillis
        val suppliedCode = code.orEmpty()
        if (needsCode && suppliedCode.isBlank()) {
            return ActionResult(false, "Enter the override code to raise this limit.")
        }
        if (needsCode) {
            when (val result = securityStore.verify(suppliedCode, System.currentTimeMillis())) {
                VerifyResult.Success -> Unit
                is VerifyResult.Wrong ->
                    return ActionResult(false, "Wrong code. ${result.attemptsLeft} attempts left.")
                is VerifyResult.Locked ->
                    return ActionResult(false, "Too many attempts. Try again shortly.")
                VerifyResult.Expired ->
                    return ActionResult(false, "The override code was reset.")
                VerifyResult.NoCode ->
                    return ActionResult(false, "No override code is set.")
            }
        }
        repository.saveRule(rule)
        return ActionResult(true)
    }

    suspend fun deleteRule(packageName: String, code: String): ActionResult {
        when (val result = securityStore.verify(code, System.currentTimeMillis())) {
            VerifyResult.Success -> Unit
            is VerifyResult.Wrong ->
                return ActionResult(false, "Wrong code. ${result.attemptsLeft} attempts left.")
            is VerifyResult.Locked ->
                return ActionResult(false, "Too many attempts. Try again shortly.")
            VerifyResult.Expired ->
                return ActionResult(false, "The override code was reset.")
            VerifyResult.NoCode ->
                return ActionResult(false, "No override code is set.")
        }
        repository.deleteRule(packageName)
        return ActionResult(true)
    }

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        resolved
            .asSequence()
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val packageName = activity.packageName
                if (packageName == context.packageName) return@mapNotNull null
                val label = info.loadLabel(context.packageManager).toString()
                InstalledApp(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun readPermissions(): PermissionState {
        val context = getApplication<Application>()
        return PermissionState(
            accessibility = hasAccessibility(context),
        )
    }

    private fun hasAccessibility(context: Application): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(context, AppLimitService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                serviceInfo.packageName == expected.packageName &&
                    serviceInfo.name == expected.className
            }
    }
}
