from __future__ import annotations

import time
from typing import Literal

from app.agents.contracts import HOOK_AGENT_SYSTEM_PROMPT, AgentExecutionStep, GemmaHookDraft
from app.models.entities import UserPreference
from app.schemas.agents import HookProposal, LearningSnapshot, MicrotaskProposal, TaskContext
from app.services.gemma_client import GemmaClient

HOOK_META: dict[str, dict[str, str]] = {
    "challenge": {
        "visual_mode_name": "Desafío Relámpago",
        "sound_id": "challenge_start",
        "animation_id": "fox_challenge",
    },
    "fox_bzz": {
        "visual_mode_name": "La Mosca Traviesa",
        "sound_id": "fly_buzz",
        "animation_id": "fox_playful",
    },
}


class HookAgent:
    """
    Hook Agent (Section 4.4).
    Combines user preferences + Learning Agent adaptive selection + Gemma 4 contextual copy
    to present either FOX Challenge ('Desafío Relámpago') or FOX BZZ ('La Mosca Traviesa').
    Never triggers notifications/sounds directly; outputs HookProposal for Policy Engine.
    """

    def __init__(self, gemma_client: GemmaClient) -> None:
        self._gemma = gemma_client

    @staticmethod
    def _select_hook_type(
        requested_mode: Literal["auto", "challenge", "fox_bzz"],
        preferences: UserPreference,
        learning: LearningSnapshot,
    ) -> Literal["challenge", "fox_bzz"]:
        if requested_mode in ("challenge", "fox_bzz"):
            return requested_mode
        if preferences.preferred_hook in ("challenge", "fox_bzz"):
            return preferences.preferred_hook  # type: ignore[return-value]
        return learning.recommended_hook_type

    @staticmethod
    def _fallback_message(
        hook_type: Literal["challenge", "fox_bzz"],
        microtask: MicrotaskProposal,
    ) -> str:
        minutes = max(1, round(microtask.estimated_seconds / 60))
        if hook_type == "fox_bzz":
            return f"¡Bzz! ¿Y si hacemos solo '{microtask.title}' en {minutes} min y listo?"
        return f"¡FOX tiene un reto relámpago de {minutes} min! Empecemos con: {microtask.title}."

    def compose(
        self,
        *,
        context: TaskContext,
        microtask: MicrotaskProposal,
        preferences: UserPreference,
        learning: LearningSnapshot,
        requested_mode: Literal["auto", "challenge", "fox_bzz"] = "auto",
    ) -> AgentExecutionStep[HookProposal]:
        start_ns = time.perf_counter_ns()
        selected_type = self._select_hook_type(requested_mode, preferences, learning)
        meta = HOOK_META[selected_type]
        default_intensity = max(1, min(3, preferences.intervention_level))

        gemma_res = self._gemma.generate_structured(
            system_prompt=HOOK_AGENT_SYSTEM_PROMPT,
            user_payload={
                "selected_hook_type": selected_type,
                "visual_mode_name": meta["visual_mode_name"],
                "task_title": context.task_title,
                "microtask_title": microtask.title,
                "microtask_description": microtask.description,
                "estimated_seconds": microtask.estimated_seconds,
                "user_max_intensity": preferences.intervention_level,
            },
            schema_model=GemmaHookDraft,
            temperature=0.5,
        )

        default_voice_lines = [
            f"¡Silbato inicial! Arrancamos con {microtask.title}. Solo enfócate en el primer punto.",
            "¡Buen ritmo! No busques perfección, cada pequeño avance rompe la inercia.",
            "¡Últimos 30 segundos! Cierra tu microacción y muéstrame tu evidencia.",
        ]
        minigame_choice: Literal["catch_fox", "spark_combo"] = (
            "spark_combo" if selected_type == "fox_bzz" else "catch_fox"
        )

        if gemma_res.ok and gemma_res.parsed is not None:
            draft = gemma_res.parsed
            msg = draft.message.strip()[:195]
            if selected_type == "fox_bzz" and "bzz" not in msg.lower():
                msg = f"¡Bzz! {msg}"[:195]
            voice_lines = [
                line.strip()[:120] for line in draft.coach_voice_lines if line and line.strip()
            ][:3]
            if len(voice_lines) < 3:
                voice_lines = (voice_lines + default_voice_lines)[:3]
            intensity = min(max(1, draft.intensity), max(1, preferences.intervention_level))
            bounded_delay = min(120, max(0, draft.suggested_delay_seconds))
            proposal = HookProposal(
                hook_type=selected_type,
                visual_mode_name=meta["visual_mode_name"],
                message=msg,
                coach_voice_lines=voice_lines,
                ignition_minigame=minigame_choice,
                intensity=intensity,
                channel="both",
                suggested_delay_seconds=bounded_delay,
                sound_id=meta["sound_id"],
                animation_id=meta["animation_id"],
                generation_source="gemma",
                model_name=gemma_res.model_used,
            )
            step_status = "ok"
            model_used = gemma_res.model_used
        else:
            proposal = HookProposal(
                hook_type=selected_type,
                visual_mode_name=meta["visual_mode_name"],
                message=self._fallback_message(selected_type, microtask),
                coach_voice_lines=default_voice_lines,
                ignition_minigame=minigame_choice,
                intensity=default_intensity,
                channel="both",
                suggested_delay_seconds=0,
                sound_id=meta["sound_id"],
                animation_id=meta["animation_id"],
                generation_source="deterministic_fallback",
                model_name="deterministic_fallback",
            )
            step_status = "fallback"
            model_used = "deterministic_fallback"

        elapsed_ms = max(1, int((time.perf_counter_ns() - start_ns) // 1_000_000))
        return AgentExecutionStep(
            agent_name="hook_agent",
            output=proposal,
            model_name=model_used,
            duration_ms=elapsed_ms,
            status=step_status,  # type: ignore[arg-type]
            input_summary={
                "requested_mode": requested_mode,
                "selected_hook_type": selected_type,
                "microtask_title": microtask.title,
            },
            output_summary={
                "hook_type": proposal.hook_type,
                "visual_mode_name": proposal.visual_mode_name,
                "intensity": proposal.intensity,
                "sound_id": proposal.sound_id,
                "animation_id": proposal.animation_id,
                "generation_source": proposal.generation_source,
                "fallback_reason": gemma_res.error_reason,
            },
        )
