package com.sidepanel.app

import android.app.Application
import com.sidepanel.app.data.repository.TelemetryRepository
import com.sidepanel.app.data.repository.TelemetryRepositoryImpl

class SidePanelApp : Application() {

    lateinit var telemetryRepository: TelemetryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        telemetryRepository = TelemetryRepositoryImpl()
    }

    companion object {
        lateinit var instance: SidePanelApp
            private set
    }
}
