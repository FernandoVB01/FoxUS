from __future__ import annotations

from typing import Any
from fastapi.testclient import TestClient
from pydantic import BaseModel, SecretStr
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agents.contracts import (
    GemmaContextInterpretation,
    GemmaHookDraft,
    GemmaMicrotaskDraft,
)
from app.agents.orchestrator import FoxusOrchestrator
from app.core.config import Settings
from app.models.entities import Hook, Microtask, Task, UserPreference
from app.repositories.seed import ensure_demo_user_and_seed
from app.schemas.agents import GenerateMissionRequest
from app.services.gemma_client import GemmaClient, GemmaGenerationResult


class FakeLiveGemmaClient(GemmaClient):
    """Deterministic mock simulating real `gemma-4-26b-a4b-it` structured JSON responses."""

    def __init__(self) -> None:
        super().__init__(
            Settings(
                GEMINI_API_KEY=SecretStr("mock-valid-key"),
                GEMMA_MODEL="gemma-4-26b-a4b-it",
            )
        )

    def generate_structured(
        self,
        *,
        system_prompt: str,
        user_payload: dict[str, Any],
        schema_model: type[BaseModel],
        temperature: float = 0.35,
    ) -> GemmaGenerationResult[Any]:
        if schema_model is GemmaContextInterpretation:
            parsed = GemmaContextInterpretation(
                contextual_summary=(
                    "La presentación tiene 12 diapositivas y entrega cercana; "
                    "conviene romper la inercia abriendo solo la portada."
                )
            )
        elif schema_model is GemmaMicrotaskDraft:
            parsed = GemmaMicrotaskDraft(
                title="Abre la presentación y crea la portada",
                description="Abre PowerPoint o Google Slides, inserta la portada y escribe el título.",
                estimated_seconds=180 if not user_payload.get("must_reduce_difficulty") else 90,
                success_criteria="Portada creada con título escrito.",
                rationale="Elimina la resistencia de empezar sin exigir las 12 diapositivas.",
            )
        elif schema_model is GemmaHookDraft:
            hook_type = user_payload.get("selected_hook_type")
            if hook_type == "fox_bzz":
                msg = "¡Bzz! ¿Y si abrimos solo la portada en 2 minutos y ya?"
            else:
                msg = "¡FOX tiene un reto relámpago de 3 min! Crea solo la portada ahora."
            parsed = GemmaHookDraft(message=msg, intensity=2, suggested_delay_seconds=0)
        else:
            raise AssertionError(f"Unexpected schema model: {schema_model}")

        return GemmaGenerationResult(
            ok=True,
            parsed=parsed,
            model_used="gemma-4-26b-a4b-it",
            latency_ms=412,
            attempts=1,
            error_reason=None,
            is_fallback=False,
        )


def test_orchestrator_with_gemma_challenge_and_fox_bzz(db_session: Session) -> None:
    user = ensure_demo_user_and_seed(db_session)
    prefs = db_session.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    task = db_session.scalar(select(Task).where(Task.user_id == user.id))
    assert prefs is not None and task is not None

    orchestrator = FoxusOrchestrator(db=db_session, gemma_client=FakeLiveGemmaClient())

    # Mode 1: FOX Challenge (Desafío Relámpago)
    challenge_res = orchestrator.generate_mission(
        user=user,
        task=task,
        preferences=prefs,
        request=GenerateMissionRequest(
            task_id=task.id,
            available_minutes=5,
            requested_mode="challenge",
            ignore_cooldown_for_demo=True,
        ),
    )
    assert challenge_res.is_simulated_ai is False
    assert challenge_res.microtask.model_name == "gemma-4-26b-a4b-it"
    assert challenge_res.hook.hook_type == "challenge"
    assert challenge_res.hook.sound_id == "challenge_start"
    assert challenge_res.hook.animation_id == "fox_challenge"
    assert challenge_res.dispatched_device_command_id is not None

    # Mode 2: FOX BZZ (La Mosca Traviesa)
    bzz_res = orchestrator.generate_mission(
        user=user,
        task=task,
        preferences=prefs,
        request=GenerateMissionRequest(
            task_id=task.id,
            available_minutes=5,
            requested_mode="fox_bzz",
            ignore_cooldown_for_demo=True,
        ),
    )
    assert bzz_res.is_simulated_ai is False
    assert bzz_res.hook.hook_type == "fox_bzz"
    assert "bzz" in bzz_res.hook.message.lower()
    assert bzz_res.hook.sound_id == "fly_buzz"
    assert bzz_res.hook.animation_id == "fox_playful"


def test_fallback_never_masquerades_as_real_gemma_and_adapts_difficulty(
    client: TestClient,
    auth_headers: dict[str, str],
    db_session: Session,
) -> None:
    tasks_resp = client.get("/api/v1/tasks")
    assert tasks_resp.status_code == 200
    task_id = tasks_resp.json()[0]["id"]

    # Simulate two prior rejected hooks on this exact task
    import uuid

    user = ensure_demo_user_and_seed(db_session)
    task_obj = db_session.get(Task, uuid.UUID(task_id))
    assert task_obj is not None
    m1 = Microtask(
        task_id=task_obj.id,
        title="Paso previo",
        description="Desc",
        estimated_seconds=180,
        success_criteria="Crit",
        model_name="deterministic_fallback",
    )
    db_session.add(m1)
    db_session.flush()
    db_session.add_all(
        [
            Hook(
                user_id=user.id,
                microtask_id=m1.id,
                hook_type="challenge",
                message="Intento 1",
                intensity=2,
                channel="both",
                status="rejected",
            ),
            Hook(
                user_id=user.id,
                microtask_id=m1.id,
                hook_type="challenge",
                message="Intento 2",
                intensity=2,
                channel="both",
                status="postponed",
            ),
        ]
    )
    db_session.commit()

    gen_resp = client.post(
        "/api/v1/agents/generate-mission",
        headers=auth_headers,
        json={
            "task_id": task_id,
            "available_minutes": 5,
            "requested_mode": "challenge",
            "ignore_cooldown_for_demo": True,
        },
    )
    assert gen_resp.status_code == 200
    mission = gen_resp.json()
    assert mission["is_simulated_ai"] is True
    assert mission["microtask"]["model_name"] == "deterministic_fallback"
    assert mission["microtask"]["difficulty_adapted"] is True
    assert mission["microtask"]["estimated_seconds"] <= 120

    trace_resp = client.get(f"/api/v1/agents/traces/{mission['trace_id']}")
    assert trace_resp.status_code == 200
    trace_body = trace_resp.json()
    assert trace_body["used_real_gemma"] is False
    step_names = [s["agent_name"] for s in trace_body["steps"]]
    assert step_names == [
        "context_agent",
        "learning_agent",
        "microtask_agent",
        "hook_agent",
        "policy_engine",
        "orchestrator",
    ]
