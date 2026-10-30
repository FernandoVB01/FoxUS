# Flujo Multiagente y Trazabilidad (`agent_traces`)

## Secuencia Determinista del Orquestador

Cada llamada a `POST /api/v1/agents/generate-mission` genera un identificador único `trace_id` (`trc_...`) y ejecuta una tubería lineal auditable sin bucles infinitos:

1. **`context_agent` (`ContextAgent`)**:
   - Ejecuta herramientas internas deterministas sobre PostgreSQL y proveedores locales/externos:
     - `get_user_tasks()`
     - `get_calendar_events()`
     - `get_user_preferences()`
     - `get_recent_sessions()`
   - Calcula `hours_until_deadline`, `urgency_level` y `recent_rejections_for_task` sin permitir que el modelo invente fechas ni eventos.
   - Si `GEMINI_API_KEY` está activa, solicita a `gemma-4-26b-a4b-it` una síntesis empática breve basada únicamente en esos hechos.

2. **`learning_agent` (`LearningAgent`)**:
   - Consulta `user_hook_stats` del usuario y calcula puntuaciones adaptativas (`AdaptiveUcbStrategy`) priorizando **inicio real** y **finalización** sobre simples clics, y penalizando rechazos/posposiciones.
   - Recomienda entre los dos modos interactivos del MVP:
     - `challenge` (**Desafío Relámpago** — FOX Challenge)
     - `fox_bzz` (**La Mosca Traviesa** — FOX BZZ)

3. **`microtask_agent` (`MicrotaskAgent`)**:
   - Invoca `gemma-4-26b-a4b-it` con su prompt de sistema aislado para convertir la tarea original en una microacción física concreta de 60 a 300 segundos (sin divisiones aritméticas).
   - Si detecta `recent_rejections_for_task >= 2` o `perceived_difficulty >= 4`, activa `difficulty_adapted=True` reduciendo el paso a <= 120 segundos.
   - Genera `lcd_short_title` normalizado en ASCII de máximo 16 caracteres para la pantalla LCD 16x2 de la ESP32.

4. **`hook_agent` (`HookAgent`)**:
   - Combina el modo seleccionado con generación contextual en `gemma-4-26b-a4b-it` para redactar el mensaje de FOX, asignar el minijuego de ignición de 30s (`catch_fox` o `spark_combo`), redactar las 3 frases habladas del árbitro (`coach_voice_lines`) y asignar `sound_id` y `animation_id`.

5. **`policy_engine` (`PolicyEngine`)**:
   - Valida límites del esquema, modo silencio (`intervention_level == 0`), ventana de descanso (`quiet_hours_start`..`quiet_hours_end`), *cooldown* entre intervenciones y amortiguación de intensidad.

6. **`orchestrator` (`FoxusOrchestrator`)**:
   - Sincroniza automáticamente Google Tasks / Google Calendar si están vinculados (`auto_sync_google_if_connected`), persiste `Microtask`, `Hook`, los pasos en `agent_traces` y encola `SHOW_MISSION` hacia **FoxusWatch**.

7. **`referee_agent` (`RefereeAgent` — 5.º Agente Especializado Multimodal)**:
   - Se ejecuta al finalizar el reto en `POST /api/v1/sessions/{session_id}/verify-evidence`.
   - Usa la visión nativa y razonamiento de **Google Gemma 4 (`gemma-4-26b-a4b-it`)** para inspeccionar la **foto del avance** (`image/jpeg`) o el **reporte hablado por voz** (`voice_transcript`), emitiendo un veredicto hablado (`referee_speech`) y habilitando el bono determinista de `+15 XP` (`verified_evidence`) en `RewardEngine`.
