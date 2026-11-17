package com.sidepanel.app

import android.app.Application
import com.sidepanel.app.data.preferences.PreferencesManager
import com.sidepanel.app.data.repository.TelemetryRepository
import com.sidepanel.app.data.repository.TelemetryRepositoryImpl

class SidePanelApp : Application() {

    lateinit var telemetryRepository: TelemetryRepository
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesManager = PreferencesManager(this)
        telemetryRepository = TelemetryRepositoryImpl()
    }

    companion object {
        lateinit var instance: SidePanelApp
            private set
    }
}
