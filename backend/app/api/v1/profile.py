import uuid
from app.models.entities import SessionEvent
from app.services.profile_dashboard import dashboard, continuation_suggestion
from typing import Literal
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from app.api.deps import get_current_demo_user
from app.core.database import get_db
from app.core.security import verify_demo_write_access
from app.models.entities import User, UserProfile, MomentumChain, ChainLink, ProfilePost, Task, Microtask, Hook, FocusSession
from app.integrations.calendar_provider import DatabaseSyncedCalendarProvider
from app.services.momentum import expire_chains, category
from app.services.technique_advisor import TechniqueAdvisor
from app.services.timing import compute_time_context, PHASE_LABELS
from app.services.reward_engine import RewardEngine

router = APIRouter(prefix="/profile", tags=["Momentum & Profile"])


@router.get("", dependencies=[Depends(verify_demo_write_access)])
def profile(user: User = Depends(get_current_demo_user), db: Session = Depends(get_db)):
    expire_chains(db, user.id)
    chains = list(db.scalars(select(MomentumChain).where(MomentumChain.user_id == user.id)
                           .order_by(MomentumChain.created_at.desc()).limit(30)))
    posts = list(db.scalars(select(ProfilePost).where(ProfilePost.user_id == user.id)
                          .order_by(ProfilePost.created_at.desc()).limit(30)))
    candidates = list(db.scalars(select(ChainLink).where(
        ChainLink.user_id == user.id, ChainLink.status == "completed",
        ChainLink.session_id.is_not(None),
        ~select(ProfilePost.id).where(ProfilePost.link_id == ChainLink.id).exists()
    ).order_by(ChainLink.completed_at.desc()).limit(30)))
    heat = sum(c.heat for c in chains if c.status == "active")
    db.commit()
    return dict(display_name=user.display_name, total_xp=RewardEngine().get_user_total_xp(db, user.id),
                category=category(heat), heat=heat, dashboard=dashboard(db, user.id),
                eligible_sessions=[dict(id=l.session_id, title=db.get(Microtask, l.microtask_id).title,
                                        link_index=l.link_index) for l in candidates],
                chains=[dict(id=c.id, task_id=c.task_id, title=db.get(Task, c.task_id).title,
                             links=c.links_completed, heat=c.heat, status=c.status, expires_at=c.expires_at,
                             suggestion=continuation_suggestion(db.get(Task, c.task_id).title)[0],
                             suggested_target=continuation_suggestion(db.get(Task, c.task_id).title)[1])
                        for c in chains],
                posts=[dict(id=p.id, title=p.task_title, caption=p.caption, evidence_type=p.evidence_type,
                            link_index=p.link_index, image_base64=p.image_base64,
                            media_mime=p.media_mime) for p in posts])


class ContinueRequest(BaseModel):
    target: str = Field(min_length=3, max_length=120)
    minutes: int = Field(default=35, ge=1, le=120)
    technique: str | None = None
    timer_mode: Literal["countdown", "stopwatch"] = "countdown"


