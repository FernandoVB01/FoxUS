package com.foxus.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foxus.app.intervention.FoxInterventionManager
import com.foxus.app.ui.navigation.FoxusNavHost
import com.foxus.app.ui.theme.FoxusTheme
import com.foxus.app.viewmodel.FoxusMainViewModel

data class PendingInterruptDeepLink(
    val action: String,
    val taskId: String?,
    val hookMode: String?,
    val nonce: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<PendingInterruptDeepLink?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* User chose notification preference */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        extractInterruptIntent(intent)

        val appContainer = (application as FoxusApplication).container

        setContent {
            FoxusTheme {
                val mainViewModel: FoxusMainViewModel = viewModel(
                    factory = FoxusMainViewModel.factory(
                        repository = appContainer.repository,
                        settingsStore = appContainer.settingsDataStore,
                        endpointConfig = appContainer.endpointConfig,
                        audioController = appContainer.audioManager,
                        interventionManager = appContainer.interventionManager,
                        wifiTransport = appContainer.wifiTransport,
                        bleTransport = appContainer.bleTransport
                    )
                )
                FoxusNavHost(
                    viewModel = mainViewModel,
                    pendingDeepLink = pendingDeepLink,
                    onConsumeDeepLink = { pendingDeepLink = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractInterruptIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val appContainer = (application as FoxusApplication).container
        appContainer.interventionManager.dismissOverlay()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun extractInterruptIntent(intent: Intent?) {
        val action = intent?.getStringExtra(FoxInterventionManager.EXTRA_INTERRUPT_ACTION) ?: return
        pendingDeepLink = PendingInterruptDeepLink(
            action = action,
            taskId = intent.getStringExtra(FoxInterventionManager.EXTRA_TASK_ID),
            hookMode = intent.getStringExtra(FoxInterventionManager.EXTRA_HOOK_MODE)
        )
    }
}
