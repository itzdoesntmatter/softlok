// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RuleEntity::class, UsageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SoftlokDatabase : RoomDatabase() {
    abstract fun dao(): SoftlokDao

    companion object {
        @Volatile
        private var instance: SoftlokDatabase? = null

        fun get(context: Context): SoftlokDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SoftlokDatabase::class.java,
                    "softlok.db",
                ).build().also { instance = it }
            }
    }
}
