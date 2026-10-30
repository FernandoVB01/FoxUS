from __future__ import annotations

import re
import time

from app.agents.contracts import (
    MICROTASK_AGENT_SYSTEM_PROMPT,
    AgentExecutionStep,
    GemmaMicrotaskDraft,
    format_lcd_line,
)
from app.schemas.agents import MicrotaskProposal, TaskContext
from app.services.gemma_client import GemmaClient

_LEADING_BULLET_RE = re.compile(r"^[\s•\-\*\d\.\)\:]+")


def normalize_bullet_steps(
    raw_steps: list[str],
    description: str,
    success_criteria: str,
) -> list[str]:
    """Ensure every mission returns 2 to 3 crisp, scannable bullet points (<= 85 chars)."""
    cleaned: list[str] = []
    for item in raw_steps:
        stripped = _LEADING_BULLET_RE.sub("", (item or "").strip()).strip()
        if len(stripped) >= 4:
            cleaned.append(stripped[:90])
        if len(cleaned) >= 3:
            break

    if len(cleaned) >= 2:
        return cleaned

    # Fallback split from description + success criteria
    clauses = [
        _LEADING_BULLET_RE.sub("", part.strip()).strip()
        for part in re.split(r"[\.;\n]+", description)
        if len(part.strip()) >= 6
    ]
    for cl in clauses[:2]:
        if cl not in cleaned:
            cleaned.append(cl[:90])
    crit = _LEADING_BULLET_RE.sub("", success_criteria.strip()).strip()
    if crit and len(cleaned) < 3:
        cleaned.append(f"Meta: {crit[:80]}")
    return cleaned[:3] or [description[:85]]


