// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.data

import kotlinx.coroutines.flow.Flow

class SoftlokRepository(private val dao: SoftlokDao) {
    val rules: Flow<List<RuleEntity>> = dao.observeRules()

    fun usage(dayKey: String): Flow<List<UsageEntity>> = dao.observeUsage(dayKey)

    suspend fun rule(packageName: String): RuleEntity? = dao.getRule(packageName)

    suspend fun rules(): List<RuleEntity> = dao.getRules()

    suspend fun saveRule(rule: RuleEntity) {
        dao.upsertRule(rule)
    }

    suspend fun deleteRule(packageName: String) {
        dao.deleteRule(packageName)
    }

    suspend fun usageFor(packageName: String, dayKey: String): UsageEntity {
        dao.insertUsageIfMissing(UsageEntity(packageName, dayKey))
        return dao.getUsage(packageName, dayKey) ?: UsageEntity(packageName, dayKey)
    }

    suspend fun addUsed(packageName: String, dayKey: String, millis: Long) {
        if (millis <= 0) return
        dao.insertUsageIfMissing(UsageEntity(packageName, dayKey))
        dao.addUsed(packageName, dayKey, millis)
    }

    suspend fun addExtra(packageName: String, dayKey: String, millis: Long) {
        if (millis <= 0) return
        dao.insertUsageIfMissing(UsageEntity(packageName, dayKey))
        dao.addExtra(packageName, dayKey, millis)
    }
}
