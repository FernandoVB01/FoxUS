from __future__ import annotations

from datetime import UTC, datetime, timedelta
from urllib.parse import urlencode
from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import HTMLResponse
import httpx
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.deps import get_current_demo_user
from app.core.config import Settings, get_settings
from app.core.database import get_db
from app.core.security import verify_demo_write_access
from app.integrations.calendar_provider import GoogleCalendarProvider, ICalFeedCalendarProvider
from app.integrations.tasks_provider import GoogleTasksProvider
from app.models.entities import ExternalIntegration, Task, User
from app.schemas.agents import CalendarEventItem

router = APIRouter(prefix="/integrations", tags=["Google Tasks & Calendar"])

GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
GOOGLE_READONLY_SCOPES = (
    "openid email "
    "https://www.googleapis.com/auth/tasks.readonly "
    "https://www.googleapis.com/auth/calendar.readonly"
)


class GoogleStatusResponse(BaseModel):
    oauth_configured_on_server: bool
    connected: bool
    auth_mode: str = "none"
    connected_email: str | None = None
    has_calendar_ical: bool = False
    synced_google_tasks_count: int = 0
    upcoming_events_count: int = 0
    upcoming_events: list[CalendarEventItem] = Field(default_factory=list)
    last_synced_at: datetime | None = None


class GoogleAuthUrlResponse(BaseModel):
    oauth_configured: bool
    auth_url: str | None = None
    setup_hint: str


class GoogleConnectTokenRequest(BaseModel):
    access_token: str = Field(min_length=12, max_length=4096)
    calendar_ical_url: str | None = Field(default=None, max_length=2048)


class GoogleConnectIcalRequest(BaseModel):
    calendar_ical_url: str = Field(min_length=12, max_length=2048)


class GoogleSyncResponse(BaseModel):
    ok: bool
    imported_tasks: int = 0
    updated_tasks: int = 0
    synced_events: int = 0
    upcoming_events: list[CalendarEventItem] = Field(default_factory=list)
    detail: str


def _get_or_create_google_integration(db: Session, user_id) -> ExternalIntegration:
    integ = db.scalar(
        select(ExternalIntegration).where(
            ExternalIntegration.user_id == user_id,
            ExternalIntegration.provider == "google",
        )
    )
    if integ is None:
        integ = ExternalIntegration(
            user_id=user_id,
            provider="google",
            auth_mode="none",
            cached_events_json=[],
        )
        db.add(integ)
        db.flush()
    return integ


def _maybe_refresh_oauth_token(integ: ExternalIntegration, settings: Settings) -> str | None:
    if not integ.access_token:
        return None
    now = datetime.now(UTC)
    exp = integ.token_expires_at
    if exp is not None:
        exp_utc = exp if exp.tzinfo else exp.replace(tzinfo=UTC)
        if exp_utc <= now + timedelta(seconds=60) and integ.refresh_token and settings.GOOGLE_CLIENT_ID:
            try:
                with httpx.Client(timeout=10.0) as client:
                    resp = client.post(
                        GOOGLE_TOKEN_URL,
                        data={
                            "client_id": settings.GOOGLE_CLIENT_ID,
                            "client_secret": settings.GOOGLE_CLIENT_SECRET.get_secret_value(),
                            "refresh_token": integ.refresh_token,
                            "grant_type": "refresh_token",
                        },
                    )
                    if resp.status_code == 200:
                        tok = resp.json()
                        integ.access_token = tok.get("access_token", integ.access_token)
                        expires_in = int(tok.get("expires_in", 3500))
                        integ.token_expires_at = now + timedelta(seconds=expires_in)
            except Exception:
                pass
    return integ.access_token


