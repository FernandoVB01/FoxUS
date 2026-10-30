package com.foxus.app.intervention

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.foxus.app.FoxusApplication
import com.foxus.app.audio.FoxSoundEffect
import kotlinx.coroutines.flow.first

/**
 * Background Worker (`WorkManager`) that evaluates urgent/pressure tasks via Backend + Gemma 4
 * and fires FOX's Duolingo-style Heads-Up Notification (and optional opt-in floating card).
 */
class FoxPressureWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FoxusApplication ?: return Result.success()
        val container = app.container
        val localSettings = container.settingsDataStore.settingsFlow.first()

        container.endpointConfig.baseUrl = localSettings.backendBaseUrl
        container.endpointConfig.demoAccessToken = localSettings.demoAccessToken
        container.endpointConfig.deviceSharedToken = localSettings.deviceSharedToken

        val demoForce = inputData.getBoolean("demo_force", false)
        val taskId = inputData.getString("task_id")?.ifBlank { null }

        if (localSettings.interventionLevel <= 0 && !demoForce) {
            return Result.success()
        }

        val evalResult = container.repository.evaluateProactivePressure(
            taskId = taskId,
            demoForce = demoForce
        ).getOrNull()

        if (evalResult != null && evalResult.shouldInterrupt && evalResult.mission != null) {
            if (evalResult.mission.policy.audioAllowed && localSettings.audioEnabled) {
                container.audioManager.playEffectById(evalResult.mission.hook.soundId)
            }
            container.interventionManager.postHeadsUpIntervention(
                mission = evalResult.mission,
                reasonShort = evalResult.reasonShort,
                allowExperimentalOverlay = localSettings.experimentalOverlayEnabled
            )
            return Result.success()
        }

        // Offline fallback when demo_force is requested without backend reachability
        if (demoForce) {
            val cachedTasks = container.repository.cachedTasksFlow.first()
            val chosen = cachedTasks.firstOrNull { it.id == taskId } ?: cachedTasks.firstOrNull()
            if (chosen != null) {
                val fallbackMission = container.repository.generateMission(
                    taskId = chosen.id,
                    taskTitleFallback = chosen.title,
                    requestedMode = "challenge"
                )
                container.audioManager.playEffect(FoxSoundEffect.CHALLENGE_START)
                container.interventionManager.postHeadsUpIntervention(
                    mission = fallbackMission,
                    reasonShort = "⚡ Prueba de interrupción inmersiva de FOX",
                    allowExperimentalOverlay = localSettings.experimentalOverlayEnabled
                )
            }
        }

        return Result.success()
    }
}
