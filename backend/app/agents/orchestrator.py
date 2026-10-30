from __future__ import annotations

from datetime import UTC, datetime
import time
import uuid
from typing import Any
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agents.context_agent import ContextAgent
from app.agents.contracts import AgentExecutionStep, format_lcd_line
from app.agents.hook_agent import HookAgent
from app.agents.learning_agent import LearningAgent
from app.agents.microtask_agent import MicrotaskAgent
from app.models.entities import (
    AgentTrace,
    Device,
    DeviceCommand,
    Hook,
    Microtask,
    Task,
    User,
    UserPreference,
)
from app.schemas.agents import (
    GenerateMissionRequest,
    GenerateMissionResponse,
    PressureEvaluationRequest,
    PressureEvaluationResponse,
)
from app.services.gemma_client import GemmaClient
from app.services.policy_engine import PolicyEngine


class FoxusOrchestrator:
    """
    Central deterministic and auditable controller for FOXUS multi-agent intelligence (Section 4.1).
    Executes a bounded single-pass pipeline per `trace_id`:
      ContextAgent -> LearningAgent -> MicrotaskAgent -> HookAgent -> PolicyEngine
    Persists traces, stores generated Microtask & Hook, updates stats, and dispatches ESP32 commands.
    """

    def __init__(
        self,
        db: Session,
        *,
        gemma_client: GemmaClient | None = None,
        policy_engine: PolicyEngine | None = None,
    ) -> None:
        self._db = db
        self._gemma = gemma_client or GemmaClient()
        self._policy = policy_engine or PolicyEngine()
        self._context_agent = ContextAgent(db=db, gemma_client=self._gemma)
        self._learning_agent = LearningAgent(db=db)
        self._microtask_agent = MicrotaskAgent(gemma_client=self._gemma)
        self._hook_agent = HookAgent(gemma_client=self._gemma)

    def _record_trace_step(
        self,
        *,
        trace_id: str,
        user_id: uuid.UUID,
        step: AgentExecutionStep[Any],
    ) -> None:
        self._db.add(
            AgentTrace(
                trace_id=trace_id,
                user_id=user_id,
                agent_name=step.agent_name,
                model_name=step.model_name,
                duration_ms=step.duration_ms,
                status=step.status,
                input_summary=step.input_summary,
                output_summary=step.output_summary,
            )
        )

    def _dispatch_mission_to_devices(
        self,
        *,
        user_id: uuid.UUID,
        microtask_row: Microtask,
        hook_row: Hook,
        lcd_short_title: str,
        audio_allowed: bool,
    ) -> uuid.UUID | None:
        devices = list(
            self._db.scalars(
                select(Device)
                .where(Device.user_id == user_id)
                .order_by(Device.last_seen_at.desc().nullslast())
            ).all()
        )
        if not devices:
            return None

        first_cmd_id: uuid.UUID | None = None
        line_1 = "FOX BZZ!" if hook_row.hook_type == "fox_bzz" else "NEW MISSION!"
        line_2 = format_lcd_line(lcd_short_title, 16)

        for dev in devices:
            cmd = DeviceCommand(
                device_id=dev.id,
                command_type="SHOW_MISSION",
                payload={
                    "microtask_id": str(microtask_row.id),
                    "hook_id": str(hook_row.id),
                    "hook_type": hook_row.hook_type,
                    "estimated_seconds": microtask_row.estimated_seconds,
                    "lcd_line_1": line_1,
                    "lcd_line_2": line_2,
                    "buzzer_allowed": audio_allowed,
                },
                status="pending",
            )
            self._db.add(cmd)
            self._db.flush()
            if first_cmd_id is None:
                first_cmd_id = cmd.id

        return first_cmd_id

    def generate_mission(
        self,
        *,
        user: User,
        task: Task,
        preferences: UserPreference,
        request: GenerateMissionRequest,
        explicit_user_request: bool = True,
    ) -> GenerateMissionResponse:
        pipeline_start_ns = time.perf_counter_ns()
        trace_id = f"trc_{uuid.uuid4().hex[:16]}"

        # Step 1: Context Agent
        ctx_step = self._context_agent.analyze(
            user_id=user.id,
            task=task,
            preferences=preferences,
            available_minutes=request.available_minutes,
            perceived_difficulty=request.perceived_difficulty,
        )
        self._record_trace_step(trace_id=trace_id, user_id=user.id, step=ctx_step)

        # Step 2: Learning Agent statistical recommendation
        learn_step = self._learning_agent.recommend(user_id=user.id, preferences=preferences)
        self._record_trace_step(trace_id=trace_id, user_id=user.id, step=learn_step)

        # Step 3: Microtask Agent (Gemma 4)
        micro_step = self._microtask_agent.propose(context=ctx_step.output)
        self._record_trace_step(trace_id=trace_id, user_id=user.id, step=micro_step)

        # Step 4: Hook Agent (Gemma 4 + adaptive rules)
        hook_step = self._hook_agent.compose(
            context=ctx_step.output,
            microtask=micro_step.output,
            preferences=preferences,
            learning=learn_step.output,
            requested_mode=request.requested_mode,
        )
        self._record_trace_step(trace_id=trace_id, user_id=user.id, step=hook_step)

        # Step 5: Deterministic Policy Engine gate
        policy_start_ns = time.perf_counter_ns()
        policy_decision = self._policy.evaluate_intervention(
            self._db,
            preferences=preferences,
            microtask=micro_step.output,
            hook=hook_step.output,
            ignore_cooldown_for_demo=request.ignore_cooldown_for_demo,
            explicit_user_request=explicit_user_request,
        )
        policy_ms = max(1, int((time.perf_counter_ns() - policy_start_ns) // 1_000_000))
        self._db.add(
            AgentTrace(
                trace_id=trace_id,
                user_id=user.id,
                agent_name="policy_engine",
                model_name="deterministic_rules",
                duration_ms=policy_ms,
                status="ok" if policy_decision.allowed else "blocked",
                input_summary={
                    "proposed_hook": hook_step.output.hook_type,
                    "proposed_intensity": hook_step.output.intensity,
                    "explicit_user_request": explicit_user_request,
                },
                output_summary=policy_decision.model_dump(),
            )
        )

        # Persist Microtask and Hook
        micro_row = Microtask(
            task_id=task.id,
            title=micro_step.output.title,
            description=micro_step.output.description,
            estimated_seconds=micro_step.output.estimated_seconds,
            success_criteria=micro_step.output.success_criteria,
            model_name=micro_step.output.model_name,
        )
        self._db.add(micro_row)
        self._db.flush()

        hook_status = "shown" if policy_decision.allowed else "blocked_by_policy"
        persisted_intensity = max(1, policy_decision.effective_intensity or 1)
        hook_row = Hook(
            user_id=user.id,
            microtask_id=micro_row.id,
            hook_type=hook_step.output.hook_type,
            message=hook_step.output.message,
            intensity=persisted_intensity,
            channel=hook_step.output.channel,
            status=hook_status,
        )
        self._db.add(hook_row)
        self._db.flush()

        device_cmd_id: uuid.UUID | None = None
        if policy_decision.allowed:
            self._learning_agent.record_outcome(
                user_id=user.id,
                hook_type=hook_row.hook_type,
                outcome="shown",
            )
            if request.dispatch_to_device and policy_decision.effective_channel in ("esp32", "both"):
                device_cmd_id = self._dispatch_mission_to_devices(
                    user_id=user.id,
                    microtask_row=micro_row,
                    hook_row=hook_row,
                    lcd_short_title=micro_step.output.lcd_short_title,
                    audio_allowed=policy_decision.audio_allowed,
                )

        is_simulated_ai = (
            micro_step.output.generation_source != "gemma"
            or hook_step.output.generation_source != "gemma"
        )

        total_ms = max(1, int((time.perf_counter_ns() - pipeline_start_ns) // 1_000_000))
        self._db.add(
            AgentTrace(
                trace_id=trace_id,
                user_id=user.id,
                agent_name="orchestrator",
                model_name=self._gemma.configured_model if not is_simulated_ai else "deterministic_fallback",
                duration_ms=total_ms,
                status="fallback" if is_simulated_ai else "ok",
                input_summary={
                    "task_id": str(task.id),
                    "requested_mode": request.requested_mode,
                    "dispatch_to_device": request.dispatch_to_device,
                },
                output_summary={
                    "microtask_id": str(micro_row.id),
                    "hook_id": str(hook_row.id),
                    "policy_allowed": policy_decision.allowed,
                    "is_simulated_ai": is_simulated_ai,
                    "dispatched_device_command_id": str(device_cmd_id) if device_cmd_id else None,
                },
            )
        )

        self._db.commit()

        return GenerateMissionResponse(
            schema_version=1,
            trace_id=trace_id,
            task_id=task.id,
            microtask_id=micro_row.id,
            hook_id=hook_row.id,
            is_simulated_ai=is_simulated_ai,
            policy=policy_decision,
            context=ctx_step.output,
            microtask=micro_step.output,
            hook=hook_step.output,
            learning=learn_step.output,
            dispatched_device_command_id=device_cmd_id,
        )

    def evaluate_proactive_pressure(
        self,
        *,
        user: User,
        preferences: UserPreference,
        request: PressureEvaluationRequest,
    ) -> PressureEvaluationResponse:
        """
        Duolingo-style adaptive urgency & pressure evaluator:
        Checks whether the user has an urgent deadline or explicitly flagged `needs_pressure`,
        uses LearningAgent to pick the hook style with highest real engagement, and decides
        whether interrupting right now is justified.
        """
        if preferences.intervention_level <= 0 and not request.demo_force:
            return PressureEvaluationResponse(
                should_interrupt=False,
                urgency_score=0,
                reason_short="Modo descanso activo: FOX respeta tu silencio.",
                recommended_hook="challenge",
                mission=None,
            )

        from app.api.v1.integrations import auto_sync_google_if_connected

        auto_sync_google_if_connected(db=self._db, user=user, min_interval_seconds=20)

        candidates = list(
            self._db.scalars(
                select(Task)
                .where(Task.user_id == user.id, Task.status != "completed")
                .order_by(Task.needs_pressure.desc(), Task.created_at.desc())
            ).all()
        )
        if request.task_id is not None:
            candidates = [t for t in candidates if t.id == request.task_id] or candidates

        if not candidates:
            return PressureEvaluationResponse(
                should_interrupt=False,
                urgency_score=0,
                reason_short="No tienes tareas pendientes por iniciar.",
                recommended_hook="challenge",
                mission=None,
            )

        now = datetime.now(UTC)

        def score_task(t: Task) -> int:
            s = 35
            if t.needs_pressure:
                s += 35
            if t.deadline is not None:
                dl = t.deadline if t.deadline.tzinfo else t.deadline.replace(tzinfo=UTC)
                hrs = (dl - now).total_seconds() / 3600.0
                if hrs <= 6:
                    s += 35
                elif hrs <= 24:
                    s += 25
                elif hrs <= 72:
                    s += 12
            return min(100, s)

        best_task = max(candidates, key=score_task)
        urgency_score = score_task(best_task)
        learn_step = self._learning_agent.recommend(user_id=user.id, preferences=preferences)
        recommended_hook = learn_step.output.recommended_hook_type

        # Interrupt automatically if urgency >= 60 (needs_pressure OR deadline <= 24h) or demo_force
        if urgency_score < 60 and not request.demo_force:
            return PressureEvaluationResponse(
                should_interrupt=False,
                urgency_score=urgency_score,
                reason_short=(
                    f"Urgencia moderada ({urgency_score}/100). Activa 'Necesito presión' o acerca la entrega "
                    "para interrupciones automáticas."
                ),
                recommended_hook=recommended_hook,
                mission=None,
            )

        mission = self.generate_mission(
            user=user,
            task=best_task,
            preferences=preferences,
            request=GenerateMissionRequest(
                task_id=best_task.id,
                available_minutes=3,
                requested_mode=recommended_hook,
                dispatch_to_device=request.dispatch_to_device,
                ignore_cooldown_for_demo=request.demo_force,
            ),
            explicit_user_request=request.demo_force,
        )

        if not mission.policy.allowed:
            return PressureEvaluationResponse(
                should_interrupt=False,
                urgency_score=urgency_score,
                reason_short=f"Pausado por Policy Engine: {', '.join(mission.policy.reasons)}",
                recommended_hook=recommended_hook,
                mission=mission,
            )

        reason = (
            f"🔥 Pediste presión en '{best_task.title[:34]}' · FOX eligió {mission.hook.visual_mode_name}"
            if best_task.needs_pressure
            else f"⏰ Entrega próxima en '{best_task.title[:34]}' ({urgency_score}/100)"
        )
        return PressureEvaluationResponse(
            should_interrupt=True,
            urgency_score=urgency_score,
            reason_short=reason,
            recommended_hook=recommended_hook,
            mission=mission,
        )