def _execute_google_sync(
    db: Session,
    user: User,
    integ: ExternalIntegration,
    settings: Settings,
) -> GoogleSyncResponse:
    imported_tasks = 0
    updated_tasks = 0
    events: list[CalendarEventItem] = []
    warnings: list[str] = []

    token = _maybe_refresh_oauth_token(integ, settings)

    # 1. Sync Google Tasks (read-only import; never marks external tasks completed per Section 12)
    if token:
        try:
            gtasks = GoogleTasksProvider(oauth_access_token=token).list_pending_tasks(user.id)
            for ext in gtasks:
                existing = db.scalar(
                    select(Task).where(
                        Task.user_id == user.id,
                        Task.source == "google_tasks",
                        Task.external_id == ext.external_id,
                    )
                )
                if existing is None:
                    db.add(
                        Task(
                            user_id=user.id,
                            title=ext.title,
                            description=ext.description,
                            deadline=ext.deadline,
                            needs_pressure=bool(ext.deadline is not None),
                            source="google_tasks",
                            external_id=ext.external_id,
                            status="pending",
                        )
                    )
                    imported_tasks += 1
                else:
                    existing.title = ext.title
                    existing.description = ext.description
                    existing.deadline = ext.deadline
                    updated_tasks += 1
        except httpx.HTTPStatusError as exc:
            warnings.append(f"Google Tasks HTTP {exc.response.status_code}")
        except Exception:
            warnings.append("No se pudo leer Google Tasks")

        # 2. Sync Google Calendar via OAuth API v3
        try:
            cal_events = GoogleCalendarProvider(oauth_access_token=token).get_upcoming_events(
                user.id, horizon_hours=72
            )
            events.extend(cal_events)
        except httpx.HTTPStatusError as exc:
            warnings.append(f"Google Calendar API HTTP {exc.response.status_code}")
        except Exception:
            warnings.append("No se pudo leer Google Calendar API")

    # 3. Also sync optional `.ics` Google Calendar URL (works without any GCP OAuth client!)
    if integ.calendar_ical_url:
        try:
            ics_events = ICalFeedCalendarProvider(integ.calendar_ical_url).get_upcoming_events(
                user.id, horizon_hours=72
            )
            seen_ids = {e.id for e in events}
            for ev in ics_events:
                if ev.id not in seen_ids:
                    events.append(ev)
                    seen_ids.add(ev.id)
        except Exception:
            warnings.append("No se pudo descargar el enlace iCal (.ics)")

    events.sort(key=lambda e: e.start_at)
    integ.cached_events_json = [e.model_dump(mode="json") for e in events[:25]]
    integ.last_synced_at = datetime.now(UTC)
    db.commit()

    summary = (
        f"Sincronizado: +{imported_tasks} tareas nuevas ({updated_tasks} actualizadas) "
        f"y {len(events)} eventos próximos."
    )
    if warnings:
        summary += f" Avisos: {'; '.join(warnings)}."

    return GoogleSyncResponse(
        ok=len(warnings) == 0 or imported_tasks > 0 or len(events) > 0,
        imported_tasks=imported_tasks,
        updated_tasks=updated_tasks,
        synced_events=len(events),
        upcoming_events=events[:10],
        detail=summary,
    )


@router.get("/google/status", response_model=GoogleStatusResponse)
def get_google_status(
    user: User = Depends(get_current_demo_user),
    settings: Settings = Depends(get_settings),
    db: Session = Depends(get_db),
) -> GoogleStatusResponse:
    integ = db.scalar(
        select(ExternalIntegration).where(
            ExternalIntegration.user_id == user.id,
            ExternalIntegration.provider == "google",
        )
    )
    gtasks_count = int(
        db.scalar(
            select(func.count(Task.id)).where(
                Task.user_id == user.id,
                Task.source == "google_tasks",
            )
        )
        or 0
    )

    oauth_ready = bool(
        settings.GOOGLE_CLIENT_ID.strip()
        and settings.GOOGLE_CLIENT_SECRET.get_secret_value().strip()
    )
    if integ is None or integ.auth_mode == "none":
        return GoogleStatusResponse(
            oauth_configured_on_server=oauth_ready,
            connected=False,
            auth_mode="none",
            synced_google_tasks_count=gtasks_count,
        )

    upcoming: list[CalendarEventItem] = []
    for raw in integ.cached_events_json or []:
        try:
            upcoming.append(CalendarEventItem.model_validate(raw))
        except Exception:
            continue

    return GoogleStatusResponse(
        oauth_configured_on_server=oauth_ready,
        connected=True,
        auth_mode=integ.auth_mode,
        connected_email=integ.connected_email,
        has_calendar_ical=bool(integ.calendar_ical_url),
        synced_google_tasks_count=gtasks_count,
        upcoming_events_count=len(upcoming),
        upcoming_events=upcoming[:8],
        last_synced_at=integ.last_synced_at,
    )


