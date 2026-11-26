// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.softlok.domain.CodeHasher
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore("security")

data class SecurityState(
    val hasCode: Boolean = false,
    val recoveryStartedAt: Long? = null,
    val recoveryDueAt: Long? = null,
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0,
) {
    fun isRecoveryDue(nowMillis: Long): Boolean =
        recoveryDueAt != null && nowMillis >= recoveryDueAt

    fun isLocked(nowMillis: Long): Boolean = nowMillis < lockedUntil
}

sealed interface VerifyResult {
    data object Success : VerifyResult
    data class Wrong(val attemptsLeft: Int) : VerifyResult
    data class Locked(val untilMillis: Long) : VerifyResult
    data object NoCode : VerifyResult
    data object Expired : VerifyResult
}

class SecurityStore(private val store: DataStore<Preferences>) {
    val state: Flow<SecurityState> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            SecurityState(
                hasCode = prefs[CODE_HASH] != null,
                recoveryStartedAt = prefs[RECOVERY_STARTED],
                recoveryDueAt = prefs[RECOVERY_DUE],
                failedAttempts = prefs[FAILED_ATTEMPTS] ?: 0,
                lockedUntil = prefs[LOCKED_UNTIL] ?: 0,
            )
        }

    suspend fun setCode(code: String) {
        val salt = CodeHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { CodeHasher.hash(code, salt) }
        store.edit { prefs ->
            prefs[CODE_HASH] = CodeHasher.encode(hash)
            prefs[SALT] = CodeHasher.encode(salt)
            prefs[ITERATIONS] = CodeHasher.ITERATIONS
            prefs.remove(RECOVERY_STARTED)
            prefs.remove(RECOVERY_DUE)
            prefs.remove(FAILED_ATTEMPTS)
            prefs.remove(LOCKED_UNTIL)
        }
    }

    suspend fun verify(code: String, nowMillis: Long): VerifyResult {
        val prefs = store.data.first()
        val expected = prefs[CODE_HASH] ?: return VerifyResult.NoCode
        val dueAt = prefs[RECOVERY_DUE]
        if (dueAt != null && nowMillis >= dueAt) return VerifyResult.Expired

        val lockedUntil = prefs[LOCKED_UNTIL] ?: 0
        if (nowMillis < lockedUntil) return VerifyResult.Locked(lockedUntil)

        val salt = prefs[SALT] ?: return VerifyResult.NoCode
        val iterations = prefs[ITERATIONS] ?: CodeHasher.ITERATIONS
        val matches = withContext(Dispatchers.Default) {
            CodeHasher.verify(code, salt, iterations, expected)
        }
        if (matches) {
            store.edit { prefs ->
                prefs.remove(FAILED_ATTEMPTS)
                prefs.remove(LOCKED_UNTIL)
            }
            return VerifyResult.Success
        }

        val attempts = (prefs[FAILED_ATTEMPTS] ?: 0) + 1
        return if (attempts >= MAX_ATTEMPTS) {
            val until = nowMillis + LOCKOUT_MILLIS
            store.edit { prefs ->
                prefs[FAILED_ATTEMPTS] = 0
                prefs[LOCKED_UNTIL] = until
            }
            VerifyResult.Locked(until)
        } else {
            store.edit { prefs -> prefs[FAILED_ATTEMPTS] = attempts }
            VerifyResult.Wrong(MAX_ATTEMPTS - attempts)
        }
    }

    suspend fun startRecovery(nowMillis: Long) {
        store.edit { prefs ->
            if (prefs[CODE_HASH] != null) {
                prefs[RECOVERY_STARTED] = nowMillis
                prefs[RECOVERY_DUE] = nowMillis + RECOVERY_DELAY_MILLIS
            }
        }
    }

    suspend fun cancelRecovery() {
        store.edit { prefs ->
            prefs.remove(RECOVERY_STARTED)
            prefs.remove(RECOVERY_DUE)
        }
    }

    /** Clears an expired code. Returns true when a reset became effective. */
    suspend fun completeRecoveryIfDue(nowMillis: Long): Boolean {
        var cleared = false
        store.edit { prefs ->
            val dueAt = prefs[RECOVERY_DUE] ?: return@edit
            if (nowMillis >= dueAt) {
                prefs.remove(CODE_HASH)
                prefs.remove(SALT)
                prefs.remove(ITERATIONS)
                prefs.remove(RECOVERY_STARTED)
                prefs.remove(RECOVERY_DUE)
                prefs.remove(FAILED_ATTEMPTS)
                prefs.remove(LOCKED_UNTIL)
                cleared = true
            }
        }
        return cleared
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
        const val RECOVERY_DELAY_MILLIS = 48 * 60 * 60 * 1000L

        private val CODE_HASH = stringPreferencesKey("code_hash")
        private val SALT = stringPreferencesKey("salt")
        private val ITERATIONS = intPreferencesKey("iterations")
        private val RECOVERY_STARTED = longPreferencesKey("recovery_started")
        private val RECOVERY_DUE = longPreferencesKey("recovery_due")
        private val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        private val LOCKED_UNTIL = longPreferencesKey("locked_until")
    }
}
