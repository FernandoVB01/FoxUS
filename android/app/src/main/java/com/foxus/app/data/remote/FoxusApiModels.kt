package com.foxus.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthResponseDto(
    val status: String,
    val service: String = "FOXUS API",
    val version: String = "0.2.0",
    val ai: AiHealthDto? = null
)

@Serializable
data class AiHealthDto(
    val provider: String = "",
    @SerialName("configured_model") val configuredModel: String = "gemma-4-26b-a4b-it",
    @SerialName("api_key_configured") val apiKeyConfigured: Boolean = false,
    val mode: String = "deterministic_fallback"
)

@Serializable
data class MicrotaskDto(
    val id: String,
    @SerialName("task_id") val taskId: String,
    val title: String,
    val description: String,
    @SerialName("estimated_seconds") val estimatedSeconds: Int,
    @SerialName("success_criteria") val successCriteria: String,
    @SerialName("model_name") val modelName: String,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class TaskCreateRequestDto(
    val title: String,
    val description: String? = null,
    val deadline: String? = null,
    @SerialName("needs_pressure") val needsPressure: Boolean = false,
    val source: String = "manual"
)

@Serializable
data class TaskUpdateRequestDto(
    @SerialName("needs_pressure") val needsPressure: Boolean? = null,
    val deadline: String? = null,
    val status: String? = null
)

@Serializable
data class TaskDto(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    val title: String,
    val description: String? = null,
    val source: String = "manual",
    val deadline: String? = null,
    @SerialName("needs_pressure") val needsPressure: Boolean = false,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String = "",
    val microtasks: List<MicrotaskDto> = emptyList()
)

@Serializable
data class GenerateMissionRequestDto(
    @SerialName("task_id") val taskId: String,
    @SerialName("available_minutes") val availableMinutes: Int = 5,
    @SerialName("perceived_difficulty") val perceivedDifficulty: Int? = null,
    @SerialName("requested_mode") val requestedMode: String = "challenge",
    @SerialName("dispatch_to_device") val dispatchToDevice: Boolean = true,
    @SerialName("ignore_cooldown_for_demo") val ignoreCooldownForDemo: Boolean = true
)

@Serializable
data class PolicyDecisionDto(
    val allowed: Boolean,
    @SerialName("effective_intensity") val effectiveIntensity: Int = 2,
    @SerialName("effective_channel") val effectiveChannel: String = "both",
    @SerialName("audio_allowed") val audioAllowed: Boolean = true,
    val reasons: List<String> = emptyList(),
    @SerialName("cooldown_remaining_seconds") val cooldownRemainingSeconds: Int = 0
)

@Serializable
data class CalendarEventDto(
    val id: String = "",
    val title: String = "",
    @SerialName("start_at") val startAt: String = "",
    @SerialName("end_at") val endAt: String = "",
    val source: String = "google_calendar"
)

@Serializable
data class TaskContextDto(
    @SerialName("task_id") val taskId: String,
    @SerialName("task_title") val taskTitle: String,
    @SerialName("needs_pressure") val needsPressure: Boolean = false,
    @SerialName("urgency_level") val urgencyLevel: String = "medium",
    @SerialName("upcoming_events") val upcomingEvents: List<CalendarEventDto> = emptyList(),
    @SerialName("contextual_summary") val contextualSummary: String = "",
    @SerialName("generation_source") val generationSource: String = "deterministic_rules",
    @SerialName("model_name") val modelName: String? = null
)

@Serializable
data class MicrotaskProposalDto(
    @SerialName("task_id") val taskId: String,
    val title: String,
    @SerialName("lcd_short_title") val lcdShortTitle: String,
    val description: String,
    val steps: List<String> = emptyList(),
    @SerialName("estimated_seconds") val estimatedSeconds: Int = 180,
    @SerialName("success_criteria") val successCriteria: String,
    val rationale: String = "",
    @SerialName("difficulty_adapted") val difficultyAdapted: Boolean = false,
    @SerialName("generation_source") val generationSource: String = "deterministic_fallback",
    @SerialName("model_name") val modelName: String = "deterministic_fallback"
)

@Serializable
data class HookProposalDto(
    @SerialName("hook_type") val hookType: String = "challenge",
    @SerialName("visual_mode_name") val visualModeName: String = "Desafío Relámpago",
    val message: String,
    @SerialName("coach_voice_lines") val coachVoiceLines: List<String> = emptyList(),
    @SerialName("ignition_minigame") val ignitionMinigame: String = "catch_fox",
    val intensity: Int = 2,
    val channel: String = "both",
    @SerialName("sound_id") val soundId: String = "challenge_start",
    @SerialName("animation_id") val animationId: String = "fox_challenge",
    @SerialName("generation_source") val generationSource: String = "deterministic_fallback",
    @SerialName("model_name") val modelName: String = "deterministic_fallback"
)

@Serializable
data class LearningSnapshotDto(
    @SerialName("recommended_hook_type") val recommendedHookType: String = "challenge",
    @SerialName("acceptance_rate") val acceptanceRate: Double = 0.0,
    @SerialName("start_rate") val startRate: Double = 0.0,
    @SerialName("completion_rate") val completionRate: Double = 0.0,
    val strategy: String = "adaptive_ucb_v1"
)

@Serializable
data class GenerateMissionResponseDto(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("trace_id") val traceId: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("microtask_id") val microtaskId: String? = null,
    @SerialName("hook_id") val hookId: String? = null,
    @SerialName("is_simulated_ai") val isSimulatedAi: Boolean = false,
    val policy: PolicyDecisionDto,
    val context: TaskContextDto,
    val microtask: MicrotaskProposalDto,
    val hook: HookProposalDto,
    val learning: LearningSnapshotDto,
    @SerialName("dispatched_device_command_id") val dispatchedDeviceCommandId: String? = null
)

@Serializable
data class PressureEvaluationRequestDto(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("demo_force") val demoForce: Boolean = false,
    @SerialName("dispatch_to_device") val dispatchToDevice: Boolean = true
)

@Serializable
data class PressureEvaluationResponseDto(
    @SerialName("should_interrupt") val shouldInterrupt: Boolean = false,
    @SerialName("urgency_score") val urgencyScore: Int = 0,
    @SerialName("reason_short") val reasonShort: String = "",
    @SerialName("recommended_hook") val recommendedHook: String = "challenge",
    val mission: GenerateMissionResponseDto? = null
)

@Serializable
data class GoogleStatusResponseDto(
    @SerialName("oauth_configured_on_server") val oauthConfiguredOnServer: Boolean = false,
    val connected: Boolean = false,
    @SerialName("token_expired") val tokenExpired: Boolean = false,
    @SerialName("status_message") val statusMessage: String? = null,
    @SerialName("auth_mode") val authMode: String = "none",
    @SerialName("connected_email") val connectedEmail: String? = null,
    @SerialName("has_calendar_ical") val hasCalendarIcal: Boolean = false,
    @SerialName("synced_google_tasks_count") val syncedGoogleTasksCount: Int = 0,
    @SerialName("upcoming_events_count") val upcomingEventsCount: Int = 0,
    @SerialName("upcoming_events") val upcomingEvents: List<CalendarEventDto> = emptyList(),
    @SerialName("last_synced_at") val lastSyncedAt: String? = null
)

@Serializable
data class GoogleAuthUrlResponseDto(
    @SerialName("oauth_configured") val oauthConfigured: Boolean = false,
    @SerialName("auth_url") val authUrl: String? = null,
    @SerialName("setup_hint") val setupHint: String = ""
)

@Serializable
data class GoogleConnectTokenRequestDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("calendar_ical_url") val calendarIcalUrl: String? = null
)

