// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityStateTest {
    @Test
    fun recoveryIsNotDueBeforeTheDeadline() {
        val state = SecurityState(hasCode = true, recoveryDueAt = 1_000L)
        assertFalse(state.isRecoveryDue(999L))
    }

    @Test
    fun recoveryIsDueAtTheDeadline() {
        val state = SecurityState(hasCode = true, recoveryDueAt = 1_000L)
        assertTrue(state.isRecoveryDue(1_000L))
    }

    @Test
    fun lockoutExpiresAtItsDeadline() {
        val state = SecurityState(lockedUntil = 5_000L)
        assertTrue(state.isLocked(4_999L))
        assertFalse(state.isLocked(5_000L))
    }
}
