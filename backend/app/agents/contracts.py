from __future__ import annotations

import unicodedata
import uuid
from dataclasses import dataclass
from typing import Any, Generic, Literal, Protocol, TypeVar
from pydantic import BaseModel, Field

from app.models.entities import Task, UserPreference
from app.schemas.agents import (
    HookProposal,
    LearningSnapshot,
    MicrotaskProposal,
    TaskContext,
)

T = TypeVar("T")


def format_lcd_line(text: str, width: int = 16) -> str:
    """
    Convert UTF-8 Spanish text into clean ASCII safe for HD44780 16x2 I2C LCD displays
    and fit within `width` characters without mid-character corruption.
    """
    normalized = unicodedata.normalize("NFKD", text)
    ascii_only = "".join(
        ch for ch in normalized if not unicodedata.combining(ch) and 32 <= ord(ch) <= 126
    )
    compact = " ".join(ascii_only.split()).strip()
    if not compact:
        return "FOXUS MISSION"[:width]
    if len(compact) <= width:
        return compact
    return compact[:width].rstrip()


@dataclass(slots=True)
class AgentExecutionStep(Generic[T]):
    agent_name: str
    output: T
    model_name: str | None
    duration_ms: int
    status: Literal["ok", "fallback", "blocked", "error"]
    input_summary: dict[str, Any]
    output_summary: dict[str, Any]


# Internal Pydantic schemas strictly for Gemma JSON parsing (validated before building domain contracts)
class GemmaContextInterpretation(BaseModel):
    contextual_summary: str = Field(min_length=8, max_length=360)


class GemmaMicrotaskDraft(BaseModel):
    title: str = Field(min_length=4, max_length=140)
    description: str = Field(min_length=10, max_length=600)
    steps: list[str] = Field(default_factory=list)
    estimated_seconds: int = Field(ge=30, le=900)
    success_criteria: str = Field(min_length=4, max_length=320)
    rationale: str = Field(min_length=4, max_length=380)


class GemmaHookDraft(BaseModel):
    message: str = Field(min_length=6, max_length=260)
    coach_voice_lines: list[str] = Field(default_factory=list)
    intensity: int = Field(default=2, ge=1, le=5)
    suggested_delay_seconds: int = Field(default=0, ge=0, le=300)


class GemmaRefereeDraft(BaseModel):
    verified: bool
    confidence_score: int = Field(default=85, ge=0, le=100)
    referee_speech: str = Field(min_length=6, max_length=320)
    detected_progress: str = Field(min_length=4, max_length=320)
    next_tip: str = Field(min_length=4, max_length=260)


# Separate, role-isolated system prompts for each generative agent (Section 5)
CONTEXT_AGENT_SYSTEM_PROMPT = """
Eres el CONTEXT AGENT de FOXUS, un compañero anti-procrastinación para estudiantes.
Tu único rol es resumir en 1 o 2 oraciones claras y empáticas (en español) el estado actual de la tarea del usuario usando EXCLUSIVAMENTE los datos reales proporcionados en DATOS DE ENTRADA.
REGLAS ESTRICTAS:
1. PROHIBIDO inventar fechas límite, eventos de calendario, horarios o datos no presentes en DATOS DE ENTRADA.
2. Si `deadline` es null, no menciones ninguna fecha límite inventada.
3. Identifica el principal obstáculo psicológico probable (tarea grande, cercanía de entrega o rechazos previos) con tono constructivo.
""".strip()

