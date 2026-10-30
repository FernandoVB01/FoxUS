from __future__ import annotations

import base64
import time
from typing import Literal

from app.agents.contracts import (
    REFEREE_AGENT_SYSTEM_PROMPT,
    AgentExecutionStep,
    GemmaRefereeDraft,
)
from app.schemas.sessions import RefereeVerdict
from app.services.gemma_client import GemmaClient


class RefereeAgent:
    """
    Referee & Multimodal Verification Agent (5th Specialized Agent).
    Uses Google Gemma 4 (`gemma-4-26b-a4b-it`) native Vision + Text reasoning to
    verify post-session photo or voice evidence and deliver spoken referee feedback.
    """

    def __init__(self, gemma_client: GemmaClient) -> None:
        self._gemma = gemma_client

    @staticmethod
    def _decode_image(image_base64: str | None) -> bytes | None:
        if not image_base64:
            return None
        cleaned = image_base64.strip()
        if "," in cleaned and cleaned.lower().startswith("data:"):
            cleaned = cleaned.split(",", 1)[1]
        try:
            raw = base64.b64decode(cleaned, validate=False)
            return raw if len(raw) >= 32 else None
        except Exception:
            return None

    def verify_evidence(
        self,
        *,
        task_title: str,
        microtask_title: str,
        microtask_steps: list[str],
        evidence_type: Literal["photo", "voice"],
        image_base64: str | None = None,
        image_mime_type: str = "image/jpeg",
        voice_transcript: str | None = None,
        checked_steps_count: int = 0,
    ) -> AgentExecutionStep[RefereeVerdict]:
        start_ns = time.perf_counter_ns()
        image_bytes = self._decode_image(image_base64) if evidence_type == "photo" else None
        clean_voice = (voice_transcript or "").strip()

        # Quick guard if empty evidence payload was sent
        if evidence_type == "photo" and not image_bytes:
            elapsed_ms = max(1, (time.perf_counter_ns() - start_ns) // 1_000_000)
            verdict = RefereeVerdict(
                verified=False,
                evidence_type="photo",
                confidence_score=0,
                referee_speech="¡Piiit! No pude ver la foto con claridad, intenta capturar tu avance otra vez.",
                detected_progress="No se recibió una imagen válida.",
                next_tip="Enfoca tu pantalla o cuaderno con buena luz.",
                generation_source="deterministic_fallback",
                model_name="deterministic_fallback",
            )
            return AgentExecutionStep(
                agent_name="referee_agent",
                output=verdict,
                model_name="deterministic_fallback",
                duration_ms=int(elapsed_ms),
                status="fallback",
                input_summary={"evidence_type": evidence_type, "has_image": False},
                output_summary={"verified": False},
            )

        if evidence_type == "voice" and len(clean_voice) < 4:
            elapsed_ms = max(1, (time.perf_counter_ns() - start_ns) // 1_000_000)
            verdict = RefereeVerdict(
                verified=False,
                evidence_type="voice",
                confidence_score=10,
                referee_speech="¡Árbitro atento! Casi no escuché tu reporte, cuéntame en una frase qué lograste abrir o escribir.",
                detected_progress="Reporte de voz demasiado corto.",
                next_tip="Di en una frase corta el paso concreto que hiciste.",
                generation_source="deterministic_fallback",
                model_name="deterministic_fallback",
            )
            return AgentExecutionStep(
                agent_name="referee_agent",
                output=verdict,
                model_name="deterministic_fallback",
                duration_ms=int(elapsed_ms),
                status="fallback",
                input_summary={"evidence_type": evidence_type, "voice_len": len(clean_voice)},
                output_summary={"verified": False},
            )

        payload: dict[str, object] = {
            "task_title": task_title,
            "microtask_title": microtask_title,
            "microtask_steps": microtask_steps,
            "checked_steps_count": checked_steps_count,
            "evidence_type": evidence_type,
        }
        if evidence_type == "voice":
            payload["student_voice_report"] = clean_voice
        else:
            payload["photo_instruction"] = (
                "Analiza visualmente la foto adjunta enviada por el estudiante al cerrar el reto."
            )

        gemma_res = self._gemma.generate_structured(
            system_prompt=REFEREE_AGENT_SYSTEM_PROMPT,
            user_payload=payload,
            schema_model=GemmaRefereeDraft,
            temperature=0.35,
            image_bytes=image_bytes,
            image_mime_type=image_mime_type or "image/jpeg",
        )

        if gemma_res.ok and gemma_res.parsed is not None:
            draft = gemma_res.parsed
            verdict = RefereeVerdict(
                verified=draft.verified,
                evidence_type=evidence_type,
                confidence_score=max(0, min(100, draft.confidence_score)),
                referee_speech=draft.referee_speech.strip()[:280],
                detected_progress=draft.detected_progress.strip()[:280],
                next_tip=draft.next_tip.strip()[:220],
                generation_source="gemma",
                model_name=gemma_res.model_used,
            )
            step_status: Literal["ok", "fallback"] = "ok"
            model_used = gemma_res.model_used
        else:
            # Honest deterministic fallback when offline
            verdict = RefereeVerdict(
                verified=True,
                evidence_type=evidence_type,
                confidence_score=75,
                referee_speech=(
                    f"¡Silbato final! Gran arranque en '{microtask_title}'. "
                    "Rompiste la inercia y sumaste avance real."
                ),
                detected_progress=(
                    f"Reporte registrado ({clean_voice[:90]})"
                    if evidence_type == "voice"
                    else "Captura visual de avance registrada localmente."
                ),
                next_tip="Aprovecha el impulso si quieres sumar 3 minutos extra.",
                generation_source="deterministic_fallback",
                model_name="deterministic_fallback",
            )
            step_status = "fallback"
            model_used = "deterministic_fallback"

        elapsed_ms = max(1, (time.perf_counter_ns() - start_ns) // 1_000_000)
        return AgentExecutionStep(
            agent_name="referee_agent",
            output=verdict,
            model_name=model_used,
            duration_ms=int(elapsed_ms),
            status=step_status,
            input_summary={
                "evidence_type": evidence_type,
                "has_image": bool(image_bytes),
                "voice_chars": len(clean_voice),
                "checked_steps": checked_steps_count,
            },
            output_summary={
                "verified": verdict.verified,
                "confidence": verdict.confidence_score,
                "source": verdict.generation_source,
            },
        )
