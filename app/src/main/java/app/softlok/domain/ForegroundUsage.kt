// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.domain

/**
 * Pairs foreground start/stop events into per-package durations.
 * Events are keyed by activity so an app's activity transition is not double counted.
 */
class ForegroundUsageAccumulator(
    private val packages: Set<String>,
    private val endMillis: Long,
) {
    private data class Active(val packageName: String, val startedAt: Long)

    private val active = mutableMapOf<String, Active>()
    private val totals = packages.associateWith { 0L }.toMutableMap()

    fun resumed(packageName: String, activity: String, at: Long) {
        if (packageName !in packages) return
        val key = "$packageName/$activity"
        active[key]?.let { close(it, at) }
        active[key] = Active(packageName, at)
    }

    fun paused(packageName: String, activity: String, at: Long) {
        if (packageName !in packages) return
        active.remove("$packageName/$activity")?.let { close(it, at) }
    }

    fun stopAll(at: Long) {
        active.values.toList().forEach { close(it, at) }
        active.clear()
    }

    fun result(): Map<String, Long> {
        active.values.forEach { close(it, endMillis) }
        active.clear()
        return totals.toMap()
    }

    private fun close(entry: Active, at: Long) {
        totals[entry.packageName] =
            (totals[entry.packageName] ?: 0L) + (at - entry.startedAt).coerceAtLeast(0)
    }
}