@Serializable
data class GoogleConnectIcalRequestDto(
    @SerialName("calendar_ical_url") val calendarIcalUrl: String
)

@Serializable
data class GoogleSyncResponseDto(
    val ok: Boolean = false,
    @SerialName("imported_tasks") val importedTasks: Int = 0,
    @SerialName("updated_tasks") val updatedTasks: Int = 0,
    @SerialName("synced_events") val syncedEvents: Int = 0,
    @SerialName("upcoming_events") val upcomingEvents: List<CalendarEventDto> = emptyList(),
    val detail: String = ""
)

@Serializable
data class SessionStartRequestDto(
    @SerialName("microtask_id") val microtaskId: String,
    @SerialName("hook_id") val hookId: String? = null,
    val source: String = "android"
)

@Serializable
data class SessionCompleteRequestDto(
    val source: String = "android",
    @SerialName("voluntary_continuity") val voluntaryContinuity: Boolean = false,
    @SerialName("actual_duration_seconds") val actualDurationSeconds: Int? = null
)

@Serializable
data class SessionStopRequestDto(
    val source: String = "android",
    val reason: String = "postponed"
)

@Serializable
data class HookDismissRequestDto(
    val source: String = "android",
    val reason: String = "postponed"
)

@Serializable
data class HookDismissResponseDto(
    @SerialName("hook_id") val hookId: String,
    @SerialName("hook_type") val hookType: String,
    val status: String,
    @SerialName("recorded_event_type") val recordedEventType: String = ""
)

