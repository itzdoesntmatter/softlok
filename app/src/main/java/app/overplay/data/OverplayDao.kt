// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OverplayDao {
    @Query("SELECT * FROM rules ORDER BY label COLLATE NOCASE")
    fun observeRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE packageName = :packageName")
    suspend fun getRule(packageName: String): RuleEntity?

    @Query("SELECT * FROM rules")
    suspend fun getRules(): List<RuleEntity>

    @Upsert
    suspend fun upsertRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE packageName = :packageName")
    suspend fun deleteRule(packageName: String)

    @Query("SELECT * FROM usage WHERE dayKey = :dayKey")
    fun observeUsage(dayKey: String): Flow<List<UsageEntity>>

    @Query("SELECT * FROM usage WHERE packageName = :packageName AND dayKey = :dayKey")
    suspend fun getUsage(packageName: String, dayKey: String): UsageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUsageIfMissing(usage: UsageEntity)

    @Query(
        "UPDATE usage SET usedMillis = usedMillis + :millis " +
            "WHERE packageName = :packageName AND dayKey = :dayKey",
    )
    suspend fun addUsed(packageName: String, dayKey: String, millis: Long)

    @Query(
        "UPDATE usage SET extraMillis = extraMillis + :millis " +
            "WHERE packageName = :packageName AND dayKey = :dayKey",
    )
    suspend fun addExtra(packageName: String, dayKey: String, millis: Long)

    @Query("DELETE FROM usage WHERE dayKey < :cutoffDayKey")
    suspend fun pruneUsage(cutoffDayKey: String)
}
