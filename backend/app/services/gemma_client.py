from __future__ import annotations

import json
import logging
import re
import time
from dataclasses import dataclass
from typing import Generic, TypeVar
from google import genai
from google.genai import types
from pydantic import BaseModel, ValidationError

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)

_CODE_FENCE_RE = re.compile(r"```(?:json)?\s*(\{.*?\})\s*```", re.DOTALL | re.IGNORECASE)


def extract_json_object(raw_text: str) -> str:
    """
    Extract a JSON object from Gemma text output safely.
    Gemma models do not identically mirror Gemini proprietary structured-output modes
    and may wrap valid JSON in Markdown fences, arrays, or brief preambles.
    """
    cleaned = raw_text.strip()
    if cleaned.startswith("[") and cleaned.endswith("]"):
        try:
            loaded = json.loads(cleaned)
            if isinstance(loaded, list) and loaded and isinstance(loaded[0], dict):
                return json.dumps(loaded[0], ensure_ascii=False)
        except json.JSONDecodeError:
            pass
    if cleaned.startswith("{") and cleaned.endswith("}"):
        return cleaned

    fence_match = _CODE_FENCE_RE.search(cleaned)
    if fence_match:
        return fence_match.group(1).strip()

    first_brace = cleaned.find("{")
    if first_brace == -1:
        raise ValueError("No JSON object opening brace found in model output.")

    depth = 0
    in_string = False
    escape = False
    for idx in range(first_brace, len(cleaned)):
        ch = cleaned[idx]
        if escape:
            escape = False
            continue
        if ch == "\\":
            escape = True
            continue
        if ch == '"':
            in_string = not in_string
            continue
        if not in_string:
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return cleaned[first_brace : idx + 1]

    raise ValueError("Unbalanced JSON braces in model output.")


@dataclass(slots=True)
class GemmaGenerationResult(Generic[T]):
    ok: bool
    parsed: T | None
    model_used: str
    latency_ms: int
    attempts: int
    error_reason: str | None = None
    is_fallback: bool = False


