from __future__ import annotations

from datetime import UTC, datetime
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.models.entities import Hook, RewardTransaction, UserPreference
from app.schemas.agents import HookProposal, MicrotaskProposal, PolicyDecision


def _parse_hhmm(value: str | None) -> tuple[int, int] | None:
    if not value or ":" not in value:
        return None
    parts = value.strip().split(":")
    if len(parts) != 2:
        return None
    try:
        hour, minute = int(parts[0]), int(parts[1])
        if 0 <= hour <= 23 and 0 <= minute <= 59:
            return hour, minute
    except ValueError:
        return None
    return None


def is_within_quiet_hours(
    now_utc: datetime,
    quiet_start: str | None,
    quiet_end: str | None,
    timezone_name: str,
) -> bool:
    start_hm = _parse_hhmm(quiet_start)
    end_hm = _parse_hhmm(quiet_end)
    if not start_hm or not end_hm:
        return False

    try:
        tz = ZoneInfo(timezone_name)
    except ZoneInfoNotFoundError:
        tz = UTC

    local_dt = now_utc.astimezone(tz)
    now_minutes = local_dt.hour * 60 + local_dt.minute
    start_minutes = start_hm[0] * 60 + start_hm[1]
    end_minutes = end_hm[0] * 60 + end_hm[1]

    if start_minutes == end_minutes:
        return False
    if start_minutes < end_minutes:
        return start_minutes <= now_minutes < end_minutes
    # Overnight window (e.g., 23:00 -> 07:00)
    return now_minutes >= start_minutes or now_minutes < end_minutes


class PolicyEngine:
    """
    Independent, deterministic Policy Engine (Section 4.6).
    Validates permissions, quiet hours, cooldowns, hook repetition, intensity caps,
    duplicate reward safeguards, and AI-generated proposal bounds.
    """

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()

    def evaluate_intervention(
        self,
        db: Session,
        *,
        preferences: UserPreference,
        microtask: MicrotaskProposal,
        hook: HookProposal,
        now_utc: datetime | None = None,
        ignore_cooldown_for_demo: bool = False,
        explicit_user_request: bool = True,
    ) -> PolicyDecision:
        now = now_utc or datetime.now(UTC)
        reasons: list[str] = []

        # 1. Validate Gemma/Agent output bounds deterministically
        if not microtask.title.strip() or microtask.estimated_seconds < 30:
            return PolicyDecision(
                allowed=False,
                effective_intensity=0,
                effective_channel="none",
                audio_allowed=False,
                reasons=["INVALID_MICROTASK_BOUNDS"],
            )
        if not hook.message.strip():
            return PolicyDecision(
                allowed=False,
                effective_intensity=0,
                effective_channel="none",
                audio_allowed=False,
                reasons=["EMPTY_HOOK_MESSAGE"],
            )

        # 2. Check silence / sleep mode (intervention_level == 0)
        if preferences.intervention_level <= 0:
            return PolicyDecision(
                allowed=False,
                effective_intensity=0,
                effective_channel="none",
                audio_allowed=False,
                reasons=["INTERVENTIONS_SILENCED_BY_USER"],
            )

        # 3. Quiet hours policy
        in_quiet_hours = is_within_quiet_hours(
            now_utc=now,
            quiet_start=preferences.quiet_hours_start,
            quiet_end=preferences.quiet_hours_end,
            timezone_name=preferences.timezone,
        )
        if in_quiet_hours and not explicit_user_request:
            return PolicyDecision(
                allowed=False,
                effective_intensity=0,
                effective_channel="none",
                audio_allowed=False,
                reasons=["QUIET_HOURS_ACTIVE"],
            )
        if in_quiet_hours and explicit_user_request:
            reasons.append("QUIET_HOURS_MUTED_AUDIO")

        # 4. Intervention frequency / cooldown check
        recent_hooks = list(
            db.scalars(
                select(Hook)
                .where(Hook.user_id == preferences.user_id)
                .order_by(Hook.created_at.desc())
                .limit(max(5, self._settings.MAX_RECENT_IDENTICAL_HOOKS + 1))
            ).all()
        )
        if recent_hooks and not ignore_cooldown_for_demo:
            latest_created = recent_hooks[0].created_at
            if latest_created.tzinfo is None:
                latest_created = latest_created.replace(tzinfo=UTC)
            elapsed = (now - latest_created).total_seconds()
            cooldown = self._settings.MIN_INTERVENTION_COOLDOWN_SECONDS
            if elapsed < cooldown:
                remaining = max(1, int(cooldown - elapsed))
                return PolicyDecision(
                    allowed=False,
                    effective_intensity=0,
                    effective_channel="none",
                    audio_allowed=False,
                    reasons=["COOLDOWN_ACTIVE"],
                    cooldown_remaining_seconds=remaining,
                )

        # 5. Repetitive hook check (soften intensity if same hook repeated consecutively)
        effective_intensity = min(hook.intensity, preferences.intervention_level)
        max_identical = self._settings.MAX_RECENT_IDENTICAL_HOOKS
        if len(recent_hooks) >= max_identical and all(
            h.hook_type == hook.hook_type for h in recent_hooks[:max_identical]
        ):
            effective_intensity = max(1, effective_intensity - 1)
            reasons.append("REPETITIVE_HOOK_INTENSITY_DAMPENED")

        audio_allowed = bool(preferences.audio_enabled) and not in_quiet_hours
        if not reasons:
            reasons.append("POLICY_APPROVED")

        return PolicyDecision(
            allowed=True,
            effective_intensity=effective_intensity,
            effective_channel=hook.channel,
            audio_allowed=audio_allowed,
            reasons=reasons,
            cooldown_remaining_seconds=0,
        )

    def can_grant_reward(self, db: Session, *, idempotency_key: str) -> bool:
        existing = db.scalar(
            select(RewardTransaction.id).where(
                RewardTransaction.idempotency_key == idempotency_key
            )
        )
        return existing is None
