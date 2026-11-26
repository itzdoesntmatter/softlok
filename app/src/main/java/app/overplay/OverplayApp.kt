// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay

import android.app.Application
import android.content.Context
import app.overplay.data.OverplayDatabase
import app.overplay.data.OverplayRepository
import app.overplay.data.SecurityStore
import app.overplay.data.securityDataStore

class OverplayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val database = OverplayDatabase.get(context)
    val repository = OverplayRepository(database.dao())
    val securityStore = SecurityStore(context.securityDataStore)
}
