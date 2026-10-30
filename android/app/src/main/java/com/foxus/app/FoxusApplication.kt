package com.foxus.app

import android.app.Application
import com.foxus.app.audio.FoxAudioController
import com.foxus.app.audio.FoxAudioManager
import com.foxus.app.data.device.BleTransport
import com.foxus.app.data.device.DeviceTransport
import com.foxus.app.data.device.WifiHttpTransport
import com.foxus.app.data.local.FoxusDatabase
import com.foxus.app.data.local.SettingsDataStore
import com.foxus.app.data.remote.DynamicEndpointConfig
import com.foxus.app.data.remote.FoxusApiService
import com.foxus.app.data.remote.FoxusNetworkFactory
import com.foxus.app.data.repository.FoxusRepository
import com.foxus.app.intervention.FoxInterventionManager

class AppContainer(application: Application) {
    val endpointConfig = DynamicEndpointConfig()
    val settingsDataStore = SettingsDataStore(application)
    val database = FoxusDatabase.build(application)
    val apiService: FoxusApiService = FoxusNetworkFactory.createApiService(endpointConfig)
    val wifiTransport: DeviceTransport = WifiHttpTransport(apiService)
    val bleTransport: DeviceTransport = BleTransport()
    val audioManager: FoxAudioController = FoxAudioManager(application)
    val interventionManager = FoxInterventionManager(application)
    val repository = FoxusRepository(apiService, database.foxusDao())
}

class FoxusApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        if (::container.isInitialized) {
            container.interventionManager.dismissOverlay()
            container.audioManager.release()
        }
        super.onTerminate()
    }
}