@router.post("/chains/{chain_id}/continue", dependencies=[Depends(verify_demo_write_access)])
def continue_chain(chain_id: uuid.UUID, payload: ContinueRequest,
                   user: User = Depends(get_current_demo_user), db: Session = Depends(get_db)):
    db.scalar(select(User).where(User.id == user.id).with_for_update())
    expire_chains(db, user.id)
    chain = db.get(MomentumChain, chain_id)
    if not chain or chain.user_id != user.id:
        raise HTTPException(404, "Enganche no encontrado")
    if chain.status != "active":
        raise HTTPException(409, "La racha terminó. Inicia un nuevo enganche.")
    task = db.get(Task, chain.task_id)
    if task.status == "completed":
        raise HTTPException(409, "Esta actividad ya terminó")
    events = DatabaseSyncedCalendarProvider(db).get_upcoming_events(user.id)
    ctx = compute_time_context(deadline=task.deadline, events=events,
                               timezone_name=user.preferences.timezone if user.preferences else None)
    if ctx.free_window_minutes < (payload.minutes if payload.timer_mode == "countdown" else 1):
        raise HTTPException(409, f"Tienes {ctx.free_window_minutes} min antes de tu próximo evento.")
    previous = db.scalar(select(ChainLink).where(ChainLink.chain_id == chain.id)
                         .order_by(ChainLink.link_index.desc()))
    if previous and previous.status == "started":
        raise HTTPException(409, "Termina o detén el enganche actual primero")
    advisor = TechniqueAdvisor(db)
    rec = advisor.recommend(user_id=user.id, urgency_phase=ctx.urgency_phase,
                            time_bucket=ctx.time_bucket, exclude=previous.technique if previous else None)
    from app.services.technique_advisor import TECHNIQUES
    technique = payload.technique or rec.technique
    if technique not in TECHNIQUES:
        raise HTTPException(422, "Técnica desconocida")
    label = TECHNIQUES[technique][0]
    # Repeated taps reuse the pending proposal; no extra links or rewards.
    if previous and previous.status == "proposed":
        link = previous
        plan = db.scalar(select(SessionEvent).where(SessionEvent.event_type == "MOMENTUM_PLAN",
            SessionEvent.source == str(link.id)))
        timer_mode = plan.event_metadata.get("timer_mode", "countdown") if plan else "countdown"
        micro = db.get(Microtask, link.microtask_id)
        hook = db.scalar(select(Hook).where(Hook.microtask_id == micro.id))
        technique, label = link.technique, TECHNIQUES[link.technique][0]
    else:
        micro = Microtask(task_id=task.id, title=payload.target, description=payload.target,
                          success_criteria=payload.target, estimated_seconds=(payload.minutes if payload.timer_mode == "countdown" else min(120, ctx.free_window_minutes)) * 60,
                          model_name="momentum_rules")
        db.add(micro)
        db.flush()
        hook = Hook(user_id=user.id, microtask_id=micro.id, hook_type="challenge",
                    message=f"{label} · " + ("Cronómetro" if payload.timer_mode == "stopwatch" else f"{payload.minutes} min"), technique=technique,
                    context_key=ctx.context_key, intensity=1, channel="in_app")
        link = ChainLink(chain_id=chain.id, user_id=user.id, microtask_id=micro.id,
                         link_index=(previous.link_index + 1) if previous else 1,
                         technique=technique, context_key=ctx.context_key,
                         target_minutes=payload.minutes, target_units=payload.target)
        db.add_all([hook, link])
        db.flush()
        timer_mode = payload.timer_mode
        db.add(SessionEvent(event_type="MOMENTUM_PLAN", source=str(link.id),
            event_metadata={"timer_mode": timer_mode, "microtask_id": str(micro.id)}))
        advisor.record(user_id=user.id, technique=technique, context_key=ctx.context_key, outcome="shown")
    db.commit()
    return dict(trace_id=f"momentum_{link.id}", task_id=task.id, microtask_id=micro.id, hook_id=hook.id,
                is_simulated_ai=True, timer_mode=timer_mode,
                policy=dict(allowed=True, effective_intensity=1, effective_channel="in_app", audio_allowed=True),
                context=dict(task_id=task.id, task_title=task.title, contextual_summary=PHASE_LABELS[ctx.urgency_phase]),
                microtask=dict(task_id=task.id, title=micro.title, description=micro.description,
                               lcd_short_title=micro.title[:16], steps=[micro.title],
                               estimated_seconds=micro.estimated_seconds, success_criteria=micro.success_criteria),
                hook=dict(message=hook.message, visual_mode_name=label), learning=dict(recommended_technique=technique))


class MomentRequest(BaseModel):
    session_id: uuid.UUID
    evidence_type: Literal["photo", "voice", "story", "tutorial", "vlog", "podcast", "video"]
    caption: str = Field(min_length=3, max_length=200)
    units_completed: int | None = Field(default=None, ge=1, le=10000)
    unit: Literal["diapositivas", "páginas", "ejercicios", "pasos"] = "pasos"
    image_base64: str | None = Field(default=None, max_length=2_000_000)
    media_base64: str | None = Field(default=None, max_length=16_000_000)
    media_mime: Literal["video/mp4", "audio/mp4", "audio/mpeg", "audio/wav", "audio/ogg"] | None = None


