from __future__ import annotations

from datetime import UTC, datetime
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.entities import FocusSession, Microtask, Task, UserPreference
from app.repositories.seed import ensure_demo_user_and_seed
from app.schemas.agents import HookProposal, MicrotaskProposal
from app.services.policy_engine import PolicyEngine, is_within_quiet_hours
from app.services.reward_engine import RewardEngine


def test_quiet_hours_overnight_window() -> None:
    # 04:00 UTC is 23:00-05:00 in America/Guayaquil (UTC-5) -> 23:00 local -> inside 23:00..07:00
    dt_inside = datetime(2026, 10, 29, 4, 30, tzinfo=UTC)
    assert is_within_quiet_hours(dt_inside, "23:00", "07:00", "America/Guayaquil") is True

    # 16:00 UTC is 11:00 local -> outside 23:00..07:00
    dt_outside = datetime(2026, 10, 29, 16, 0, tzinfo=UTC)
    assert is_within_quiet_hours(dt_outside, "23:00", "07:00", "America/Guayaquil") is False


def test_policy_blocks_when_silenced(db_session: Session) -> None:
    user = ensure_demo_user_and_seed(db_session)
    prefs = db_session.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    task = db_session.scalar(select(Task).where(Task.user_id == user.id))
    assert prefs is not None and task is not None

    prefs.intervention_level = 0
    db_session.commit()

    decision = PolicyEngine().evaluate_intervention(
        db_session,
        preferences=prefs,
        microtask=MicrotaskProposal(
            task_id=task.id,
            title="Crear portada",
            lcd_short_title="Crear portada",
            description="Abre la presentación y escribe el título.",
            estimated_seconds=180,
            success_criteria="Portada creada",
            rationale="Inicio concreto",
            generation_source="gemma",
            model_name="gemma-4-26b-a4b-it",
        ),
        hook=HookProposal(
            hook_type="challenge",
            visual_mode_name="Desafío Relámpago",
            message="¡Reto de 3 min!",
            intensity=2,
            channel="both",
            sound_id="challenge_start",
            animation_id="fox_challenge",
            generation_source="gemma",
            model_name="gemma-4-26b-a4b-it",
        ),
    )
    assert decision.allowed is False
    assert "INTERVENTIONS_SILENCED_BY_USER" in decision.reasons


def test_reward_engine_idempotency(db_session: Session) -> None:
    user = ensure_demo_user_and_seed(db_session)
    task = db_session.scalar(select(Task).where(Task.user_id == user.id))
    assert task is not None

    micro = Microtask(
        task_id=task.id,
        title="Abrir portada",
        description="Diseñar portada",
        estimated_seconds=180,
        success_criteria="Portada lista",
        model_name="gemma-4-26b-a4b-it",
    )
    db_session.add(micro)
    db_session.flush()

    sess = FocusSession(user_id=user.id, microtask_id=micro.id, status="started")
    db_session.add(sess)
    db_session.commit()

    rewards = RewardEngine()
    g1 = rewards.grant_session_start_xp(
        db_session, user_id=user.id, session_id=sess.id, microtask_id=micro.id
    )
    g2 = rewards.grant_session_start_xp(
        db_session, user_id=user.id, session_id=sess.id, microtask_id=micro.id
    )
    assert g1.xp_awarded == 10 and g1.already_granted is False
    assert g2.xp_awarded == 0 and g2.already_granted is True

    c1 = rewards.grant_completion_xp(
        db_session,
        user_id=user.id,
        session_id=sess.id,
        microtask_id=micro.id,
        voluntary_continuity=True,
    )
    c2 = rewards.grant_completion_xp(
        db_session,
        user_id=user.id,
        session_id=sess.id,
        microtask_id=micro.id,
        voluntary_continuity=True,
    )
    assert sum(x.xp_awarded for x in c1) == 30
    assert sum(x.xp_awarded for x in c2) == 0
    db_session.commit()
    assert rewards.get_user_total_xp(db_session, user.id) == 40
