package com.foxus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foxus.app.audio.FoxAudioController
import com.foxus.app.audio.FoxSoundEffect
import com.foxus.app.audio.NoOpFoxAudioController
import com.foxus.app.data.device.BleTransport
import com.foxus.app.data.device.DeviceTransport
import com.foxus.app.data.local.CachedTaskEntity
import com.foxus.app.data.local.LocalAppSettings
import com.foxus.app.data.local.SettingsDataStore
import com.foxus.app.data.remote.AgentTraceTimelineDto
import com.foxus.app.data.remote.DeviceDto
import com.foxus.app.data.remote.DynamicEndpointConfig
import com.foxus.app.data.remote.GenerateMissionResponseDto
import com.foxus.app.data.remote.GoogleStatusResponseDto
import com.foxus.app.data.remote.RefereeVerdictDto
import com.foxus.app.data.remote.RewardProfileDto
import com.foxus.app.data.remote.RewardTransactionDto
import com.foxus.app.data.remote.SessionDto
import com.foxus.app.data.repository.FoxusRepository
import com.foxus.app.intervention.FoxInterventionManager
import com.foxus.app.ui.components.FoxMascotState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

enum class ChallengePhase {
    IDLE_READY,
    RUNNING,
    VERIFYING_EVIDENCE,
    COMPLETED_VICTORY
}

data class FoxusUiState(
    val isLoading: Boolean = false,
    val isActionBusy: Boolean = false,
    val isVerifyingEvidence: Boolean = false,
    val settings: LocalAppSettings = LocalAppSettings(),
    val backendOnline: Boolean = false,
    val aiModeLabel: String = "Conectando...",
    val tasks: List<CachedTaskEntity> = emptyList(),
    val googleStatus: GoogleStatusResponseDto = GoogleStatusResponseDto(),
    val rewardProfile: RewardProfileDto = RewardProfileDto(),
    val rewardTransactions: List<RewardTransactionDto> = emptyList(),
    val recentSessions: List<SessionDto> = emptyList(),
    val devices: List<DeviceDto> = emptyList(),
    val useBleExperimental: Boolean = false,
    val activeTransportName: String = "Wi-Fi FoxusWatch",
    val lcdLine1: String = "FOXUS READY",
    val lcdLine2: String = "Waiting mission",
    val pendingDeviceCommandId: String? = null,
    val activeMission: GenerateMissionResponseDto? = null,
    val activeSessionId: String? = null,
    val challengePhase: ChallengePhase = ChallengePhase.IDLE_READY,
    val totalTimerSeconds: Int = 180,
    val remainingTimerSeconds: Int = 180,
    val elapsedSessionSeconds: Int = 0,
    val coachLiveMessage: String = "Árbitro FOX listo para dar el silbatazo inicial.",
    val latestRefereeVerdict: RefereeVerdictDto? = null,
    val lastEarnedXp: Int = 0,
    val latestTrace: AgentTraceTimelineDto? = null,
    val showImmersiveInterruptModal: Boolean = false,
    val interruptUrgencyReason: String = "",
    val interruptUrgencyScore: Int = 0,
    val overlayPermissionGranted: Boolean = false,
    val statusBanner: String? = null
)

fun formatCountdownClock(seconds: Int): String {
    val clamped = seconds.coerceAtLeast(0)
    val mins = clamped / 60
    val secs = clamped % 60
    return "%02d:%02d".format(mins, secs)
}

fun formatLcdRow16(raw: String): String =
    raw.trim().take(16)

fun resolveBulletSteps(mission: GenerateMissionResponseDto): List<String> {
    if (mission.microtask.steps.isNotEmpty()) {
        return mission.microtask.steps
    }
    val parts = mission.microtask.description
        .split(Regex("[.;\\n]+"))
        .map { it.trim() }
        .filter { it.length >= 4 }
        .take(2)
        .toMutableList()
    if (mission.microtask.successCriteria.isNotBlank()) {
        parts.add("Meta: ${mission.microtask.successCriteria.trim()}")
    }
    return if (parts.isNotEmpty()) parts else listOf(mission.microtask.title)
}

fun resolveHomeMascotState(
    interventionLevel: Int,
    hasActiveMission: Boolean,
    hookType: String?
): FoxMascotState = when {
    interventionLevel <= 0 -> FoxMascotState.SLEEP
    hookType == "fox_bzz" -> FoxMascotState.PLAYFUL
    hasActiveMission -> FoxMascotState.CURIOUS
    else -> FoxMascotState.IDLE
}

