// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok

import android.app.Application
import android.content.Context
import app.softlok.data.SoftlokDatabase
import app.softlok.data.SoftlokRepository
import app.softlok.data.ScreenTimeTracker
import app.softlok.data.SecurityStore
import app.softlok.data.securityDataStore

class SoftlokApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val database = SoftlokDatabase.get(context)
    val repository = SoftlokRepository(database.dao())
    val securityStore = SecurityStore(context.securityDataStore)
    val screenTimeTracker = ScreenTimeTracker(context)
}