class GemmaClient:
    """
    Reusable Google Gemma 4 client backed by `google-genai` (Gemini API).
    Enforces:
      - Open Gemma model family only (default `gemma-4-26b-a4b-it`)
      - Timeouts, bounded retries, latency tracking
      - Application-validated Pydantic JSON with bounded repair retry
      - Explicit identification of real model vs fallback
      - Zero leakage of GEMINI_API_KEY
    """

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()
        self._model_name = self._settings.GEMMA_MODEL

        if not self._model_name.lower().startswith("gemma-"):
            raise ValueError(f"Forbidden non-Gemma model configured: {self._model_name}")

    @property
    def configured_model(self) -> str:
        return self._model_name

    @property
    def is_configured(self) -> bool:
        return self._settings.has_gemini_api_key

    def _build_sdk_client(self) -> genai.Client:
        api_key = self._settings.GEMINI_API_KEY.get_secret_value().strip()
        timeout_ms = int(self._settings.GEMMA_TIMEOUT_SECONDS * 1000)
        return genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(
                timeout=timeout_ms,
                retry_options=types.HttpRetryOptions(attempts=2, initial_delay=0.6),
            ),
        )

    def _call_model_text(
        self,
        prompt: str,
        temperature: float = 0.35,
        image_bytes: bytes | None = None,
        image_mime_type: str = "image/jpeg",
    ) -> tuple[str, str]:
        client = self._build_sdk_client()
        # Verified empirically with gemma-4-26b-a4b-it on Gemini API:
        # supports native multimodal image bytes + ThinkingLevel.MINIMAL for fast JSON generation.
        config = types.GenerateContentConfig(
            temperature=temperature,
            thinking_config=types.ThinkingConfig(
                thinking_level=types.ThinkingLevel.MINIMAL,
            ),
        )
        contents: object = (
            [types.Part.from_bytes(data=image_bytes, mime_type=image_mime_type), prompt]
            if image_bytes
            else prompt
        )
        response = client.models.generate_content(
            model=self._model_name,
            contents=contents,  # type: ignore[arg-type]
            config=config,
        )
        text_out = (response.text or "").strip()
        if not text_out:
            raise ValueError("Empty response text returned by Gemma.")
        actual_model = getattr(response, "model_version", None) or self._model_name
        # Strip 'models/' prefix if returned by API
        if isinstance(actual_model, str) and actual_model.startswith("models/"):
            actual_model = actual_model[len("models/") :]
        return text_out, str(actual_model)

    def generate_structured(
        self,
        *,
        system_prompt: str,
        user_payload: dict[str, object],
        schema_model: type[T],
        temperature: float = 0.35,
        image_bytes: bytes | None = None,
        image_mime_type: str = "image/jpeg",
    ) -> GemmaGenerationResult[T]:
        start_ns = time.perf_counter_ns()

        if not self.is_configured:
            elapsed_ms = max(1, (time.perf_counter_ns() - start_ns) // 1_000_000)
            return GemmaGenerationResult(
                ok=False,
                parsed=None,
                model_used="deterministic_fallback",
                latency_ms=int(elapsed_ms),
                attempts=0,
                error_reason="GEMINI_API_KEY_NOT_CONFIGURED",
                is_fallback=True,
            )

        fields_spec: dict[str, str] = {}
        for fname, finfo in schema_model.model_fields.items():
            raw_ann = str(finfo.annotation).lower()
            ann_name = getattr(finfo.annotation, "__name__", raw_ann).lower()
            if "list" in raw_ann or "list" in ann_name:
                fields_spec[fname] = "lista JSON de 3 frases breves en espanol (array of strings)"
            elif "bool" in raw_ann or "bool" in ann_name:
                fields_spec[fname] = "booleano JSON true o false (boolean)"
            elif "int" in ann_name:
                fields_spec[fname] = "numero entero (int)"
            else:
                fields_spec[fname] = "texto en espanol (string)"
        fields_json = json.dumps(fields_spec, ensure_ascii=False)
        payload_json = json.dumps(user_payload, ensure_ascii=False, default=str)

        # Plain-text prompt (avoiding XML angle-bracket tags and verbose JSON Schema metadata,
        # which empirically trigger 500 ServerErrors on gemma-4-26b-a4b-it over Gemini API).
        composed_prompt = (
            f"INSTRUCCIONES DE ROL:\n{system_prompt.strip()}\n\n"
            f"DATOS DE ENTRADA (JSON):\n{payload_json}\n\n"
            f"CAMPOS JSON REQUERIDOS:\n{fields_json}\n\n"
            "Responde UNICAMENTE con un objeto JSON valido con exactamente esas claves, "
            "sin explicaciones adicionales."
        )

        max_attempts = 1 + self._settings.GEMMA_MAX_RETRIES
        last_error: str = "UNKNOWN_ERROR"
        current_prompt = composed_prompt
        actual_model_used = self._model_name

        for attempt in range(1, max_attempts + 1):
            try:
                raw_text, actual_model_used = self._call_model_text(
                    current_prompt,
                    temperature=temperature if attempt == 1 else 0.15,
                    image_bytes=image_bytes,
                    image_mime_type=image_mime_type,
                )
                json_candidate = extract_json_object(raw_text)
                validated = schema_model.model_validate_json(json_candidate)
                elapsed_ms = max(1, (time.perf_counter_ns() - start_ns) // 1_000_000)
                return GemmaGenerationResult(
                    ok=True,
                    parsed=validated,
                    model_used=actual_model_used,
                    latency_ms=int(elapsed_ms),
                    attempts=attempt,
                    error_reason=None,
                    is_fallback=False,
                )
            except (ValidationError, ValueError, json.JSONDecodeError) as val_exc:
                last_error = f"INVALID_JSON_OUTPUT:{type(val_exc).__name__}"
                # Bounded repair prompt for next attempt without leaking secrets
                current_prompt = (
                    f"{composed_prompt}\n\n"
                    "Tu intento anterior no cumplió el formato JSON requerido. "
                    "Devuelve EXCLUSIVAMENTE un objeto JSON válido con todos los campos obligatorios."
                )
            except Exception as api_exc:
                # Never log raw exception message if it could include headers/URLs
                last_error = f"API_CALL_FAILED:{type(api_exc).__name__}"
                logger.warning("Gemma invocation attempt %d failed (%s)", attempt, last_error)
                if attempt < max_attempts:
                    time.sleep(0.35 * attempt)

        elapsed_ms = max(1, (time.perf_counter_ns() - start_ns) // 1_000_000)
        return GemmaGenerationResult(
            ok=False,
            parsed=None,
            model_used="deterministic_fallback",
            latency_ms=int(elapsed_ms),
            attempts=max_attempts,
            error_reason=last_error,
            is_fallback=True,
        )
