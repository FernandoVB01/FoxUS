package com.foxus.app.data.repository

import com.foxus.app.data.local.CachedTaskEntity
import com.foxus.app.data.local.FoxusDao
import com.foxus.app.data.remote.AgentTraceTimelineDto
import com.foxus.app.data.remote.DeviceDto
import com.foxus.app.data.remote.DeviceRegisterRequestDto
import com.foxus.app.data.remote.FoxusApiService
import com.foxus.app.data.remote.GenerateMissionRequestDto
import com.foxus.app.data.remote.GenerateMissionResponseDto
import com.foxus.app.data.remote.GoogleAuthUrlResponseDto
import com.foxus.app.data.remote.GoogleConnectIcalRequestDto
import com.foxus.app.data.remote.GoogleConnectTokenRequestDto
import com.foxus.app.data.remote.GoogleStatusResponseDto
import com.foxus.app.data.remote.GoogleSyncResponseDto
import com.foxus.app.data.remote.HealthResponseDto
import com.foxus.app.data.remote.HookDismissRequestDto
import com.foxus.app.data.remote.HookDismissResponseDto
import com.foxus.app.data.remote.HookProposalDto
import com.foxus.app.data.remote.LearningSnapshotDto
import com.foxus.app.data.remote.MicrotaskProposalDto
import com.foxus.app.data.remote.PolicyDecisionDto
import com.foxus.app.data.remote.PressureEvaluationRequestDto
import com.foxus.app.data.remote.PressureEvaluationResponseDto
import com.foxus.app.data.remote.RewardProfileDto
import com.foxus.app.data.remote.RewardTransactionDto
import com.foxus.app.data.remote.SessionActionResponseDto
import com.foxus.app.data.remote.SessionCompleteRequestDto
import com.foxus.app.data.remote.SessionDto
import com.foxus.app.data.remote.SessionEvidenceVerifyRequestDto
import com.foxus.app.data.remote.SessionEvidenceVerifyResponseDto
import com.foxus.app.data.remote.SessionStartRequestDto
import com.foxus.app.data.remote.SessionStopRequestDto
import com.foxus.app.data.remote.TaskContextDto
import com.foxus.app.data.remote.TaskCreateRequestDto
import com.foxus.app.data.remote.TaskDto
import com.foxus.app.data.remote.TaskUpdateRequestDto
import com.foxus.app.data.remote.UserPreferenceUpdateDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FoxusRepository(
    private val api: FoxusApiService,
    private val dao: FoxusDao
) {
    val cachedTasksFlow: Flow<List<CachedTaskEntity>> = dao.observeTasks()

    suspend fun checkBackendHealth(): Result<HealthResponseDto> = runCatching {
        api.getHealth()
    }

    suspend fun refreshTasks(): Result<List<TaskDto>> = runCatching {
        val remoteTasks = api.listTasks()
        val now = System.currentTimeMillis()
        val entities = remoteTasks.map { task ->
            val latestMicro = task.microtasks.maxByOrNull { it.createdAt }
            CachedTaskEntity(
                id = task.id,
                title = task.title,
                description = task.description,
                source = task.source,
                deadline = task.deadline,
                needsPressure = task.needsPressure,
                status = task.status,
                latestMicrotaskTitle = latestMicro?.title,
                latestMicrotaskSeconds = latestMicro?.estimatedSeconds,
                updatedAtEpochMs = now
            )
        }
        dao.clearTasks()
        dao.upsertTasks(entities)
        remoteTasks
    }

    suspend fun createTask(
        title: String,
        description: String?,
        needsPressure: Boolean = false,
        deadlineIso: String? = null
    ): Result<TaskDto> {
        val remoteAttempt = runCatching {
            api.createTask(
                TaskCreateRequestDto(
                    title = title.trim(),
                    description = description?.trim(),
                    deadline = deadlineIso,
                    needsPressure = needsPressure
                )
            )
        }
        remoteAttempt.onSuccess { created ->
            dao.upsertTask(
                CachedTaskEntity(
                    id = created.id,
                    title = created.title,
                    description = created.description,
                    source = created.source,
                    deadline = created.deadline,
                    needsPressure = created.needsPressure,
                    status = created.status,
                    latestMicrotaskTitle = null,
                    latestMicrotaskSeconds = null,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            return Result.success(created)
        }

        val localId = UUID.randomUUID().toString()
        val offlineTask = TaskDto(
            id = localId,
            title = title.trim(),
            description = description?.trim(),
            source = "local_offline",
            deadline = deadlineIso,
            needsPressure = needsPressure,
            status = "pending"
        )
        dao.upsertTask(
            CachedTaskEntity(
                id = offlineTask.id,
                title = offlineTask.title,
                description = offlineTask.description,
                source = offlineTask.source,
                deadline = offlineTask.deadline,
                needsPressure = offlineTask.needsPressure,
                status = offlineTask.status,
                latestMicrotaskTitle = null,
                latestMicrotaskSeconds = null,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        return Result.success(offlineTask)
    }

    suspend fun toggleTaskPressure(task: CachedTaskEntity, newNeedsPressure: Boolean): Result<Unit> =
        runCatching {
            dao.upsertTask(task.copy(needsPressure = newNeedsPressure))
            runCatching {
                api.updateTask(
                    taskId = task.id,
                    payload = TaskUpdateRequestDto(needsPressure = newNeedsPressure)
                )
            }
        }

    suspend fun generateMission(
        taskId: String,
        taskTitleFallback: String,
        requestedMode: String = "challenge",
        availableMinutes: Int = 5
    ): GenerateMissionResponseDto {
        val remote = runCatching {
            api.generateMission(
                GenerateMissionRequestDto(
                    taskId = taskId,
                    availableMinutes = availableMinutes,
                    requestedMode = requestedMode,
                    dispatchToDevice = true,
                    ignoreCooldownForDemo = true
                )
            )
        }.getOrNull()

        if (remote != null) {
            return remote
        }

        val isSlideTask = taskTitleFallback.contains("diapositiva", ignoreCase = true) ||
            taskTitleFallback.contains("presentaci", ignoreCase = true)
        val microTitle = if (isSlideTask) {
            "Crear portada de presentación"
        } else {
            "Preparar primer paso visible"
        }
        val bulletSteps = if (isSlideTask) {
            listOf(
                "Abre tu archivo de presentación",
                "Crea solo la diapositiva de portada",
                "Escribe el título y guarda"
            )
        } else {
            listOf(
                "Abre tu archivo para '${taskTitleFallback.take(22)}'",
                "Escribe el encabezado inicial",
                "Guarda ese primer avance"
            )
        }
        val msg = if (requestedMode == "fox_bzz") {
            "¡Bzz! (Offline) ¿3 min rápidos para '$microTitle'?"
        } else {
            "¡Reto de 3 min (Offline)! $microTitle."
        }

        return GenerateMissionResponseDto(
            traceId = "offline_${UUID.randomUUID().toString().take(8)}",
            taskId = taskId,
            microtaskId = null,
            hookId = null,
            isSimulatedAi = true,
            policy = PolicyDecisionDto(allowed = true, effectiveIntensity = 2, effectiveChannel = "in_app"),
            context = TaskContextDto(
                taskId = taskId,
                taskTitle = taskTitleFallback,
                contextualSummary = "Modo local offline claramente identificado."
            ),
            microtask = MicrotaskProposalDto(
                taskId = taskId,
                title = microTitle,
                lcdShortTitle = microTitle.take(16),
                description = bulletSteps.joinToString(". "),
                steps = bulletSteps,
                estimatedSeconds = 180,
                successCriteria = "Primer paso guardado.",
                rationale = "Simulación offline honesta.",
                generationSource = "deterministic_fallback",
                modelName = "offline_local_fallback"
            ),
            hook = HookProposalDto(
                hookType = requestedMode,
                visualModeName = if (requestedMode == "fox_bzz") "La Mosca Traviesa" else "Desafío Relámpago",
                message = msg,
                soundId = if (requestedMode == "fox_bzz") "fly_buzz" else "challenge_start",
                animationId = if (requestedMode == "fox_bzz") "fox_playful" else "fox_challenge",
                generationSource = "deterministic_fallback",
                modelName = "offline_local_fallback"
            ),
            learning = LearningSnapshotDto(recommendedHookType = requestedMode)
        )
    }

    suspend fun evaluateProactivePressure(
        taskId: String? = null,
        demoForce: Boolean = false
    ): Result<PressureEvaluationResponseDto> = runCatching {
        api.evaluatePressure(
            PressureEvaluationRequestDto(
                taskId = taskId,
                demoForce = demoForce,
                dispatchToDevice = true
            )
        )
    }

    suspend fun fetchGoogleStatus(): Result<GoogleStatusResponseDto> = runCatching {
        api.getGoogleStatus()
    }

    suspend fun fetchGoogleAuthUrl(): Result<GoogleAuthUrlResponseDto> = runCatching {
        api.getGoogleAuthUrl()
    }

    suspend fun connectGoogleAccessToken(
        accessToken: String,
        icalUrl: String?
    ): Result<GoogleSyncResponseDto> = runCatching {
        api.connectGoogleToken(
            GoogleConnectTokenRequestDto(
                accessToken = accessToken,
                calendarIcalUrl = icalUrl?.ifBlank { null }
            )
        )
    }

    suspend fun connectGoogleIcal(icalUrl: String): Result<GoogleSyncResponseDto> = runCatching {
        api.connectGoogleIcal(GoogleConnectIcalRequestDto(calendarIcalUrl = icalUrl))
    }

    suspend fun syncGoogleNow(): Result<GoogleSyncResponseDto> = runCatching {
        api.syncGoogleNow()
    }

    suspend fun disconnectGoogle(): Result<Unit> = runCatching {
        api.disconnectGoogle()
    }

    suspend fun fetchRewardProfile(): Result<RewardProfileDto> = runCatching {
        api.getRewardProfile()
    }

    suspend fun fetchRewardHistory(): Result<List<RewardTransactionDto>> = runCatching {
        api.getRewardHistory()
    }

    suspend fun fetchSessionsHistory(): Result<List<SessionDto>> = runCatching {
        api.getSessionsHistory()
    }

    suspend fun fetchTraceTimeline(traceId: String): Result<AgentTraceTimelineDto> = runCatching {
        api.getTraceTimeline(traceId)
    }

    suspend fun startSession(microtaskId: String, hookId: String?): Result<SessionActionResponseDto> =
        runCatching {
            api.startSession(SessionStartRequestDto(microtaskId = microtaskId, hookId = hookId))
        }

    suspend fun completeSession(
        sessionId: String,
        voluntaryContinuity: Boolean,
        actualSeconds: Int
    ): Result<SessionActionResponseDto> = runCatching {
        api.completeSession(
            sessionId = sessionId,
            payload = SessionCompleteRequestDto(
                source = "android",
                voluntaryContinuity = voluntaryContinuity,
                actualDurationSeconds = actualSeconds
            )
        )
    }

    suspend fun verifySessionEvidence(
        sessionId: String,
        evidenceType: String,
        imageBase64: String? = null,
        voiceTranscript: String? = null,
        checkedStepsCount: Int = 0,
        actualSeconds: Int = 180
    ): Result<SessionEvidenceVerifyResponseDto> = runCatching {
        api.verifySessionEvidence(
            sessionId = sessionId,
            payload = SessionEvidenceVerifyRequestDto(
                evidenceType = evidenceType,
                imageBase64 = imageBase64,
                imageMimeType = "image/jpeg",
                voiceTranscript = voiceTranscript,
                checkedStepsCount = checkedStepsCount,
                actualDurationSeconds = actualSeconds
            )
        )
    }

    suspend fun stopSession(sessionId: String, reason: String): Result<SessionActionResponseDto> =
        runCatching {
            api.stopSession(sessionId = sessionId, payload = SessionStopRequestDto(reason = reason))
        }

    suspend fun dismissHook(hookId: String, reason: String): Result<HookDismissResponseDto> =
        runCatching {
            api.dismissHook(hookId = hookId, payload = HookDismissRequestDto(reason = reason))
        }

    suspend fun listDevices(): Result<List<DeviceDto>> = runCatching {
        api.listDevices()
    }

    suspend fun registerDevice(
        deviceName: String = "FoxusWatch",
        deviceType: String = "esp32_watch"
    ): Result<DeviceDto> = runCatching {
        api.registerDevice(DeviceRegisterRequestDto(deviceName = deviceName, deviceType = deviceType))
    }

    suspend fun syncPreferencesToServer(
        interventionLevel: Int,
        audioEnabled: Boolean,
        musicEnabled: Boolean
    ): Result<Unit> = runCatching {
        api.updatePreferences(
            UserPreferenceUpdateDto(
                interventionLevel = interventionLevel,
                audioEnabled = audioEnabled,
                musicEnabled = musicEnabled
            )
        )
    }
}