@router.post("/moments", dependencies=[Depends(verify_demo_write_access)])
def create_moment(payload: MomentRequest, user: User = Depends(get_current_demo_user), db: Session = Depends(get_db)):
    db.scalar(select(User).where(User.id == user.id).with_for_update())
    link = db.scalar(select(ChainLink).where(ChainLink.session_id == payload.session_id,
                                           ChainLink.user_id == user.id, ChainLink.status == "completed"))
    if not link:
        raise HTTPException(409, "Completa un enganche primero")
    existing = db.scalar(select(ProfilePost).where(ProfilePost.link_id == link.id))
    if existing:
        return dict(id=existing.id, already_saved=True)
    if payload.media_base64 and payload.evidence_type not in ("video", "vlog", "tutorial", "voice", "podcast"):
        raise HTTPException(422, "Selecciona un formato de audio o video")
    if payload.evidence_type in ("video", "vlog", "tutorial", "voice", "podcast"):
        import base64
        import binascii
        try:
            data = base64.b64decode(payload.media_base64 or "", validate=True)
            mime = payload.media_mime or ""
            valid = ((mime in ("video/mp4", "audio/mp4") and data[4:8] == b"ftyp") or
                     (mime == "audio/ogg" and data.startswith(b"OggS")) or
                     (mime == "audio/wav" and data.startswith(b"RIFF") and data[8:12] == b"WAVE") or
                     (mime == "audio/mpeg" and (data.startswith(b"ID3") or data[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"))))
            expected = "video/" if payload.evidence_type in ("video", "vlog", "tutorial") else "audio/"
            if not valid or not mime.startswith(expected) or len(data) > 12_000_000:
                raise ValueError()
        except (ValueError, binascii.Error):
            raise HTTPException(422, "Adjunta audio o video compatible de hasta 12 MB")
    if payload.evidence_type == "photo" or payload.image_base64:
        import base64
        import binascii
        try:
            data = base64.b64decode(payload.image_base64 or "", validate=True)
            if not (data.startswith(b"\xff\xd8\xff") or data.startswith(b"\x89PNG\r\n\x1a\n")):
                raise ValueError()
        except (ValueError, binascii.Error):
            raise HTTPException(422, "Adjunta una foto JPEG o PNG")
    expire_chains(db, user.id)
    chain = db.get(MomentumChain, link.chain_id)
    post = ProfilePost(user_id=user.id, chain_id=chain.id, link_id=link.id,
                       task_title=db.get(Task, chain.task_id).title, link_index=link.link_index,
                       evidence_type=payload.evidence_type, caption=payload.caption,
                       image_base64=payload.image_base64, heat_points=10,
                       media_base64=payload.media_base64, media_mime=payload.media_mime)
    db.add(post)
    if payload.units_completed is not None:
        db.add(SessionEvent(session_id=payload.session_id, event_type="MOMENT_UNITS", source="profile",
                            event_metadata={"count": payload.units_completed, "unit": payload.unit}))
    if chain.status == "active":
        chain.heat += 10
    RewardEngine()._grant_once(db, user_id=user.id, session_id=payload.session_id,
                             reward_type="moment", xp_amount=10, idempotency_key=f"moment:{link.id}")
    db.commit()
    return dict(id=post.id, already_saved=False)


@router.get("/moments/{post_id}/media", dependencies=[Depends(verify_demo_write_access)])
def moment_media(post_id: uuid.UUID, user: User = Depends(get_current_demo_user), db: Session = Depends(get_db)):
    from fastapi.responses import Response
    import base64
    post = db.get(ProfilePost, post_id)
    if not post or post.user_id != user.id or not post.media_base64:
        raise HTTPException(404, "Archivo no encontrado")
    return Response(base64.b64decode(post.media_base64), media_type=post.media_mime,
                    headers={"Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff"})
