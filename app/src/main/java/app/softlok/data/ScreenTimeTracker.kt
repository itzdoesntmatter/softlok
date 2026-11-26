// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import app.softlok.domain.ForegroundUsageAccumulator
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenTimeTracker(private val context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val ops = appOps ?: return false
        return runCatching {
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    suspend fun todayUsage(
        packages: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptyMap()
        val manager = usageStats
            ?: return@withContext packages.associateWith { 0L }

        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val events = runCatching { manager.queryEvents(start, nowMillis) }.getOrNull()
            ?: return@withContext packages.associateWith { 0L }

        val accumulator = ForegroundUsageAccumulator(packages, nowMillis)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) break
            val packageName = event.packageName ?: continue
            if (packageName !in packages) continue

            if (Build.VERSION.SDK_INT >= 28 &&
                event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE
            ) {
                accumulator.stopAll(event.timeStamp)
                continue
            }

            val activity = event.className ?: packageName
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    accumulator.resumed(packageName, activity, event.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> accumulator.paused(packageName, activity, event.timeStamp)
            }
        }
        accumulator.result()
    }
}