MICROTASK_AGENT_SYSTEM_PROMPT = """
Eres el MICROTASK AGENT de FOXUS (impulsado por Google Gemma 4).
Tu misión es transformar una tarea académica o personal intimidante en UNA SOLA microacción física e inmediata que elimine la fricción de empezar, desglosada en 3 viñetas cortas (`steps`).
REGLAS ESTRICTAS:
1. Propón una acción concreta y verificable de entre 60 y 300 segundos (ej. 180 segundos = 3 minutos).
2. En `steps` devuelve exactamente 3 pasos breves tipo viñeta (máximo 65 caracteres cada uno, sin numeración inicial) fáciles de escanear en 2 segundos.
3. PROHIBIDO usar instrucciones vagas ("avanza un poco", "concéntrate", "investiga el tema").
4. PROHIBIDO dividir aritméticamente ("haz 3 de las 12 diapositivas"). Enfócate en el primer paso físico que rompe la inercia (ej. ["Abre el archivo de tu presentación", "Crea solo la diapositiva de portada", "Escribe el título provisional y guarda"]).
5. Si `recent_rejections_for_task` >= 2 o `perceived_difficulty` >= 4, reduce radicalmente la dificultad a un paso de 90-120 segundos imposible de fallar.
6. Escribe siempre en español claro y directo.
""".strip()

HOOK_AGENT_SYSTEM_PROMPT = """
Eres el HOOK AGENT de FOXUS (impulsado por Google Gemma 4).
Tu rol es redactar el mensaje breve de intervención de FOX (un zorro geométrico astuto, cálido y motivador) y 3 frases cortas habladas (`coach_voice_lines`) para acompañarlo en voz alta durante el reto:
- Si `selected_hook_type` es "challenge" (Desafío Relámpago): tono de árbitro deportivo energético y alentador.
- Si `selected_hook_type` es "fox_bzz" (La Mosca Traviesa): tono juguetón, ligero y cómplice (empezando con "¡Bzz!").
REGLAS ESTRICTAS:
1. Máximo 160 caracteres para `message`.
2. En `coach_voice_lines` devuelve exactamente 3 frases habladas cortas (máximo 90 caracteres cada una): [frase 1 de arranque con silbato, frase 2 de ánimo a mitad del reto, frase 3 de cierre en los últimos 30 segundos].
3. Menciona la microacción concreta propuesta sin culpabilizar al usuario.
4. Escribe en español natural listo para síntesis de voz.
""".strip()

REFEREE_AGENT_SYSTEM_PROMPT = """
Eres el REFEREE AGENT (Árbitro y Verificador Multimodal) de FOXUS, impulsado por Google Gemma 4.
El estudiante acaba de terminar un reto corto de enfoque y envió una evidencia (`photo` de su pantalla/cuaderno o `voice` explicando qué hizo).
Tu misión es actuar como un árbitro justo, entusiasta y observador:
1. Si recibes una imagen o transcripción de voz coherente con haber iniciado o avanzado en `task_title` / `microtask_title`, marca `verified` = true y `confidence_score` entre 70 y 98.
2. Si la imagen está completamente en negro/vacía sin relación alguna o la voz dice explícitamente que no hizo nada, marca `verified` = false con empatía (sin regañar) e invítalo a intentar un paso aún más pequeño.
3. En `detected_progress` describe brevemente qué observaste en la foto o en su reporte de voz.
4. En `referee_speech` redacta tu veredicto hablado de árbitro FOX (máximo 180 caracteres, enérgico y natural para leer en voz alta).
5. En `next_tip` sugiere en 1 oración corta cuál sería el siguiente micro-paso opcional.
""".strip()


class ContextAgentProtocol(Protocol):
    def analyze(
        self,
        *,
        user_id: uuid.UUID,
        task: Task,
        preferences: UserPreference,
        available_minutes: int,
        perceived_difficulty: int | None,
    ) -> AgentExecutionStep[TaskContext]: ...


class MicrotaskAgentProtocol(Protocol):
    def propose(self, *, context: TaskContext) -> AgentExecutionStep[MicrotaskProposal]: ...


class HookAgentProtocol(Protocol):
    def compose(
        self,
        *,
        context: TaskContext,
        microtask: MicrotaskProposal,
        preferences: UserPreference,
        learning: LearningSnapshot,
        requested_mode: Literal["auto", "challenge", "fox_bzz"],
    ) -> AgentExecutionStep[HookProposal]: ...


class LearningAgentProtocol(Protocol):
    def recommend(
        self, *, user_id: uuid.UUID, preferences: UserPreference
    ) -> AgentExecutionStep[LearningSnapshot]: ...
