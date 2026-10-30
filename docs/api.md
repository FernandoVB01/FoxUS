# Referencia de la API REST (`/api/v1`)

La documentación interactiva OpenAPI se genera automáticamente al iniciar FastAPI en:

- Swagger UI: `http://localhost:8000/docs`
- OpenAPI JSON: `http://localhost:8000/api/v1/openapi.json`

## Autenticación en MVP

- **Operaciones de escritura de usuario/demo:** Cabecera `X-Demo-Token: <DEMO_ACCESS_TOKEN>` o `Authorization: Bearer <DEMO_ACCESS_TOKEN>`.
- **Operaciones de dispositivo ESP32:** Cabecera `X-Device-Token: <DEVICE_SHARED_TOKEN>` o `Authorization: Bearer <DEVICE_SHARED_TOKEN>`.

## Endpoints Principales

### Sistema
- `GET /api/v1/health` — Estado del servicio, verificación real `SELECT 1` en PostgreSQL y estado del cliente Gemma 4.
- `GET /api/v1/config/public` — Reglas públicas de XP, modos disponibles y modelo Gemma configurado.

### Tareas
- `GET /api/v1/tasks` — Lista tareas del usuario y sus microacciones generadas (priorizando proyectos con `needs_pressure = true`).
- `POST /api/v1/tasks` — Crea una tarea manual (incluyendo `needs_pressure` y `deadline`).
- `GET /api/v1/tasks/{task_id}` — Detalle de tarea con microacciones.
- `PATCH /api/v1/tasks/{task_id}` — Actualiza el consentimiento de presión (`needs_pressure`), `deadline` o estado.

### Google Tasks y Google Calendar (Solo Lectura — Sección 12)
- `GET /api/v1/integrations/google/status` — Estado de conexión, tareas importadas y eventos próximos de Google Calendar.
- `GET /api/v1/integrations/google/auth-url` — Genera URL OAuth 2.0 oficial de Google (`tasks.readonly`, `calendar.readonly`).
- `GET /api/v1/integrations/google/callback` — Callback OAuth 2.0 que guarda tokens en PostgreSQL y ejecuta sincronización inicial.
- `POST /api/v1/integrations/google/connect-token` — Conexión inmediata mediante Access Token OAuth 2.0 (ej. OAuth Playground).
- `POST /api/v1/integrations/google/connect-ical` — Conexión sin llaves API mediante enlace secreto iCal (`.ics`) de Google Calendar.
- `POST /api/v1/integrations/google/sync` — Sincroniza bajo demanda Google Tasks y Google Calendar sin marcar tareas externas como completadas.
- `DELETE /api/v1/integrations/google` — Desconecta la cuenta Google.

### Agentes Inteligentes
- `POST /api/v1/agents/generate-mission` — Ejecuta `ContextAgent` -> `LearningAgent` -> `MicrotaskAgent` -> `HookAgent` -> `PolicyEngine`, genera micro-retos en viñetas (`steps`), persiste trazas y despacha comando a ESP32.
- `POST /api/v1/agents/evaluate-pressure` — Evaluador proactivo estilo Duolingo que combina urgencia de entrega, `needs_pressure` y aprendizaje estadístico de `LearningAgent` para decidir si amerita interrumpir.
- `GET /api/v1/agents/traces` — Últimos pasos registrados de ejecución multiagente.
- `GET /api/v1/agents/traces/{trace_id}` — Línea de tiempo completa de una misión con `used_real_gemma` y latencias por agente.

### Sesiones y Recompensas
- `POST /api/v1/sessions/start` — Inicia sesión válida (+10 XP idempotente por microacción).
- `POST /api/v1/sessions/{session_id}/complete` — Registra finalización autorreportada (+20 XP + 10 XP opcional por continuidad voluntaria).
- `POST /api/v1/sessions/{session_id}/stop` — Detiene, pospone o silencia la sesión y alimenta al `LearningAgent`.
- `POST /api/v1/sessions/hooks/{hook_id}/dismiss` — Registra posposición o silenciamiento de un hook (ej. **FOX BZZ** o **FOX Challenge**) antes de iniciar sesión, persistiendo el evento en PostgreSQL y actualizando `LearningAgent`.
- `GET /api/v1/sessions/history` — Historial reciente de sesiones.
- `GET /api/v1/rewards/profile` — XP total, nivel, progreso, logros y estadísticas por hook.
- `GET /api/v1/rewards/history` — Transacciones de XP auditables.

### Preferencias y Dispositivos ESP32
- `GET /api/v1/preferences` / `PATCH /api/v1/preferences` — Ajustes de intensidad, audio, música y horario silencioso.
- `GET /api/v1/devices` — Dispositivos asociados al usuario.
- `POST /api/v1/devices/register` — Registro/reconexión de ESP32 o simulador.
- `GET /api/v1/devices/{device_id}/commands/next` — Polling ligero de comandos pendientes (`lcd_line_1`, `lcd_line_2` de <= 16 caracteres ASCII).
- `POST /api/v1/devices/{device_id}/events` — Eventos físicos de botones (`BUTTON_ACCEPT`, `BUTTON_POSTPONE`, `BUTTON_COMPLETE`, `COMMAND_ACK`).
- `POST /api/v1/devices/{device_id}/heartbeat` — Reporte periódico de estado.
- `POST /api/v1/demo/reset` — Reinicio limpio de estado de demostración (solo activo con `DEMO_MODE=true`).
