// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.domain

object Limits {
    const val MIN_LIMIT_MINUTES = 5
    const val MAX_LIMIT_MINUTES = 24 * 60
    val EXTENSIONS_MINUTES = listOf(5, 15, 30, 60)

    fun minutesToMillis(minutes: Int): Long = minutes * 60_000L

    fun extensionMillis(minutes: Int): Long = minutes * 60_000L
}