class MicrotaskAgent:
    """
    Microtask Agent (Section 4.3) — Core Gemma 4 component.
    Transforms intimidating tasks into concrete, non-arithmetic micro-actions
    presented as short checkable bullet points (`steps`).
    """

    def __init__(self, gemma_client: GemmaClient) -> None:
        self._gemma = gemma_client

    @staticmethod
    def _build_deterministic_fallback(
        context: TaskContext, difficulty_adapted: bool
    ) -> MicrotaskProposal:
        lower_title = context.task_title.lower()
        target_seconds = 120 if difficulty_adapted else min(180, context.available_minutes * 60)
        target_seconds = max(60, target_seconds)

        if any(k in lower_title for k in ("diapositiva", "presentaci", "exposici", "slide")):
            title = "Crear portada y título"
            steps = [
                "Abre tu archivo de presentación",
                "Crea únicamente la diapositiva de portada",
                "Escribe el título principal y tu nombre",
            ]
            desc = (
                "Abre tu archivo de presentación, crea únicamente la diapositiva de portada "
                "y escribe el título principal junto con tu nombre."
            )
            criteria = "Portada y título guardados."
        elif any(k in lower_title for k in ("cálculo", "calculo", "capítulo", "capitulo", "ejercic", "estudiar")):
            title = "Abrir cap. 1 y paso 1"
            steps = [
                "Abre la guía o capítulo 1 en tu escritorio",
                "Elige únicamente el primer ejercicio",
                "Escribe los datos iniciales en una hoja limpia",
            ]
            desc = (
                "Abre el primer capítulo o guía en tu escritorio y escribe el planteamiento "
                "inicial del primer ejercicio en una hoja limpia."
            )
            criteria = "Primer ejercicio planteado en papel."
        elif any(k in lower_title for k in ("informe", "ensayo", "tesis", "documento", "redactar")):
            title = "Esquema de 3 viñetas"
            steps = [
                "Abre un documento en blanco y ponle nombre",
                "Escribe un título tentativo arriba",
                "Anota solo 3 subtítulos clave en viñetas",
            ]
            desc = (
                "Abre el documento en blanco, escribe el título tentativo y anota solo "
                "3 subtítulos clave en forma de viñetas."
            )
            criteria = "Documento guardado con 3 viñetas."
        else:
            title = "Preparar primer paso"
            steps = [
                f"Abre tu espacio de trabajo para '{context.task_title[:28]}'",
                "Escribe el encabezado o primer bloque visible",
                "Guarda ese primer avance sin perfeccionar",
            ]
            desc = (
                f"Abre el entorno de trabajo para '{context.task_title}' y completa únicamente "
                "el primer encabezado o elemento inicial visible."
            )
            criteria = "Primer avance tangible guardado."

        return MicrotaskProposal(
            task_id=context.task_id,
            title=title,
            lcd_short_title=format_lcd_line(title, 16),
            description=desc,
            steps=steps,
            estimated_seconds=target_seconds,
            success_criteria=criteria,
            rationale=(
                "Modo determinista de respaldo: divide el arranque en 3 viñetas físicas cortas."
            ),
            difficulty_adapted=difficulty_adapted,
            generation_source="deterministic_fallback",
            model_name="deterministic_fallback",
        )

    def propose(self, *, context: TaskContext) -> AgentExecutionStep[MicrotaskProposal]:
        start_ns = time.perf_counter_ns()
        difficulty_adapted = (
            context.recent_rejections_for_task >= 2
            or (context.perceived_difficulty is not None and context.perceived_difficulty >= 4)
        )
        max_seconds = 120 if difficulty_adapted else min(300, max(90, context.available_minutes * 60))

        gemma_res = self._gemma.generate_structured(
            system_prompt=MICROTASK_AGENT_SYSTEM_PROMPT,
            user_payload={
                "task_title": context.task_title,
                "task_description": context.task_description,
                "urgency_level": context.urgency_level,
                "needs_pressure": context.needs_pressure,
                "hours_until_deadline": context.hours_until_deadline,
                "available_minutes": context.available_minutes,
                "max_recommended_seconds": max_seconds,
                "perceived_difficulty": context.perceived_difficulty,
                "recent_rejections_for_task": context.recent_rejections_for_task,
                "must_reduce_difficulty": difficulty_adapted,
                "contextual_summary": context.contextual_summary,
            },
            schema_model=GemmaMicrotaskDraft,
            temperature=0.4,
        )

        if gemma_res.ok and gemma_res.parsed is not None:
            draft = gemma_res.parsed
            bounded_seconds = min(draft.estimated_seconds, max_seconds)
            bounded_seconds = max(60, bounded_seconds)
            clean_steps = normalize_bullet_steps(
                draft.steps,
                draft.description.strip(),
                draft.success_criteria.strip(),
            )
            proposal = MicrotaskProposal(
                task_id=context.task_id,
                title=draft.title.strip(),
                lcd_short_title=format_lcd_line(draft.title, 16),
                description=draft.description.strip(),
                steps=clean_steps,
                estimated_seconds=bounded_seconds,
                success_criteria=draft.success_criteria.strip(),
                rationale=draft.rationale.strip(),
                difficulty_adapted=difficulty_adapted,
                generation_source="gemma",
                model_name=gemma_res.model_used,
            )
            step_status = "ok"
            model_used = gemma_res.model_used
        else:
            proposal = self._build_deterministic_fallback(context, difficulty_adapted)
            step_status = "fallback"
            model_used = "deterministic_fallback"

        elapsed_ms = max(1, int((time.perf_counter_ns() - start_ns) // 1_000_000))
        return AgentExecutionStep(
            agent_name="microtask_agent",
            output=proposal,
            model_name=model_used,
            duration_ms=elapsed_ms,
            status=step_status,  # type: ignore[arg-type]
            input_summary={
                "task_id": str(context.task_id),
                "difficulty_adapted": difficulty_adapted,
                "max_seconds": max_seconds,
            },
            output_summary={
                "title": proposal.title,
                "steps_count": len(proposal.steps),
                "lcd_short_title": proposal.lcd_short_title,
                "estimated_seconds": proposal.estimated_seconds,
                "generation_source": proposal.generation_source,
                "fallback_reason": gemma_res.error_reason,
            },
        )
