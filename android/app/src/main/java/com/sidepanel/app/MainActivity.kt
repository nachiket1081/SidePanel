package com.sidepanel.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sidepanel.app.data.model.PerformanceHistoryBuffer
import com.sidepanel.app.data.preferences.PairingSettings
import com.sidepanel.app.data.preferences.PreferencesManager
import com.sidepanel.app.data.repository.TelemetryRepository
import com.sidepanel.app.ui.dashboard.LandscapeDashboardScreen
import com.sidepanel.app.ui.theme.DarkCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: TelemetryRepository
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive sticky landscape mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        val app = application as SidePanelApp
        repository = app.telemetryRepository
        preferencesManager = app.preferencesManager

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkCanvas
                ) {
                    val connectionState by repository.connectionState.collectAsState()
                    val telemetry by repository.telemetry.collectAsState(initial = null)
                    val pairingSettings by preferencesManager.pairingSettings.collectAsState(
                        initial = PairingSettings()
                    )
                    val historyBuffer = remember { PerformanceHistoryBuffer(maxCapacity = 60) }

                    LandscapeDashboardScreen(
                        connectionState = connectionState,
                        telemetry = telemetry,
                        savedSettings = pairingSettings,
                        historyBuffer = historyBuffer,
                        onConnect = { host, port, token ->
                            repository.connect(host = host, port = port, token = token)
                        },
                        onDisconnect = {
                            repository.disconnect()
                        },
                        onSavePairing = { host, port, token ->
                            CoroutineScope(Dispatchers.IO).launch {
                                preferencesManager.savePairing(host, port, token)
                            }
                        }
                    )
                }
            }
        }
    }
}
