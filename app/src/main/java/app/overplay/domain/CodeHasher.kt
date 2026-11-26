// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CodeHasher {
    const val CODE_LENGTH = 24
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16

    fun generate(random: SecureRandom = SecureRandom(), length: Int = CODE_LENGTH): String {
        require(length in 16..32)
        return buildString(length) {
            repeat(length) { append(random.nextInt(10)) }
        }
    }

    fun group(code: String): String = code.chunked(4).joinToString(" ")

    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(SALT_BYTES).also(random::nextBytes)

    fun hash(code: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(
        code: String,
        saltBase64: String,
        iterations: Int,
        expectedHashBase64: String,
    ): Boolean {
        val salt = Base64.getDecoder().decode(saltBase64)
        val actual = hash(code, salt, iterations)
        val expected = Base64.getDecoder().decode(expectedHashBase64)
        return MessageDigest.isEqual(actual, expected)
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