class FoxusMainViewModel(
    private val repository: FoxusRepository,
    private val settingsStore: SettingsDataStore,
    private val endpointConfig: DynamicEndpointConfig,
    private val audioController: FoxAudioController = NoOpFoxAudioController,
    private val interventionManager: FoxInterventionManager? = null,
    private val wifiTransport: DeviceTransport,
    private val bleTransport: DeviceTransport = BleTransport()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FoxusUiState(
            activeTransportName = wifiTransport.transportName,
            overlayPermissionGranted = interventionManager?.canDrawOverlays() ?: false
        )
    )
    val uiState: StateFlow<FoxusUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var pepTalkCursor: Int = 0

    private val pepTalkCatalog = listOf(
        "¡Vamos! No tiene que quedar perfecto hoy, solo rompe la inercia con este paso.",
        "¡Árbitro FOX reportando! Dos minutos de enfoque real valen más que dos horas postergando.",
        "¡Sigue adelante! Cada viñeta que marcas entrena tu disciplina sin agotarte.",
        "¡Respira y escribe lo primero que tengas en mente! Luego podrás pulirlo."
    )

    private val currentTransport: DeviceTransport
        get() = if (_uiState.value.useBleExperimental) bleTransport else wifiTransport

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { localSettings ->
                endpointConfig.baseUrl = localSettings.backendBaseUrl
                endpointConfig.demoAccessToken = localSettings.demoAccessToken
                endpointConfig.deviceSharedToken = localSettings.deviceSharedToken
                audioController.updateAudioConfiguration(
                    audioEnabled = localSettings.audioEnabled,
                    musicEnabled = localSettings.musicEnabled,
                    interventionLevel = localSettings.interventionLevel,
                    voiceCoachEnabled = localSettings.voiceCoachEnabled
                )
                interventionManager?.syncPeriodicPressureSchedule(localSettings.smartPressureScheduled)
                _uiState.update {
                    it.copy(
                        settings = localSettings,
                        overlayPermissionGranted = interventionManager?.canDrawOverlays() ?: false
                    )
                }
                refreshAllSilent()
            }
        }
        viewModelScope.launch {
            repository.cachedTasksFlow.collect { cached ->
                _uiState.update { it.copy(tasks = cached) }
            }
        }
    }

    fun refreshOverlayPermissionStatus() {
        _uiState.update {
            it.copy(overlayPermissionGranted = interventionManager?.canDrawOverlays() ?: false)
        }
    }

    fun refreshAllSilent() {
        viewModelScope.launch {
            val healthRes = repository.checkBackendHealth()
            healthRes.onSuccess { h ->
                val aiMode = if (h.ai?.apiKeyConfigured == true) {
                    "Gemma 4 activo"
                } else {
                    "Modo local"
                }
                _uiState.update { it.copy(backendOnline = h.status == "ok", aiModeLabel = aiMode) }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        backendOnline = false,
                        aiModeLabel = "Sin conexión"
                    )
                }
            }

            // Silent Google Tasks + Calendar auto-sync on app open
            repository.fetchGoogleStatus().onSuccess { status ->
                _uiState.update { it.copy(googleStatus = status) }
                if (status.connected) {
                    repository.syncGoogleNow()
                }
            }
            repository.refreshTasks()
            refreshRewardsAndHistory()
            refreshDevices()
        }
    }

    fun refreshGoogleStatus() {
        viewModelScope.launch {
            repository.fetchGoogleStatus().onSuccess { status ->
                _uiState.update { it.copy(googleStatus = status) }
            }
        }
    }

    fun requestGoogleOAuthUrl(onUrlReady: (String) -> Unit) {
        viewModelScope.launch {
            repository.fetchGoogleAuthUrl().onSuccess { res ->
                if (res.oauthConfigured && !res.authUrl.isNullOrBlank()) {
                    onUrlReady(res.authUrl)
                } else {
                    _uiState.update { it.copy(statusBanner = res.setupHint) }
                }
            }.onFailure {
                _uiState.update { it.copy(statusBanner = "No se pudo consultar OAuth en el servidor.") }
            }
        }
    }

    fun connectGoogleWithToken(accessToken: String, icalUrl: String?) {
        if (accessToken.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.connectGoogleAccessToken(accessToken, icalUrl).onSuccess { res ->
                repository.refreshTasks()
                refreshGoogleStatus()
                _uiState.update { it.copy(isLoading = false, statusBanner = res.detail) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, statusBanner = "Google Sync: ${err.message}")
                }
            }
        }
    }

    fun connectGoogleCalendarIcal(icalUrl: String) {
        if (icalUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.connectGoogleIcal(icalUrl).onSuccess { res ->
                refreshGoogleStatus()
                _uiState.update { it.copy(isLoading = false, statusBanner = res.detail) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, statusBanner = "Verifica el enlace .ics: ${err.message}")
                }
            }
        }
    }

    fun syncGoogleNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.syncGoogleNow().onSuccess { res ->
                repository.refreshTasks()
                refreshGoogleStatus()
                _uiState.update { it.copy(isLoading = false, statusBanner = res.detail) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, statusBanner = err.message ?: "Error al sincronizar Google")
                }
            }
        }
    }

    fun disconnectGoogle() {
        viewModelScope.launch {
            repository.disconnectGoogle().onSuccess {
                refreshGoogleStatus()
                _uiState.update { it.copy(statusBanner = "Cuenta Google desconectada.") }
            }
        }
    }

    fun refreshRewardsAndHistory() {
        viewModelScope.launch {
            repository.fetchRewardProfile().onSuccess { profile ->
                _uiState.update { it.copy(rewardProfile = profile) }
            }
            repository.fetchRewardHistory().onSuccess { txs ->
                _uiState.update { it.copy(rewardTransactions = txs) }
            }
            repository.fetchSessionsHistory().onSuccess { sessions ->
                _uiState.update { it.copy(recentSessions = sessions) }
            }
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            repository.listDevices().onSuccess { devList ->
                if (devList.isNotEmpty()) {
                    _uiState.update { it.copy(devices = devList) }
                } else {
                    repository.registerDevice(deviceName = "FoxusWatch").onSuccess { created ->
                        _uiState.update { it.copy(devices = listOf(created)) }
                    }
                }
            }
        }
    }

    fun completeOnboarding(interventionLevel: Int) {
        viewModelScope.launch {
            settingsStore.completeOnboarding(interventionLevel)
            audioController.playEffect(FoxSoundEffect.FOX_APPEAR)
            repository.syncPreferencesToServer(
                interventionLevel = interventionLevel,
                audioEnabled = _uiState.value.settings.audioEnabled,
                musicEnabled = _uiState.value.settings.musicEnabled
            )
        }
    }

    fun createNewTask(
        title: String,
        description: String?,
        needsPressure: Boolean = false,
        deadlineIso: String? = null,
        onCreated: () -> Unit = {}
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusBanner = null) }
            repository.createTask(title, description, needsPressure, deadlineIso)
            repository.refreshTasks()
            audioController.playEffect(FoxSoundEffect.FOX_APPEAR)
            _uiState.update {
                it.copy(isLoading = false, statusBanner = "Tarea agregada: ${title.trim()}")
            }
            onCreated()
        }
    }

    fun toggleTaskPressure(task: CachedTaskEntity) {
        viewModelScope.launch {
            val next = !task.needsPressure
            repository.toggleTaskPressure(task, next)
            _uiState.update {
                it.copy(
                    statusBanner = if (next) {
                        "🔥 Intervención prioritaria activada en \"${task.title.take(28)}\""
                    } else {
                        "Intervención prioritaria desactivada en \"${task.title.take(28)}\""
                    }
                )
            }
        }
    }

    fun updateSmartPressureSettings(scheduled: Boolean, overlayOptIn: Boolean) {
        viewModelScope.launch {
            settingsStore.updatePressureConfig(scheduled, overlayOptIn)
            interventionManager?.syncPeriodicPressureSchedule(scheduled)
            refreshOverlayPermissionStatus()
        }
    }

    fun playMinigameHitSound(completed: Boolean = false) {
        if (completed) {
            audioController.playEffect(FoxSoundEffect.XP_GAIN)
        } else {
            audioController.playEffect(FoxSoundEffect.FOX_APPEAR)
        }
    }

    fun triggerImmersiveInterruptNow() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val eval = repository.evaluateProactivePressure(demoForce = true).getOrNull()
            val mission = eval?.mission ?: run {
                val t = _uiState.value.tasks.firstOrNull()
                if (t != null) {
                    repository.generateMission(t.id, t.title, "challenge")
                } else null
            }

            if (mission != null) {
                val duration = mission.microtask.estimatedSeconds.coerceIn(60, 900)
                if (mission.policy.audioAllowed) {
                    audioController.playEffectById(mission.hook.soundId)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeMission = mission,
                        activeSessionId = null,
                        latestRefereeVerdict = null,
                        challengePhase = ChallengePhase.IDLE_READY,
                        totalTimerSeconds = duration,
                        remainingTimerSeconds = duration,
                        elapsedSessionSeconds = 0,
                        showImmersiveInterruptModal = true,
                        interruptUrgencyReason = eval?.reasonShort ?: "🔥 Ventana de arranque detectada por FOX",
                        interruptUrgencyScore = eval?.urgencyScore ?: 82
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, statusBanner = "Agrega primero una tarea pendiente.")
                }
            }
        }
    }

    fun scheduleBackgroundInterruptIn5s() {
        val targetTask = _uiState.value.tasks.firstOrNull { it.needsPressure && it.status != "completed" }
            ?: _uiState.value.tasks.firstOrNull { it.status != "completed" }
        interventionManager?.scheduleLiveTestInterruptIn5Seconds(targetTask?.id)
        _uiState.update {
            it.copy(
                statusBanner = "🔔 Sal a otra app o al inicio: FOX lanzará tu alerta emergente en 5 s."
            )
        }
    }

    fun dismissImmersiveModal() {
        _uiState.update { it.copy(showImmersiveInterruptModal = false) }
    }

    fun handleIncomingInterruptIntent(
        action: String?,
        taskId: String?,
        hookMode: String?,
        onNavigateToChallenge: () -> Unit
    ) {
        if (action.isNullOrBlank()) return
        interventionManager?.dismissOverlay()

        val existing = _uiState.value.activeMission
        if (existing != null && (taskId.isNullOrBlank() || existing.taskId == taskId)) {
            // Always open the 30s mini-game modal unless explicitly already running
            _uiState.update {
                it.copy(
                    showImmersiveInterruptModal = true,
                    interruptUrgencyReason = "🦊 Intervención activa · Supera el minijuego de 30s para arrancar"
                )
            }
            return
        }

        val targetTask = _uiState.value.tasks.firstOrNull { it.id == taskId }
            ?: _uiState.value.tasks.firstOrNull() ?: return
        requestMissionForTask(
            taskId = targetTask.id,
            taskTitle = targetTask.title,
            requestedMode = hookMode ?: "challenge"
        ) {
            _uiState.update {
                it.copy(
                    showImmersiveInterruptModal = true,
                    interruptUrgencyReason = "🦊 Intervención activa · Supera el minijuego de 30s para arrancar"
                )
            }
        }
    }

    fun requestMissionForTask(
        taskId: String,
        taskTitle: String,
        requestedMode: String = "challenge",
        onReady: (resolvedHookType: String) -> Unit = {}
    ) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            timerJob?.cancel()
            audioController.stopSpeech()
            audioController.setFocusSessionMusicActive(false)
            _uiState.update { it.copy(isLoading = true, statusBanner = null, latestRefereeVerdict = null) }
            val mission = repository.generateMission(
                taskId = taskId,
                taskTitleFallback = taskTitle,
                requestedMode = requestedMode
            )
            val duration = mission.microtask.estimatedSeconds.coerceIn(60, 900)
            if (mission.policy.audioAllowed) {
                audioController.playEffectById(mission.hook.soundId)
            }
            val introCoach = mission.hook.coachVoiceLines.firstOrNull()
                ?: "Árbitro FOX listo: toca iniciar para el silbatazo de salida."
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activeMission = mission,
                    activeSessionId = null,
                    pendingDeviceCommandId = mission.dispatchedDeviceCommandId,
                    challengePhase = ChallengePhase.IDLE_READY,
                    totalTimerSeconds = duration,
                    remainingTimerSeconds = duration,
                    elapsedSessionSeconds = 0,
                    coachLiveMessage = introCoach,
                    latestRefereeVerdict = null,
                    lastEarnedXp = 0,
                    lcdLine1 = "NEW MISSION!",
                    lcdLine2 = formatLcdRow16(mission.microtask.lcdShortTitle)
                )
            }
            if (!mission.traceId.startsWith("offline_")) {
                repository.fetchTraceTimeline(mission.traceId).onSuccess { timeline ->
                    _uiState.update { it.copy(latestTrace = timeline) }
                }
            }
            onReady(mission.hook.hookType)
        }
    }

    fun replayDiscreetBuzz() {
        audioController.playEffect(FoxSoundEffect.FLY_BUZZ)
    }

    fun startChallengeTimer(onStarted: () -> Unit = {}) {
        val state = _uiState.value
        val mission = state.activeMission ?: return
        if (state.isActionBusy) return
        if (state.challengePhase == ChallengePhase.RUNNING) {
            onStarted()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true, showImmersiveInterruptModal = false) }
            val prevLevel = state.rewardProfile.level
            var awardedStartXp = 0
            var remoteSessionId: String? = null
            val microId = mission.microtaskId
            if (microId != null) {
                repository.startSession(microId, mission.hookId).onSuccess { res ->
                    remoteSessionId = res.session.id
                    awardedStartXp = res.rewardsGranted.sumOf { it.xpAwarded }
                    if (res.currentLevel > prevLevel) {
                        audioController.playEffect(FoxSoundEffect.LEVEL_UP)
                    }
                    refreshRewardsAndHistory()
                }
            }

            val firstStep = resolveBulletSteps(mission).firstOrNull() ?: mission.microtask.title
            val kickoffSpeech = mission.hook.coachVoiceLines.getOrNull(0)
                ?: "¡Silbatazo inicial! Arrancamos ahora. Tu primer punto es: $firstStep."

            audioController.playEffect(FoxSoundEffect.REFEREE_WHISTLE)
            audioController.setFocusSessionMusicActive(true)
            audioController.speakCoachLine(kickoffSpeech, flushQueue = true)

            _uiState.update {
                it.copy(
                    isActionBusy = false,
                    activeSessionId = remoteSessionId,
                    challengePhase = ChallengePhase.RUNNING,
                    coachLiveMessage = kickoffSpeech,
                    latestRefereeVerdict = null,
                    lastEarnedXp = awardedStartXp,
                    lcdLine1 = "FOCUS MODE",
                    lcdLine2 = formatLcdRow16("${formatCountdownClock(it.remainingTimerSeconds)} LEFT"),
                    statusBanner = if (awardedStartXp > 0) "¡Silbatazo inicial! +$awardedStartXp XP" else "¡Reto en marcha!"
                )
            }
            launchCountdownLoop()
            onStarted()
        }
    }

    private fun launchCountdownLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.challengePhase == ChallengePhase.RUNNING &&
                _uiState.value.remainingTimerSeconds > 0
            ) {
                delay(1000L)
                val curr = _uiState.value
                if (curr.challengePhase != ChallengePhase.RUNNING) break

                val nextRemaining = (curr.remainingTimerSeconds - 1).coerceAtLeast(0)
                val nextElapsed = curr.elapsedSessionSeconds + 1
                val halfway = curr.totalTimerSeconds / 2
                val mission = curr.activeMission

                var updatedCoachMsg = curr.coachLiveMessage
                if (halfway >= 30 && nextElapsed == halfway) {
                    val midLine = mission?.hook?.coachVoiceLines?.getOrNull(1)
                        ?: "¡Mitad del tiempo! Mantén el ritmo, vas excelente."
                    updatedCoachMsg = midLine
                    audioController.playEffect(FoxSoundEffect.CROWD_CHEER)
                    audioController.speakCoachLine(midLine)
                } else if (nextRemaining == 30 && curr.totalTimerSeconds > 45) {
                    val finalSprintLine = mission?.hook?.coachVoiceLines?.getOrNull(2)
                        ?: "¡Últimos 30 segundos! Cierra este paso y prepárate para mostrar tu avance."
                    updatedCoachMsg = finalSprintLine
                    audioController.playEffect(FoxSoundEffect.FOX_APPEAR)
                    audioController.speakCoachLine(finalSprintLine)
                }

                if (nextRemaining == 0) {
                    audioController.setFocusSessionMusicActive(false)
                    audioController.playEffect(FoxSoundEffect.REFEREE_WHISTLE)
                    val endLine = "¡Tiempo cumplido! Sube una foto o graba en voz qué hiciste para validar tu avance."
                    audioController.speakCoachLine(endLine)
                    _uiState.update {
                        it.copy(
                            remainingTimerSeconds = 0,
                            elapsedSessionSeconds = nextElapsed,
                            challengePhase = ChallengePhase.VERIFYING_EVIDENCE,
                            coachLiveMessage = endLine,
                            lcdLine1 = "TIME UP!",
                            lcdLine2 = "Send proof"
                        )
                    }
                    break
                } else {
                    _uiState.update {
                        it.copy(
                            remainingTimerSeconds = nextRemaining,
                            elapsedSessionSeconds = nextElapsed,
                            coachLiveMessage = updatedCoachMsg,
                            lcdLine1 = "FOCUS MODE",
                            lcdLine2 = formatLcdRow16("${formatCountdownClock(nextRemaining)} LEFT")
                        )
                    }
                }
            }
        }
    }

    fun onBulletStepChecked(checkedCount: Int, totalCount: Int) {
        audioController.playEffect(FoxSoundEffect.CROWD_CHEER)
        val line = if (checkedCount >= totalCount) {
            "¡Increíble, completaste todas las viñetas! Ya puedes finalizar y enviar tu evidencia."
        } else {
            "¡Bien hecho! Punto $checkedCount de $totalCount superado, sigue así."
        }
        _uiState.update { it.copy(coachLiveMessage = line) }
        audioController.speakCoachLine(line)
    }

    fun triggerCoachPepTalk() {
        val line = pepTalkCatalog[pepTalkCursor % pepTalkCatalog.size]
        pepTalkCursor++
        audioController.playEffect(FoxSoundEffect.CROWD_CHEER)
        _uiState.update { it.copy(coachLiveMessage = line) }
        audioController.speakCoachLine(line)
    }

    fun toggleFocusMusicInline() {
        val curr = _uiState.value.settings
        val nextMusic = !curr.musicEnabled
        updatePreferences(curr.interventionLevel, curr.audioEnabled, nextMusic, curr.voiceCoachEnabled)
    }

    fun toggleVoiceCoachInline() {
        val curr = _uiState.value.settings
        val nextVoice = !curr.voiceCoachEnabled
        updatePreferences(curr.interventionLevel, curr.audioEnabled, curr.musicEnabled, nextVoice)
    }

    fun openEvidenceVerificationStage() {
        val state = _uiState.value
        if (state.isActionBusy || state.challengePhase == ChallengePhase.COMPLETED_VICTORY) return
        timerJob?.cancel()
        audioController.setFocusSessionMusicActive(false)
        audioController.playEffect(FoxSoundEffect.REFEREE_WHISTLE)
        val prompt = "¡Silbatazo de cierre! Manda una foto o cuenta con tu voz qué hiciste para que el Árbitro IA lo analice."
        audioController.speakCoachLine(prompt)
        _uiState.update {
            it.copy(
                challengePhase = ChallengePhase.VERIFYING_EVIDENCE,
                coachLiveMessage = prompt,
                lcdLine1 = "VERIFY PROOF",
                lcdLine2 = "Photo or Voice"
            )
        }
    }

    fun submitEvidenceForVerification(
        evidenceType: String,
        imageBase64: String? = null,
        voiceTranscript: String? = null,
        checkedStepsCount: Int = 0
    ) {
        val state = _uiState.value
        if (state.isVerifyingEvidence) return

        val sessId = state.activeSessionId
        val prevLevel = state.rewardProfile.level

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingEvidence = true, isActionBusy = true) }

            if (sessId != null) {
                repository.verifySessionEvidence(
                    sessionId = sessId,
                    evidenceType = evidenceType,
                    imageBase64 = imageBase64,
                    voiceTranscript = voiceTranscript,
                    checkedStepsCount = checkedStepsCount,
                    actualSeconds = state.elapsedSessionSeconds.coerceAtLeast(15)
                ).onSuccess { res ->
                    val earned = res.sessionResult.rewardsGranted.sumOf { it.xpAwarded }
                    val newLevel = res.sessionResult.currentLevel
                    refreshRewardsAndHistory()
                    if (newLevel > prevLevel) {
                        audioController.playEffect(FoxSoundEffect.LEVEL_UP)
                    } else {
                        audioController.playEffect(FoxSoundEffect.MISSION_COMPLETE)
                    }
                    audioController.speakCoachLine(res.verdict.refereeSpeech)
                    _uiState.update {
                        it.copy(
                            isVerifyingEvidence = false,
                            isActionBusy = false,
                            challengePhase = ChallengePhase.COMPLETED_VICTORY,
                            latestRefereeVerdict = res.verdict,
                            coachLiveMessage = res.verdict.refereeSpeech,
                            lastEarnedXp = it.lastEarnedXp + earned,
                            lcdLine1 = "VERIFIED!",
                            lcdLine2 = formatLcdRow16("+$earned XP"),
                            statusBanner = if (res.bonusXpAwarded > 0) {
                                "¡Evidencia verificada por IA! +$earned XP (incluye bono +${res.bonusXpAwarded} XP)"
                            } else {
                                "Sesión registrada: +$earned XP"
                            }
                        )
                    }
                    return@launch
                }
            }

            // Offline fallback if session wasn't synced remotely
            val fallbackVerdict = RefereeVerdictDto(
                verified = true,
                evidenceType = evidenceType,
                confidenceScore = 80,
                refereeSpeech = "¡Silbatazo final! Excelente evidencia de arranque, rompiste la procrastinación.",
                detectedProgress = if (evidenceType == "voice") {
                    voiceTranscript?.take(120) ?: "Reporte de voz completado."
                } else {
                    "Captura visual registrada."
                },
                nextTip = "Descansa o aprovecha la racha con 3 minutos más.",
                generationSource = "deterministic_fallback",
                modelName = "local_referee"
            )
            audioController.playEffect(FoxSoundEffect.MISSION_COMPLETE)
            audioController.speakCoachLine(fallbackVerdict.refereeSpeech)
            _uiState.update {
                it.copy(
                    isVerifyingEvidence = false,
                    isActionBusy = false,
                    challengePhase = ChallengePhase.COMPLETED_VICTORY,
                    latestRefereeVerdict = fallbackVerdict,
                    coachLiveMessage = fallbackVerdict.refereeSpeech,
                    lastEarnedXp = it.lastEarnedXp + 35,
                    lcdLine1 = "DONE! +35 XP",
                    lcdLine2 = "Good start!"
                )
            }
        }
    }

    fun completeActiveChallenge(voluntaryContinuity: Boolean) {
        val state = _uiState.value
        if (state.isActionBusy || state.challengePhase == ChallengePhase.COMPLETED_VICTORY) return

        timerJob?.cancel()
        audioController.stopSpeech()
        audioController.setFocusSessionMusicActive(false)
        val sessId = state.activeSessionId
        val prevLevel = state.rewardProfile.level

        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            var earned = if (voluntaryContinuity) 30 else 20
            var newLevel = prevLevel
            if (sessId != null) {
                repository.completeSession(
                    sessionId = sessId,
                    voluntaryContinuity = voluntaryContinuity,
                    actualSeconds = state.elapsedSessionSeconds
                ).onSuccess { res ->
                    earned = res.rewardsGranted.sumOf { it.xpAwarded }
                    newLevel = res.currentLevel
                    refreshRewardsAndHistory()
                }
            }
            if (newLevel > prevLevel) {
                audioController.playEffect(FoxSoundEffect.LEVEL_UP)
            } else {
                audioController.playEffect(FoxSoundEffect.MISSION_COMPLETE)
            }
            _uiState.update {
                it.copy(
                    isActionBusy = false,
                    challengePhase = ChallengePhase.COMPLETED_VICTORY,
                    lastEarnedXp = it.lastEarnedXp + earned,
                    lcdLine1 = "MISSION DONE!",
                    lcdLine2 = formatLcdRow16("+$earned XP"),
                    statusBanner = "¡Reto completado! +$earned XP"
                )
            }
        }
    }

    fun postponeOrStopChallenge(reason: String = "postponed", onDone: () -> Unit = {}) {
        if (_uiState.value.isActionBusy) return
        timerJob?.cancel()
        audioController.stopSpeech()
        audioController.setFocusSessionMusicActive(false)
        if (reason == "silenced") {
            audioController.playEffect(FoxSoundEffect.FOX_SLEEP, allowDuringSleepTransition = true)
        } else {
            audioController.playEffect(FoxSoundEffect.MISSION_REJECT)
        }

        val state = _uiState.value
        val sessId = state.activeSessionId
        val hookId = state.activeMission?.hookId

        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true, showImmersiveInterruptModal = false) }
            if (sessId != null) {
                repository.stopSession(sessId, reason = reason)
            } else if (hookId != null) {
                repository.dismissHook(hookId, reason = reason)
            }
            refreshRewardsAndHistory()
            _uiState.update {
                it.copy(
                    isActionBusy = false,
                    challengePhase = ChallengePhase.IDLE_READY,
                    activeSessionId = null,
                    lcdLine1 = if (reason == "silenced") "FOX SLEEPING" else "FOXUS SNOOZED",
                    lcdLine2 = "Waiting mission",
                    statusBanner = if (reason == "silenced") {
                        "FOX en descanso · Preferencia actualizada"
                    } else {
                        "Pospuesto sin culpa · FOX ajustará el próximo paso"
                    }
                )
            }
            onDone()
        }
    }

    fun setBleExperimentalMode(enabled: Boolean) {
        val nextTransport = if (enabled) bleTransport else wifiTransport
        _uiState.update {
            it.copy(
                useBleExperimental = enabled,
                activeTransportName = nextTransport.transportName
            )
        }
    }

    fun pollNextDeviceCommand() {
        val device = _uiState.value.devices.firstOrNull()
        if (device == null) {
            refreshDevices()
            return
        }
        viewModelScope.launch {
            currentTransport.pollNextCommand(device.id).onSuccess { next ->
                val cmd = next.command
                if (next.hasCommand && cmd != null) {
                    val l1 = cmd.payload["lcd_line_1"]?.jsonPrimitive?.content ?: "NEW MISSION!"
                    val l2 = cmd.payload["lcd_line_2"]?.jsonPrimitive?.content ?: "Ready"
                    _uiState.update {
                        it.copy(
                            pendingDeviceCommandId = cmd.id,
                            lcdLine1 = formatLcdRow16(l1),
                            lcdLine2 = formatLcdRow16(l2),
                            statusBanner = "FoxusWatch: $l2"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(statusBanner = "FoxusWatch sincronizado y al día")
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusBanner = err.message ?: "No se pudo consultar FoxusWatch")
                }
            }
        }
    }

    fun simulateDeviceButton(eventType: String) {
        val state = _uiState.value
        val device = state.devices.firstOrNull()
        if (device == null) {
            _uiState.update { it.copy(statusBanner = "Conectando FoxusWatch...") }
            refreshDevices()
            return
        }
        viewModelScope.launch {
            val res = currentTransport.sendButtonAction(
                deviceId = device.id,
                eventType = eventType,
                commandId = state.pendingDeviceCommandId,
                microtaskId = state.activeMission?.microtaskId,
                hookId = state.activeMission?.hookId,
                sessionId = state.activeSessionId,
                source = "simulator"
            )
            res.onSuccess { ev ->
                when (eventType) {
                    "BUTTON_ACCEPT" -> {
                        audioController.playEffect(FoxSoundEffect.REFEREE_WHISTLE)
                        audioController.setFocusSessionMusicActive(true)
                        _uiState.update {
                            it.copy(
                                activeSessionId = ev.sessionId ?: it.activeSessionId,
                                challengePhase = ChallengePhase.RUNNING,
                                lastEarnedXp = ev.xpAwarded,
                                lcdLine1 = ev.lcdLine1,
                                lcdLine2 = ev.lcdLine2,
                                statusBanner = "FoxusWatch · Reto iniciado (${ev.lcdLine2})"
                            )
                        }
                        launchCountdownLoop()
                    }
                    "BUTTON_COMPLETE" -> {
                        timerJob?.cancel()
                        audioController.stopSpeech()
                        audioController.setFocusSessionMusicActive(false)
                        audioController.playEffect(FoxSoundEffect.MISSION_COMPLETE)
                        _uiState.update {
                            it.copy(
                                challengePhase = ChallengePhase.COMPLETED_VICTORY,
                                lastEarnedXp = it.lastEarnedXp + ev.xpAwarded,
                                lcdLine1 = ev.lcdLine1,
                                lcdLine2 = ev.lcdLine2,
                                statusBanner = "FoxusWatch · ${ev.lcdLine2}"
                            )
                        }
                    }
                    else -> {
                        timerJob?.cancel()
                        audioController.stopSpeech()
                        audioController.setFocusSessionMusicActive(false)
                        audioController.playEffect(FoxSoundEffect.MISSION_REJECT)
                        _uiState.update {
                            it.copy(
                                challengePhase = ChallengePhase.IDLE_READY,
                                activeSessionId = null,
                                lcdLine1 = ev.lcdLine1,
                                lcdLine2 = ev.lcdLine2,
                                statusBanner = "FoxusWatch · Reto pospuesto"
                            )
                        }
                    }
                }
                refreshRewardsAndHistory()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        statusBanner = err.message
                            ?: "Selecciona primero una tarea para enviarla a tu FoxusWatch"
                    )
                }
            }
        }
    }

    fun saveConnectionSettings(baseUrl: String, demoToken: String, deviceToken: String) {
        viewModelScope.launch {
            settingsStore.updateConnectionConfig(baseUrl, demoToken, deviceToken)
            _uiState.update { it.copy(statusBanner = "Conexión guardada.") }
            refreshAllSilent()
        }
    }

    fun updatePreferences(
        level: Int,
        audio: Boolean,
        music: Boolean,
        voiceCoach: Boolean = _uiState.value.settings.voiceCoachEnabled
    ) {
        viewModelScope.launch {
            settingsStore.updateLocalPreferences(level, audio, music, voiceCoach)
            audioController.updateAudioConfiguration(audio, music, level, voiceCoach)
            repository.syncPreferencesToServer(level, audio, music)
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    override fun onCleared() {
        timerJob?.cancel()
        audioController.stopSpeech()
        audioController.setFocusSessionMusicActive(false)
        super.onCleared()
    }

    companion object {
        fun factory(
            repository: FoxusRepository,
            settingsStore: SettingsDataStore,
            endpointConfig: DynamicEndpointConfig,
            audioController: FoxAudioController,
            interventionManager: FoxInterventionManager,
            wifiTransport: DeviceTransport,
            bleTransport: DeviceTransport
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FoxusMainViewModel(
                    repository = repository,
                    settingsStore = settingsStore,
                    endpointConfig = endpointConfig,
                    audioController = audioController,
                    interventionManager = interventionManager,
                    wifiTransport = wifiTransport,
                    bleTransport = bleTransport
                ) as T
            }
        }
    }
}
