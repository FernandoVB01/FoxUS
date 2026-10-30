package com.foxus.app

import com.foxus.app.audio.FoxSoundEffect
import com.foxus.app.data.remote.FoxusNetworkFactory
import com.foxus.app.data.remote.GenerateMissionResponseDto
import com.foxus.app.data.remote.HookDismissResponseDto
import com.foxus.app.data.remote.RewardProfileDto
import com.foxus.app.data.remote.UserPreferenceUpdateDto
import com.foxus.app.ui.components.FoxMascotState
import com.foxus.app.viewmodel.formatCountdownClock
import com.foxus.app.viewmodel.formatLcdRow16
import com.foxus.app.viewmodel.resolveBulletSteps
import com.foxus.app.viewmodel.resolveHomeMascotState
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoxusModelAndTimerTest {

    @Test
    fun countdownClockFormatsMinutesAndSecondsCleanly() {
        assertEquals("03:00", formatCountdownClock(180))
        assertEquals("02:59", formatCountdownClock(179))
        assertEquals("00:00", formatCountdownClock(-5))
    }

    @Test
    fun mascotStateReflectsSilenceAndPlayfulHooks() {
        assertEquals(
            FoxMascotState.SLEEP,
            resolveHomeMascotState(interventionLevel = 0, hasActiveMission = true, hookType = "challenge")
        )
        assertEquals(
            FoxMascotState.PLAYFUL,
            resolveHomeMascotState(interventionLevel = 2, hasActiveMission = true, hookType = "fox_bzz")
        )
        assertEquals(
            FoxMascotState.CURIOUS,
            resolveHomeMascotState(interventionLevel = 2, hasActiveMission = true, hookType = "challenge")
        )
        assertEquals(
            FoxMascotState.IDLE,
            resolveHomeMascotState(interventionLevel = 2, hasActiveMission = false, hookType = null)
        )
    }

    @Test
    fun missionJsonContractDeserializesRealGemmaPayload() {
        val sampleJson = """
            {
              "schema_version": 1,
              "trace_id": "trc_demo001",
              "task_id": "22222222-3333-4444-5555-666666666666",
              "microtask_id": "33333333-4444-5555-6666-777777777777",
              "hook_id": "44444444-5555-6666-7777-888888888888",
              "is_simulated_ai": false,
              "policy": {
                "allowed": true,
                "effective_intensity": 2,
                "effective_channel": "both",
                "audio_allowed": true,
                "reasons": ["POLICY_APPROVED"],
                "cooldown_remaining_seconds": 0
              },
              "context": {
                "task_id": "22222222-3333-4444-5555-666666666666",
                "task_title": "Preparar 12 diapositivas",
                "urgency_level": "high",
                "contextual_summary": "Entrega cercana.",
                "generation_source": "gemma",
                "model_name": "gemma-4-26b-a4b-it"
              },
              "microtask": {
                "task_id": "22222222-3333-4444-5555-666666666666",
                "title": "Crear portada",
                "lcd_short_title": "Crear portada",
                "description": "Abre la presentación y diseña la portada.",
                "estimated_seconds": 180,
                "success_criteria": "Portada creada",
                "rationale": "Romper inercia inicial.",
                "difficulty_adapted": false,
                "generation_source": "gemma",
                "model_name": "gemma-4-26b-a4b-it"
              },
              "hook": {
                "hook_type": "challenge",
                "visual_mode_name": "Desafío Relámpago",
                "message": "¡FOX tiene un reto de 3 minutos!",
                "intensity": 2,
                "channel": "both",
                "sound_id": "challenge_start",
                "animation_id": "fox_challenge",
                "generation_source": "gemma",
                "model_name": "gemma-4-26b-a4b-it"
              },
              "learning": {
                "recommended_hook_type": "challenge",
                "acceptance_rate": 0.75,
                "start_rate": 0.75,
                "completion_rate": 1.0,
                "strategy": "adaptive_ucb_v1"
              }
            }
        """.trimIndent()

        val parsed = FoxusNetworkFactory.json.decodeFromString<GenerateMissionResponseDto>(sampleJson)
        assertFalse(parsed.isSimulatedAi)
        assertEquals("gemma-4-26b-a4b-it", parsed.microtask.modelName)
        assertEquals(180, parsed.microtask.estimatedSeconds)
        assertEquals("challenge", parsed.hook.hookType)
    }

    @Test
    fun audioEffectMappingAndLcd16CharClampAreDeterministic() {
        assertEquals(FoxSoundEffect.FLY_BUZZ, FoxSoundEffect.fromBackendSoundId("fly_buzz"))
        assertEquals(FoxSoundEffect.CHALLENGE_START, FoxSoundEffect.fromBackendSoundId("challenge_start"))
        assertEquals(FoxSoundEffect.FOX_SLEEP, FoxSoundEffect.fromBackendSoundId("fox_sleep"))
        assertEquals(FoxSoundEffect.CHALLENGE_START, FoxSoundEffect.fromBackendSoundId("unknown_id"))
        assertEquals("Configuracion de", formatLcdRow16("Configuracion del lienzo de trabajo"))
    }

    @Test
    fun foxBzzDismissAndRewardProfileContractsDeserializeCleanly() {
        val dismissJson = """
            {
              "hook_id": "44444444-5555-6666-7777-888888888888",
              "hook_type": "fox_bzz",
              "status": "silenced",
              "recorded_event_type": "HOOK_SILENCED"
            }
        """.trimIndent()
        val dismissDto = FoxusNetworkFactory.json.decodeFromString<HookDismissResponseDto>(dismissJson)
        assertEquals("fox_bzz", dismissDto.hookType)
        assertEquals("HOOK_SILENCED", dismissDto.recordedEventType)

        val profileJson = """
            {
              "display_name": "Estudiante FOXUS",
              "total_xp": 40,
              "level": 1,
              "xp_in_current_level": 40,
              "xp_for_next_level": 100,
              "progress_ratio": 0.4,
              "completed_microtasks_count": 1,
              "started_sessions_count": 2,
              "achievements": ["Primer Impulso"],
              "hook_stats": [
                {
                  "hook_type": "fox_bzz",
                  "shown_count": 3,
                  "accepted_count": 2,
                  "started_count": 2,
                  "completed_count": 1,
                  "rejected_count": 1
                }
              ]
            }
        """.trimIndent()
        val profileDto = FoxusNetworkFactory.json.decodeFromString<RewardProfileDto>(profileJson)
        assertEquals(40, profileDto.totalXp)
        assertEquals(1, profileDto.hookStats.size)
        assertEquals("fox_bzz", profileDto.hookStats[0].hookType)

        // Verify PATCH DTO omits explicit null fields so PostgreSQL NOT NULL columns stay safe
        val patchPayload = FoxusNetworkFactory.json.encodeToString(
            UserPreferenceUpdateDto(interventionLevel = 3, audioEnabled = true, musicEnabled = false)
        )
        assertFalse(patchPayload.contains("preferred_hook"))
    }
}
