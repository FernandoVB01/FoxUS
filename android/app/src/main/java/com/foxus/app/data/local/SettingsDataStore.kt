package com.foxus.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.foxusDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "foxus_user_settings"
)

data class LocalAppSettings(
    val onboardingCompleted: Boolean = false,
    val backendBaseUrl: String = "http://10.0.2.2:8000",
    val demoAccessToken: String = "foxus-demo-dev-2026",
    val deviceSharedToken: String = "foxus-device-dev-2026",
    val interventionLevel: Int = 2,
    val audioEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val voiceCoachEnabled: Boolean = true,
    val smartPressureScheduled: Boolean = false,
    val experimentalOverlayEnabled: Boolean = false
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val DEMO_TOKEN = stringPreferencesKey("demo_token")
        val DEVICE_TOKEN = stringPreferencesKey("device_token")
        val INTERVENTION_LEVEL = intPreferencesKey("intervention_level")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val VOICE_COACH_ENABLED = booleanPreferencesKey("voice_coach_enabled")
        val SMART_PRESSURE_SCHEDULED = booleanPreferencesKey("smart_pressure_scheduled")
        val EXPERIMENTAL_OVERLAY = booleanPreferencesKey("experimental_overlay_enabled")
    }

    val settingsFlow: Flow<LocalAppSettings> = context.foxusDataStore.data.map { prefs ->
        LocalAppSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            backendBaseUrl = prefs[Keys.BACKEND_URL] ?: "http://10.0.2.2:8000",
            demoAccessToken = prefs[Keys.DEMO_TOKEN] ?: "foxus-demo-dev-2026",
            deviceSharedToken = prefs[Keys.DEVICE_TOKEN] ?: "foxus-device-dev-2026",
            interventionLevel = prefs[Keys.INTERVENTION_LEVEL] ?: 2,
            audioEnabled = prefs[Keys.AUDIO_ENABLED] ?: true,
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: true,
            voiceCoachEnabled = prefs[Keys.VOICE_COACH_ENABLED] ?: true,
            smartPressureScheduled = prefs[Keys.SMART_PRESSURE_SCHEDULED] ?: false,
            experimentalOverlayEnabled = prefs[Keys.EXPERIMENTAL_OVERLAY] ?: false
        )
    }

    suspend fun completeOnboarding(interventionLevel: Int) {
        context.foxusDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = true
            prefs[Keys.INTERVENTION_LEVEL] = interventionLevel.coerceIn(0, 3)
        }
    }

    suspend fun updateConnectionConfig(baseUrl: String, demoToken: String, deviceToken: String) {
        context.foxusDataStore.edit { prefs ->
            prefs[Keys.BACKEND_URL] = baseUrl.trim().ifBlank { "http://10.0.2.2:8000" }
            prefs[Keys.DEMO_TOKEN] = demoToken.trim()
            prefs[Keys.DEVICE_TOKEN] = deviceToken.trim()
        }
    }

    suspend fun updateLocalPreferences(
        interventionLevel: Int,
        audioEnabled: Boolean,
        musicEnabled: Boolean,
        voiceCoachEnabled: Boolean? = null
    ) {
        context.foxusDataStore.edit { prefs ->
            prefs[Keys.INTERVENTION_LEVEL] = interventionLevel.coerceIn(0, 3)
            prefs[Keys.AUDIO_ENABLED] = audioEnabled
            prefs[Keys.MUSIC_ENABLED] = musicEnabled
            if (voiceCoachEnabled != null) {
                prefs[Keys.VOICE_COACH_ENABLED] = voiceCoachEnabled
            }
        }
    }

    suspend fun updatePressureConfig(
        smartScheduled: Boolean,
        overlayOptIn: Boolean
    ) {
        context.foxusDataStore.edit { prefs ->
            prefs[Keys.SMART_PRESSURE_SCHEDULED] = smartScheduled
            prefs[Keys.EXPERIMENTAL_OVERLAY] = overlayOptIn
        }
    }
}
