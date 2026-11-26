// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {
    @Test
    fun remainingIncludesExtensionsAndClampsAtZero() {
        assertEquals(30 * 60_000L, TimeText.remaining(60 * 60_000L, 45 * 60_000L, 15 * 60_000L))
        assertEquals(0, TimeText.remaining(10 * 60_000L, 30 * 60_000L, 0))
    }

    @Test
    fun dayKeyUsesTheRequestedTimeZone() {
        val instant = Instant.parse("2026-01-01T00:30:00Z")
        assertEquals(
            "2026-01-01",
            TimeText.dayKey(instant.toEpochMilli(), ZoneId.of("UTC")),
        )
        assertEquals(
            "2025-12-31",
            TimeText.dayKey(instant.toEpochMilli(), ZoneId.of("America/Los_Angeles")),
        )
    }

    @Test
    fun clockFormatsHoursAndMinutes() {
        assertEquals("0m", TimeText.clock(0))
        assertEquals("<1m", TimeText.clock(30_000L))
        assertEquals("5m", TimeText.clock(5 * 60_000L))
        assertEquals("1h 05m", TimeText.clock(65 * 60_000L))
    }

    @Test
    fun countdownFormatsDaysAndHours() {
        assertEquals("1d 2h", TimeText.countdown(26 * 60 * 60_000L))
        assertEquals("21h 00m", TimeText.countdown(21 * 60 * 60_000L))
    }

    @Test
    fun extensionMinutesBecomeMillis() {
        assertEquals(15 * 60_000L, Limits.extensionMillis(15))
    }
}
