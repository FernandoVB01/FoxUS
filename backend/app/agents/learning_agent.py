from __future__ import annotations

import math
import time
import uuid
from typing import Literal, Protocol
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agents.contracts import AgentExecutionStep
from app.models.entities import UserHookStat, UserPreference
from app.schemas.agents import LearningSnapshot

SUPPORTED_MVP_HOOKS: tuple[Literal["challenge", "fox_bzz"], ...] = ("challenge", "fox_bzz")


class BanditScoringStrategy(Protocol):
    """Extensible interface prepared for Thompson Sampling or contextual bandits (Section 4.5)."""

    name: str

    def score_arms(self, stats_map: dict[str, UserHookStat]) -> dict[str, float]: ...


class AdaptiveUcbStrategy:
    """
    Deterministic Laplace-smoothed utility + UCB exploration strategy.
    Weights real action initiation and completion higher than mere clicks,
    and penalizes user rejections/disturbances.
    """

    name = "adaptive_ucb_v1"

    def score_arms(self, stats_map: dict[str, UserHookStat]) -> dict[str, float]:
        total_trials = sum(max(0, s.shown_count) for s in stats_map.values()) + len(SUPPORTED_MVP_HOOKS)
        scores: dict[str, float] = {}

        for arm in SUPPORTED_MVP_HOOKS:
            row = stats_map.get(arm)
            shown = row.shown_count if row else 0
            started = row.started_count if row else 0
            completed = row.completed_count if row else 0
            rejected = row.rejected_count if row else 0

            # Laplace-smoothed empirical value prioritizing real work started & completed
            denom = shown + 2.0
            start_rate = (started + 1.0) / denom
            completion_rate = (completed + 1.0) / denom
            rejection_penalty = (rejected / max(1.0, float(shown))) * 0.35

            empirical_utility = 0.45 * start_rate + 0.55 * completion_rate - rejection_penalty
            exploration_bonus = 0.22 * math.sqrt(math.log(total_trials + 1.0) / (shown + 1.0))
            scores[arm] = round(max(0.01, empirical_utility + exploration_bonus), 4)

        return scores


class LearningAgent:
    """
    Learning Agent (Section 4.5).
    Pure Python deterministic statistical learner; never uses Gemma for math.
    Updates per-user hook stats across acceptance, session starts, completions,
    rejections, real durations, and voluntary continuity.
    """

    def __init__(
        self,
        db: Session,
        strategy: BanditScoringStrategy | None = None,
    ) -> None:
        self._db = db
        self._strategy = strategy or AdaptiveUcbStrategy()

    def _get_or_create_stat(self, user_id: uuid.UUID, hook_type: str) -> UserHookStat:
        stat = self._db.scalar(
            select(UserHookStat).where(
                UserHookStat.user_id == user_id,
                UserHookStat.hook_type == hook_type,
            )
        )
        if stat is None:
            stat = UserHookStat(
                user_id=user_id,
                hook_type=hook_type,
                shown_count=0,
                accepted_count=0,
                started_count=0,
                completed_count=0,
                rejected_count=0,
            )
            self._db.add(stat)
            self._db.flush()
        return stat

    def recommend(
        self,
        *,
        user_id: uuid.UUID,
        preferences: UserPreference,
    ) -> AgentExecutionStep[LearningSnapshot]:
        start_ns = time.perf_counter_ns()
        stats_map = {
            arm: self._get_or_create_stat(user_id, arm)
            for arm in SUPPORTED_MVP_HOOKS
        }
        scores = self._strategy.score_arms(stats_map)
        best_hook: Literal["challenge", "fox_bzz"] = (
            "fox_bzz" if scores.get("fox_bzz", 0.0) > scores.get("challenge", 0.0) else "challenge"
        )

        total_shown = sum(s.shown_count for s in stats_map.values())
        total_accepted = sum(s.accepted_count for s in stats_map.values())
        total_started = sum(s.started_count for s in stats_map.values())
        total_completed = sum(s.completed_count for s in stats_map.values())
        total_rejected = sum(s.rejected_count for s in stats_map.values())

        denom = float(max(1, total_shown))
        snapshot = LearningSnapshot(
            recommended_hook_type=best_hook,
            acceptance_rate=round(total_accepted / denom, 4),
            start_rate=round(total_started / denom, 4),
            completion_rate=round(total_completed / max(1.0, float(total_started)), 4)
            if total_started > 0
            else 0.0,
            rejection_rate=round(total_rejected / denom, 4),
            scores_by_hook=scores,
            strategy=self._strategy.name,
        )

        elapsed_ms = max(1, int((time.perf_counter_ns() - start_ns) // 1_000_000))
        return AgentExecutionStep(
            agent_name="learning_agent",
            output=snapshot,
            model_name="statistical_python",
            duration_ms=elapsed_ms,
            status="ok",
            input_summary={"preferred_hook": preferences.preferred_hook, "total_shown": total_shown},
            output_summary={
                "recommended_hook_type": snapshot.recommended_hook_type,
                "scores_by_hook": snapshot.scores_by_hook,
                "strategy": snapshot.strategy,
            },
        )

    def record_outcome(
        self,
        *,
        user_id: uuid.UUID,
        hook_type: str,
        outcome: Literal["shown", "accepted", "started", "completed", "rejected"],
    ) -> UserHookStat:
        normalized = hook_type if hook_type in SUPPORTED_MVP_HOOKS else "challenge"
        stat = self._get_or_create_stat(user_id, normalized)
        if outcome == "shown":
            stat.shown_count += 1
        elif outcome == "accepted":
            stat.accepted_count += 1
        elif outcome == "started":
            stat.started_count += 1
        elif outcome == "completed":
            stat.completed_count += 1
        elif outcome == "rejected":
            stat.rejected_count += 1
        self._db.flush()
        return stat
