from __future__ import annotations

import hmac
from fastapi import Header, HTTPException, status

from app.core.config import Settings, get_settings


def _extract_bearer_or_header(
    authorization: str | None,
    explicit_token: str | None,
) -> str | None:
    if explicit_token and explicit_token.strip():
        return explicit_token.strip()
    if authorization and authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return None


def verify_demo_write_access(
    authorization: str | None = Header(default=None),
    x_demo_token: str | None = Header(default=None, alias="X-Demo-Token"),
) -> str:
    """
    Protect write endpoints and demo operations.
    When DEMO_ACCESS_TOKEN is configured, callers must pass it via Bearer or X-Demo-Token.
    In non-development environments, a non-empty token is mandatory.
    """
    settings: Settings = get_settings()
    expected = settings.DEMO_ACCESS_TOKEN.get_secret_value().strip()

    if not expected:
        if settings.APP_ENV.lower() not in {"development", "test"}:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Write authentication is not configured on the server.",
            )
        return "dev-unauthenticated-demo"

    provided = _extract_bearer_or_header(authorization, x_demo_token)
    if not provided or not hmac.compare_digest(provided, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid access token.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return "authenticated-demo-user"


def verify_device_access(
    authorization: str | None = Header(default=None),
    x_device_token: str | None = Header(default=None, alias="X-Device-Token"),
) -> str:
    """Protect ESP32 / hardware simulator endpoints."""
    settings: Settings = get_settings()
    expected = settings.DEVICE_SHARED_TOKEN.get_secret_value().strip()

    if not expected:
        if settings.APP_ENV.lower() not in {"development", "test"}:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Device authentication is not configured on the server.",
            )
        return "dev-device"

    provided = _extract_bearer_or_header(authorization, x_device_token)
    if not provided or not hmac.compare_digest(provided, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid device token.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return "authenticated-device"
