// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import app.softlok.MainActivity
import app.softlok.SoftlokApp
import app.softlok.data.SoftlokRepository
import app.softlok.data.RuleEntity
import app.softlok.data.SecurityStore
import app.softlok.domain.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppLimitService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutex = Mutex()

    private lateinit var repository: SoftlokRepository
    private lateinit var securityStore: SecurityStore
    private var initialized = false

    private var activePackage: String? = null
    private var activeDayKey: String? = null
    private var activeSinceElapsed = 0L
    private var pausedPackage: String? = null
    private var tickerJob: Job? = null

    @Volatile
    private var overlay: BlockOverlay? = null

    @Volatile
    private var blockedPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> scope.launch {
                    mutex.withLock {
                        flushLocked(SystemClock.elapsedRealtime())
                        pausedPackage = activePackage
                        stopTrackingLocked()
                    }
                }
                Intent.ACTION_USER_PRESENT -> resumePaused()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (initialized) return
        initialized = true

        val container = (application as SoftlokApp).container
        repository = container.repository
        securityStore = container.securityStore

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        scope.launch {
            events.collect { packageName -> handleForeground(packageName) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        events.tryEmit(packageName)
    }

    override fun onInterrupt() {
        scope.launch {
            mutex.withLock {
                flushLocked(SystemClock.elapsedRealtime())
                stopTrackingLocked()
            }
        }
    }

    override fun onDestroy() {
        runBlocking {
            mutex.withLock {
                flushLocked(SystemClock.elapsedRealtime())
                stopTrackingLocked()
            }
        }
        dismissOverlay()
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun handleForeground(packageName: String) {
        var blocked: Pair<RuleEntity, Long>? = null
        mutex.withLock {
            pausedPackage = null
            if (packageName == blockedPackage && overlay != null) return
            if (packageName == activePackage) return
            flushLocked(SystemClock.elapsedRealtime())
            stopTrackingLocked()
            withContext(Dispatchers.Main) { dismissOverlay() }
            blocked = startOrBlockLocked(packageName)
        }
        showBlockIfNeeded(blocked)
    }

    private fun resumePaused() {
        scope.launch {
            var blocked: Pair<RuleEntity, Long>? = null
            mutex.withLock {
                val packageName = pausedPackage ?: return@launch
                pausedPackage = null
                if (overlay != null) return@launch
                blocked = startOrBlockLocked(packageName)
            }
            showBlockIfNeeded(blocked)
        }
    }

    private suspend fun startOrBlockLocked(packageName: String): Pair<RuleEntity, Long>? {
        val rule = repository.rule(packageName)?.takeIf { it.enabled } ?: return null
        val dayKey = TimeText.dayKey(System.currentTimeMillis())
        val usage = repository.usageFor(packageName, dayKey)
        val remaining = TimeText.remaining(
            rule.dailyLimitMillis,
            usage.usedMillis,
            usage.extraMillis,
        )
        if (remaining <= 0) return rule to usage.usedMillis

        activePackage = packageName
        activeDayKey = dayKey
        activeSinceElapsed = SystemClock.elapsedRealtime()
        startTicker(rule)
        return null
    }

    private suspend fun showBlockIfNeeded(blocked: Pair<RuleEntity, Long>?) {
        val rule = blocked?.first ?: return
        val usage = repository.usageFor(
            rule.packageName,
            TimeText.dayKey(System.currentTimeMillis()),
        )
        withContext(Dispatchers.Main) {
            showBlock(rule, usage.usedMillis, usage.extraMillis)
        }
    }

    private fun startTicker(rule: RuleEntity) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                var waitMillis = CHECKPOINT_MILLIS
                var limitReached = false
                mutex.withLock {
                    val packageName = activePackage ?: return@launch
                    if (packageName != rule.packageName) return@launch
                    flushLocked(SystemClock.elapsedRealtime())

                    // Shortcut: a session that crosses midnight counts up to one checkpoint
                    // toward the previous day. Revisit if exact midnight attribution matters.
                    val dayKey = TimeText.dayKey(System.currentTimeMillis())
                    if (dayKey != activeDayKey) activeDayKey = dayKey
                    val usage = repository.usageFor(packageName, dayKey)
                    val remaining = TimeText.remaining(
                        rule.dailyLimitMillis,
                        usage.usedMillis,
                        usage.extraMillis,
                    )
                    if (remaining <= 0) {
                        stopTrackingLocked()
                        limitReached = true
                    } else {
                        waitMillis = minOf(remaining, CHECKPOINT_MILLIS)
                    }
                }
                if (limitReached) {
                    val usage = repository.usageFor(
                        rule.packageName,
                        TimeText.dayKey(System.currentTimeMillis()),
                    )
                    withContext(Dispatchers.Main) {
                        showBlock(rule, usage.usedMillis, usage.extraMillis)
                    }
                    return@launch
                }
                delay(waitMillis)
            }
        }
    }

    private suspend fun flushLocked(nowElapsed: Long) {
        val packageName = activePackage ?: return
        val dayKey = activeDayKey ?: return
        val elapsed = (nowElapsed - activeSinceElapsed).coerceAtLeast(0)
        activeSinceElapsed = nowElapsed
        repository.addUsed(packageName, dayKey, elapsed)
    }

    private fun stopTrackingLocked() {
        activePackage = null
        activeDayKey = null
        activeSinceElapsed = 0
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun showBlock(rule: RuleEntity, usedMillis: Long, extraMillis: Long) {
        if (overlay != null) return
        blockedPackage = rule.packageName
        overlay = BlockOverlay(
            context = this,
            rule = rule,
            usedMillis = usedMillis,
            extraMillis = extraMillis,
            securityStore = securityStore,
            repository = repository,
            scope = scope,
            onDismissed = {
                overlay = null
                blockedPackage = null
            },
            onExtensionGranted = {
                scope.launch {
                    mutex.withLock {
                        activePackage = rule.packageName
                        activeDayKey = TimeText.dayKey(System.currentTimeMillis())
                        activeSinceElapsed = SystemClock.elapsedRealtime()
                        startTicker(rule)
                    }
                }
            },
            onOpenApp = {
                dismissOverlay()
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onGoHome = {
                performGlobalAction(GLOBAL_ACTION_HOME)
                dismissOverlay()
            },
        ).also { it.show() }
    }

    private fun dismissOverlay() {
        overlay?.dismiss()
        overlay = null
        blockedPackage = null
    }

    companion object {
        private const val CHECKPOINT_MILLIS = 15_000L
    }
}