@router.get("/google/auth-url", response_model=GoogleAuthUrlResponse)
def get_google_oauth_url(
    settings: Settings = Depends(get_settings),
) -> GoogleAuthUrlResponse:
    client_id = settings.GOOGLE_CLIENT_ID.strip()
    client_secret = settings.GOOGLE_CLIENT_SECRET.get_secret_value().strip()
    if not client_id or not client_secret:
        return GoogleAuthUrlResponse(
            oauth_configured=False,
            auth_url=None,
            setup_hint=(
                "Para OAuth 1-clic agrega GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET en backend/.env, "
                "o conecta al instante abajo pegando tu enlace iCal (.ics) de Google Calendar o un "
                "Access Token de Google OAuth Playground."
            ),
        )

    params = {
        "client_id": client_id,
        "redirect_uri": settings.GOOGLE_REDIRECT_URI,
        "response_type": "code",
        "scope": GOOGLE_READONLY_SCOPES,
        "access_type": "offline",
        "prompt": "consent",
    }
    return GoogleAuthUrlResponse(
        oauth_configured=True,
        auth_url=f"{GOOGLE_AUTH_URL}?{urlencode(params)}",
        setup_hint="Abre el enlace para autorizar lectura de Google Tasks y Google Calendar.",
    )


@router.get("/google/callback", response_class=HTMLResponse)
def google_oauth_callback(
    code: str | None = Query(default=None),
    error: str | None = Query(default=None),
    user: User = Depends(get_current_demo_user),
    settings: Settings = Depends(get_settings),
    db: Session = Depends(get_db),
) -> HTMLResponse:
    if error or not code:
        return HTMLResponse(
            content=f"<html><body style='background:#0B1720;color:#F5EBD7;font-family:sans-serif;padding:32px'>"
            f"<h2>Autorización cancelada</h2><p>{error or 'No se recibió código OAuth.'}</p></body></html>",
            status_code=400,
        )

    with httpx.Client(timeout=12.0) as client:
        tok_resp = client.post(
            GOOGLE_TOKEN_URL,
            data={
                "code": code,
                "client_id": settings.GOOGLE_CLIENT_ID.strip(),
                "client_secret": settings.GOOGLE_CLIENT_SECRET.get_secret_value().strip(),
                "redirect_uri": settings.GOOGLE_REDIRECT_URI,
                "grant_type": "authorization_code",
            },
        )
        if tok_resp.status_code != 200:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="No se pudo intercambiar el código OAuth con Google.",
            )
        tok_data = tok_resp.json()
        access_token = tok_data.get("access_token", "")
        refresh_token = tok_data.get("refresh_token")
        expires_in = int(tok_data.get("expires_in", 3500))

        email: str | None = None
        try:
            u_resp = client.get(
                GOOGLE_USERINFO_URL,
                headers={"Authorization": f"Bearer {access_token}"},
            )
            if u_resp.status_code == 200:
                email = u_resp.json().get("email")
        except Exception:
            pass

    integ = _get_or_create_google_integration(db, user.id)
    integ.auth_mode = "oauth"
    integ.connected_email = email
    integ.access_token = access_token
    if refresh_token:
        integ.refresh_token = refresh_token
    integ.token_expires_at = datetime.now(UTC) + timedelta(seconds=expires_in)
    sync_res = _execute_google_sync(db, user, integ, settings)

    return HTMLResponse(
        content=(
            "<html><body style='background:#0B1720;color:#F5EBD7;font-family:sans-serif;"
            "text-align:center;padding:40px'>"
            "<h1 style='color:#F58220'>🦊 ¡FOXUS conectado con Google!</h1>"
            f"<p>{sync_res.detail}</p>"
            "<p style='color:#F8B62D'>Ya puedes regresar a la aplicación FOXUS.</p>"
            "</body></html>"
        )
    )


