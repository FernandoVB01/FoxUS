package com.foxus.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MomentumProfileDto(
    @SerialName("display_name") val displayName: String = "Mi perfil",
    @SerialName("total_xp") val totalXp: Int = 0,
    val category: String = "Por encender",
    val heat: Int = 0,
    val dashboard: DashboardDto = DashboardDto(),
    val chains: List<MomentumChainDto> = emptyList(),
    val posts: List<MomentDto> = emptyList(),
    @SerialName("eligible_sessions") val eligibleSessions: List<MomentCandidateDto> = emptyList()
)

@Serializable
data class MomentumChainDto(
    val id: String, @SerialName("task_id") val taskId: String,
    val title: String, val links: Int, val heat: Int, val status: String,
    val suggestion: String = "¿Puedes dar otro paso?",
    @SerialName("suggested_target") val suggestedTarget: String = "Avanzar un paso más",
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class MomentDto(
    val id: String, val title: String, val caption: String? = null,
    @SerialName("evidence_type") val evidenceType: String,
    @SerialName("link_index") val linkIndex: Int,
    @SerialName("image_base64") val imageBase64: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null
)

@Serializable
data class ContinueMomentumDto(val target: String, val minutes: Int, val technique: String? = null, @SerialName("timer_mode") val timerMode: String = "countdown")

@Serializable
data class CreateMomentDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("evidence_type") val evidenceType: String,
    val caption: String,
    @SerialName("units_completed") val unitsCompleted: Int? = null,
    val unit: String = "pasos",
    @SerialName("image_base64") val imageBase64: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_base64") val mediaBase64: String? = null
)

@Serializable
data class MomentCandidateDto(val id: String, val title: String, @SerialName("link_index") val linkIndex: Int)

@Serializable
data class DashboardDto(
    @SerialName("period_days") val periodDays: Int = 30,
    @SerialName("focus_minutes") val focusMinutes: Int = 0,
    val advances: Int = 0, val activities: Int = 0,
    @SerialName("completion_rate") val completionRate: Int? = null,
    val verified: Int = 0, val speeds: List<SpeedDto> = emptyList()
)
@Serializable
data class SpeedDto(val unit: String, @SerialName("per_hour") val perHour: Double)
