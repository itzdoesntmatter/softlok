// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.domain

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHasherTest {
    @Test
    fun generatedCodeIsDigitsOnlyWithRequestedLength() {
        val code = CodeHasher.generate(length = 24)
        assertEquals(24, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun generatedCodesDiffer() {
        assertTrue(CodeHasher.generate() != CodeHasher.generate())
    }

    @Test
    fun groupingReadsFourDigitsAtATime() {
        assertEquals("1234 5678 90", CodeHasher.group("1234567890"))
    }

    @Test
    fun hashMatchesOnlyTheOriginalCode() {
        val salt = CodeHasher.newSalt()
        val hash = CodeHasher.hash("123456789012345678901234", salt, iterations = 1_000)
        assertTrue(
            CodeHasher.verify(
                "123456789012345678901234",
                Base64.getEncoder().encodeToString(salt),
                1_000,
                Base64.getEncoder().encodeToString(hash),
            ),
        )
        assertFalse(
            CodeHasher.verify(
                "123456789012345678901235",
                Base64.getEncoder().encodeToString(salt),
                1_000,
                Base64.getEncoder().encodeToString(hash),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCodesOutsideSupportedLength() {
        CodeHasher.generate(length = 15)
    }
}
