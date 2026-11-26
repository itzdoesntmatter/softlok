// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.domain

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object TimeText {
    fun dayKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    fun remaining(limitMillis: Long, usedMillis: Long, extraMillis: Long): Long =
        (limitMillis + extraMillis - usedMillis).coerceAtLeast(0)

    fun clock(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
            minutes > 0 -> "${minutes}m"
            safe > 0 -> "<1m"
            else -> "0m"
        }
    }

    fun countdown(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val days = TimeUnit.MILLISECONDS.toDays(safe)
        val hours = TimeUnit.MILLISECONDS.toHours(safe) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        return if (days > 0) "${days}d ${hours}h" else "${hours}h ${minutes.toString().padStart(2, '0')}m"
    }
}
