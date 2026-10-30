from __future__ import annotations

import math
import uuid
from dataclasses import dataclass
from typing import Literal
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.entities import UserTechniqueStat

TechniqueOutcome = Literal["shown", "started", "completed", "verified"]

# key -> (label, short reason, default minutes)
TECHNIQUES: dict[str, tuple[str, str, int]] = {
    "regla_2_min": ("Regla de 2 min", "Algo tan pequeño que no puedes fallar.", 5),
    "sprint_5": ("Sprint", "Ráfaga corta e intensa.", 10),
    "pomodoro_25": ("Pomodoro", "25 min de foco y luego pausa.", 25),
    "contrarreloj": ("Contrarreloj", "Meta concreta en un tiempo que eliges.", 35),
    "eat_the_frog": ("Cómete la rana", "Lo más difícil primero.", 30),
    "body_doubling": ("En vivo", "Hazlo en llamada o grabando un vlog.", 25),
    "teach_back": ("Explícalo", "Graba un mini tutorial de lo que hiciste.", 10),
}

# Prior expected success per urgency phase (cold start before the user generates data)
PHASE_PRIORS: dict[str, dict[str, float]] = {
    "no_deadline": {"regla_2_min": 0.62, "pomodoro_25": 0.52, "teach_back": 0.48, "sprint_5": 0.45},
    "early": {"pomodoro_25": 0.62, "eat_the_frog": 0.58, "teach_back": 0.5, "regla_2_min": 0.46},
    "building": {"pomodoro_25": 0.6, "contrarreloj": 0.56, "sprint_5": 0.5, "body_doubling": 0.46},
    "crunch": {"contrarreloj": 0.66, "sprint_5": 0.6, "body_doubling": 0.52, "pomodoro_25": 0.45},
    "same_day": {"sprint_5": 0.7, "contrarreloj": 0.64, "regla_2_min": 0.56},
    "overdue": {"regla_2_min": 0.66, "sprint_5": 0.62, "contrarreloj": 0.5},
}

BUCKET_ADJUST: dict[str, dict[str, float]] = {
    "morning": {"eat_the_frog": 0.08, "pomodoro_25": 0.03},
    "afternoon": {"pomodoro_25": 0.04, "body_doubling": 0.03},
    "evening": {"contrarreloj": 0.03, "teach_back": 0.03},
    "night": {"regla_2_min": 0.08, "sprint_5": 0.04, "eat_the_frog": -0.1, "pomodoro_25": -0.04},
}

PRIOR_WEIGHT = 2.0
EXPLORATION = 0.16


@dataclass(slots=True)
class TechniqueRecommendation:
    technique: str
    label: str
    reason: str
    default_minutes: int
    scores: dict[str, float]
    context_key: str


def technique_label(key: str | None) -> str:
    return TECHNIQUES.get(key or "", ("Reto", "", 5))[0]


class TechniqueAdvisor:
    """
    Contextual bandit (deterministic, pure Python) learning which productivity technique
    works best for each user per (urgency phase x time of day). Part of the Learning Agent.
    """

    def __init__(self, db: Session) -> None:
        self._db = db

    def _rows(self, user_id: uuid.UUID) -> list[UserTechniqueStat]:
        return list(
            self._db.scalars(
                select(UserTechniqueStat).where(UserTechniqueStat.user_id == user_id)
            ).all()
        )

    @staticmethod
    def _success(row: UserTechniqueStat | None) -> tuple[float, float]:
        if row is None:
            return 0.0, 0.0
        succ = row.completed_count + 0.5 * row.verified_count
        trials = float(max(row.shown_count, row.started_count, row.completed_count))
        return succ, trials

    def recommend(
        self,
        *,
        user_id: uuid.UUID,
        urgency_phase: str,
        time_bucket: str,
        exclude: str | None = None,
    ) -> TechniqueRecommendation:
        context_key = f"{urgency_phase}:{time_bucket}"
        rows = self._rows(user_id)
        by_ctx = {(r.technique, r.context_key): r for r in rows}
        priors = PHASE_PRIORS.get(urgency_phase, PHASE_PRIORS["no_deadline"])
        adjust = BUCKET_ADJUST.get(time_bucket, {})
        total_trials = sum(self._success(r)[1] for r in rows) + len(TECHNIQUES)

        scores: dict[str, float] = {}
        for tech in TECHNIQUES:
            prior = priors.get(tech, 0.32) + adjust.get(tech, 0.0)
            ctx_succ, ctx_trials = self._success(by_ctx.get((tech, context_key)))
            glob_succ = sum(self._success(r)[0] for r in rows if r.technique == tech)
            glob_trials = sum(self._success(r)[1] for r in rows if r.technique == tech)
            ctx_rate = (ctx_succ + prior * PRIOR_WEIGHT) / (ctx_trials + PRIOR_WEIGHT)
            glob_rate = (glob_succ + prior * PRIOR_WEIGHT) / (glob_trials + PRIOR_WEIGHT)
            blended = 0.65 * ctx_rate + 0.35 * glob_rate
            bonus = EXPLORATION * math.sqrt(math.log(total_trials + 1.0) / (ctx_trials + 1.0))
            # Exploration only among techniques that make sense in this phase
            if tech not in priors:
                bonus *= 0.35
            scores[tech] = round(max(0.01, blended + bonus), 4)

        ranked = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)
        best = ranked[0][0]
        if exclude and best == exclude and len(ranked) > 1:
            # Rotate modality in a chain unless the repeated one is clearly better
            if ranked[0][1] - ranked[1][1] < 0.12:
                best = ranked[1][0]

        label, reason, minutes = TECHNIQUES[best]
        return TechniqueRecommendation(
            technique=best,
            label=label,
            reason=reason,
            default_minutes=minutes,
            scores=scores,
            context_key=context_key,
        )

    def record(
        self,
        *,
        user_id: uuid.UUID,
        technique: str | None,
        context_key: str | None,
        outcome: TechniqueOutcome,
    ) -> None:
        if not technique or technique not in TECHNIQUES or not context_key:
            return
        row = self._db.scalar(
            select(UserTechniqueStat).where(
                UserTechniqueStat.user_id == user_id,
                UserTechniqueStat.technique == technique,
                UserTechniqueStat.context_key == context_key,
            )
        )
        if row is None:
            row = UserTechniqueStat(
                user_id=user_id,
                technique=technique,
                context_key=context_key,
                shown_count=0,
                started_count=0,
                completed_count=0,
                verified_count=0,
            )
            self._db.add(row)
        if outcome == "shown":
            row.shown_count += 1
        elif outcome == "started":
            row.started_count += 1
        elif outcome == "completed":
            row.completed_count += 1
        elif outcome == "verified":
            row.verified_count += 1
        self._db.flush()

    def best_context_insight(self, user_id: uuid.UUID) -> tuple[str, str, str] | None:
        """Return (technique, phase, bucket) with the highest observed success, if any data."""
        best: tuple[float, UserTechniqueStat] | None = None
        for row in self._rows(user_id):
            succ, trials = self._success(row)
            if trials < 1:
                continue
            rate = (succ + 1.0) / (trials + 2.0)
            if best is None or rate > best[0]:
                best = (rate, row)
        if best is None:
            return None
        phase, _, bucket = best[1].context_key.partition(":")
        return best[1].technique, phase, bucket