@Serializable
data class SessionDto(
    val id: String,
    @SerialName("microtask_id") val microtaskId: String,
    @SerialName("hook_id") val hookId: String? = null,
    val status: String,
    @SerialName("completion_source") val completionSource: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class RewardGrantDto(
    @SerialName("reward_type") val rewardType: String,
    @SerialName("xp_awarded") val xpAwarded: Int,
    @SerialName("already_granted") val alreadyGranted: Boolean
)

@Serializable
data class SessionActionResponseDto(
    val session: SessionDto,
    @SerialName("rewards_granted") val rewardsGranted: List<RewardGrantDto> = emptyList(),
    @SerialName("total_xp") val totalXp: Int = 0,
    @SerialName("current_level") val currentLevel: Int = 1,
    val celebrate: Boolean = false
)

@Serializable
data class RefereeVerdictDto(
    val verified: Boolean = true,
    @SerialName("evidence_type") val evidenceType: String = "voice",
    @SerialName("confidence_score") val confidenceScore: Int = 85,
    @SerialName("referee_speech") val refereeSpeech: String = "",
    @SerialName("detected_progress") val detectedProgress: String = "",
    @SerialName("next_tip") val nextTip: String = "",
    @SerialName("generation_source") val generationSource: String = "gemma",
    @SerialName("model_name") val modelName: String = "gemma-4-26b-a4b-it"
)

@Serializable
data class SessionEvidenceVerifyRequestDto(
    @SerialName("evidence_type") val evidenceType: String,
    @SerialName("image_base64") val imageBase64: String? = null,
    @SerialName("image_mime_type") val imageMimeType: String = "image/jpeg",
    @SerialName("voice_transcript") val voiceTranscript: String? = null,
    @SerialName("checked_steps_count") val checkedStepsCount: Int = 0,
    @SerialName("actual_duration_seconds") val actualDurationSeconds: Int? = null,
    val source: String = "android"
)

@Serializable
data class SessionEvidenceVerifyResponseDto(
    @SerialName("session_result") val sessionResult: SessionActionResponseDto,
    val verdict: RefereeVerdictDto,
    @SerialName("bonus_xp_awarded") val bonusXpAwarded: Int = 0,
    @SerialName("trace_id") val traceId: String = ""
)

@Serializable
data class HookStatDto(
    @SerialName("hook_type") val hookType: String,
    @SerialName("shown_count") val shownCount: Int = 0,
    @SerialName("accepted_count") val acceptedCount: Int = 0,
    @SerialName("started_count") val startedCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0,
    @SerialName("rejected_count") val rejectedCount: Int = 0
)

@Serializable
data class RewardTransactionDto(
    val id: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("reward_type") val rewardType: String,
    @SerialName("xp_amount") val xpAmount: Int,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class RewardProfileDto(
    @SerialName("display_name") val displayName: String = "Estudiante FOXUS",
    @SerialName("total_xp") val totalXp: Int = 0,
    val level: Int = 1,
    @SerialName("xp_in_current_level") val xpInCurrentLevel: Int = 0,
    @SerialName("xp_for_next_level") val xpForNextLevel: Int = 100,
    @SerialName("progress_ratio") val progressRatio: Float = 0f,
    @SerialName("completed_microtasks_count") val completedMicrotasksCount: Int = 0,
    @SerialName("started_sessions_count") val startedSessionsCount: Int = 0,
    val achievements: List<String> = emptyList(),
    @SerialName("hook_stats") val hookStats: List<HookStatDto> = emptyList()
)

@Serializable
data class AgentTraceStepDto(
    val id: String,
    @SerialName("trace_id") val traceId: String,
    @SerialName("agent_name") val agentName: String,
    @SerialName("model_name") val modelName: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    val status: String,
    @SerialName("output_summary") val outputSummary: Map<String, JsonElement>? = null
)

@Serializable
data class AgentTraceTimelineDto(
    @SerialName("trace_id") val traceId: String,
    @SerialName("total_duration_ms") val totalDurationMs: Int = 0,
    @SerialName("used_real_gemma") val usedRealGemma: Boolean = false,
    @SerialName("models_invoked") val modelsInvoked: List<String> = emptyList(),
    val steps: List<AgentTraceStepDto> = emptyList()
)

@Serializable
data class UserPreferenceDto(
    @SerialName("intervention_level") val interventionLevel: Int = 2,
    @SerialName("preferred_hook") val preferredHook: String = "auto",
    @SerialName("audio_enabled") val audioEnabled: Boolean = true,
    @SerialName("music_enabled") val musicEnabled: Boolean = false,
    @SerialName("quiet_hours_start") val quietHoursStart: String? = "23:30",
    @SerialName("quiet_hours_end") val quietHoursEnd: String? = "06:30"
)

@Serializable
data class UserPreferenceUpdateDto(
    @SerialName("intervention_level") val interventionLevel: Int? = null,
    @SerialName("preferred_hook") val preferredHook: String? = null,
    @SerialName("audio_enabled") val audioEnabled: Boolean? = null,
    @SerialName("music_enabled") val musicEnabled: Boolean? = null
)

@Serializable
data class DeviceRegisterRequestDto(
    @SerialName("device_name") val deviceName: String = "FoxusWatch",
    @SerialName("device_type") val deviceType: String = "android_simulator"
)

@Serializable
data class DeviceDto(
    val id: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val status: String
)

@Serializable
data class DeviceCommandDto(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("command_type") val commandType: String,
    val payload: Map<String, JsonElement> = emptyMap(),
    val status: String = "pending"
)

@Serializable
data class NextDeviceCommandResponseDto(
    @SerialName("has_command") val hasCommand: Boolean = false,
    val command: DeviceCommandDto? = null,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 3
)

@Serializable
data class DeviceEventRequestDto(
    @SerialName("event_type") val eventType: String,
    @SerialName("command_id") val commandId: String? = null,
    @SerialName("microtask_id") val microtaskId: String? = null,
    @SerialName("hook_id") val hookId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val source: String = "simulator"
)

@Serializable
data class DeviceEventResponseDto(
    val accepted: Boolean,
    @SerialName("event_type") val eventType: String,
    @SerialName("lcd_line_1") val lcdLine1: String,
    @SerialName("lcd_line_2") val lcdLine2: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("xp_awarded") val xpAwarded: Int = 0,
    @SerialName("total_xp") val totalXp: Int = 0
)
