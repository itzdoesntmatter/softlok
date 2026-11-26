// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RuleEntity::class, UsageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OverplayDatabase : RoomDatabase() {
    abstract fun dao(): OverplayDao

    companion object {
        @Volatile
        private var instance: OverplayDatabase? = null

        fun get(context: Context): OverplayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OverplayDatabase::class.java,
                    "overplay.db",
                ).build().also { instance = it }
            }
    }
}
