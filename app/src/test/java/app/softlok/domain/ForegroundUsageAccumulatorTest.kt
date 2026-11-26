// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundUsageAccumulatorTest {
    @Test
    fun pairsResumeAndPause() {
        val usage = ForegroundUsageAccumulator(setOf("app"), endMillis = 100)
        usage.resumed("app", "Main", 10)
        usage.paused("app", "Main", 30)
        assertEquals(mapOf("app" to 20L), usage.result())
    }

    @Test
    fun activityTransitionDoesNotDoubleCount() {
        val usage = ForegroundUsageAccumulator(setOf("app"), endMillis = 100)
        usage.resumed("app", "Main", 10)
        usage.paused("app", "Main", 20)
        usage.resumed("app", "Detail", 20)
        usage.paused("app", "Detail", 40)
        assertEquals(mapOf("app" to 30L), usage.result())
    }

    @Test
    fun sessionStillOpenCountsToTheEnd() {
        val usage = ForegroundUsageAccumulator(setOf("app"), endMillis = 100)
        usage.resumed("app", "Main", 40)
        assertEquals(mapOf("app" to 60L), usage.result())
    }

    @Test
    fun otherPackagesAreIgnored() {
        val usage = ForegroundUsageAccumulator(setOf("app"), endMillis = 100)
        usage.resumed("other", "Main", 10)
        usage.paused("other", "Main", 90)
        assertEquals(mapOf("app" to 0L), usage.result())
    }

    @Test
    fun screenOffClosesOpenSessions() {
        val usage = ForegroundUsageAccumulator(setOf("app"), endMillis = 100)
        usage.resumed("app", "Main", 10)
        usage.stopAll(50)
        assertEquals(mapOf("app" to 40L), usage.result())
    }
}