@router.post("/google/connect-token", response_model=GoogleSyncResponse)
def connect_google_access_token(
    payload: GoogleConnectTokenRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    settings: Settings = Depends(get_settings),
    db: Session = Depends(get_db),
) -> GoogleSyncResponse:
    clean_tok = payload.access_token.strip()
    if clean_tok.lower().startswith("bearer "):
        clean_tok = clean_tok[7:].strip()

    if clean_tok.startswith("AIza"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                "Pegaste una API Key (AIza...), pero Google Tasks exige un OAuth Access Token "
                "(empieza con ya29.) porque tus tareas son privadas. "
                "Genera uno en https://developers.google.com/oauthplayground."
            ),
        )

    email: str | None = None
    try:
        with httpx.Client(timeout=8.0) as client:
            u_resp = client.get(
                GOOGLE_USERINFO_URL,
                headers={"Authorization": f"Bearer {clean_tok}"},
            )
            if u_resp.status_code == 200:
                email = u_resp.json().get("email")
    except Exception:
        pass

    integ = _get_or_create_google_integration(db, user.id)
    integ.auth_mode = "token"
    integ.access_token = clean_tok
    integ.connected_email = email or integ.connected_email
    if payload.calendar_ical_url:
        integ.calendar_ical_url = payload.calendar_ical_url.strip()
    return _execute_google_sync(db, user, integ, settings)


@router.post("/google/connect-ical", response_model=GoogleSyncResponse)
def connect_google_calendar_ical(
    payload: GoogleConnectIcalRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    settings: Settings = Depends(get_settings),
    db: Session = Depends(get_db),
) -> GoogleSyncResponse:
    url = payload.calendar_ical_url.strip()
    if not (url.startswith("https://") or url.startswith("http://")):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Ingresa una URL válida http(s):// de formato iCal (.ics).",
        )
    integ = _get_or_create_google_integration(db, user.id)
    if integ.auth_mode == "none":
        integ.auth_mode = "ical"
    integ.calendar_ical_url = url
    return _execute_google_sync(db, user, integ, settings)


def auto_sync_google_if_connected(
    db: Session,
    user: User,
    settings: Settings | None = None,
    min_interval_seconds: int = 25,
) -> bool:
    """
    Non-blocking silent auto-sync for Google Tasks & Calendar when opening the app
    or evaluating background Smart Pressure. Respects a short cooldown so rapid calls
    do not rate-limit Google APIs.
    """
    try:
        integ = db.scalar(
            select(ExternalIntegration).where(
                ExternalIntegration.user_id == user.id,
                ExternalIntegration.provider == "google",
            )
        )
        if integ is None:
            return False
        if not integ.access_token and not integ.calendar_ical_url:
            return False

        now = datetime.now(UTC)
        if integ.last_synced_at is not None and min_interval_seconds > 0:
            last_utc = (
                integ.last_synced_at
                if integ.last_synced_at.tzinfo
                else integ.last_synced_at.replace(tzinfo=UTC)
            )
            if (now - last_utc).total_seconds() < min_interval_seconds:
                return False

        cfg = settings or get_settings()
        _execute_google_sync(db, user, integ, cfg)
        return True
    except Exception:
        return False


@router.post("/google/sync", response_model=GoogleSyncResponse)
def sync_google_now(
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    settings: Settings = Depends(get_settings),
    db: Session = Depends(get_db),
) -> GoogleSyncResponse:
    integ = _get_or_create_google_integration(db, user.id)
    if integ.auth_mode == "none" and not integ.access_token and not integ.calendar_ical_url:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Conecta primero Google OAuth, un Access Token o una URL iCal (.ics).",
        )
    return _execute_google_sync(db, user, integ, settings)


@router.delete("/google")
def disconnect_google(
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    integ = db.scalar(
        select(ExternalIntegration).where(
            ExternalIntegration.user_id == user.id,
            ExternalIntegration.provider == "google",
        )
    )
    if integ is not None:
        db.delete(integ)
        db.commit()
    return {"status": "disconnected"}
