// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val dailyLimitMillis: Long,
    val enabled: Boolean = true,
)

@Entity(tableName = "usage", primaryKeys = ["packageName", "dayKey"])
data class UsageEntity(
    val packageName: String,
    val dayKey: String,
    val usedMillis: Long = 0,
    val extraMillis: Long = 0,
)
