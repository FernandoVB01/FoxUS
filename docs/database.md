# Modelo Relacional PostgreSQL y Migraciones Alembic

## Stack de Persistencia

- **Motor:** PostgreSQL 16+ (Supabase PostgreSQL mediante Session Pooler IPv4 en la nube; contenedor `postgres:16-alpine` mediante `docker-compose.yml` para desarrollo offline).
- **ORM y Driver:** SQLAlchemy 2.x + Psycopg 3 (`postgresql+psycopg://`).
- **Migraciones:** Alembic (`backend/alembic/versions/20261029_0001_initial_foxus_schema.py`).

## Tablas Implementadas (12 Tablas)

| Tabla | Clave Primaria | Propósito |
| :--- | :--- | :--- |
| `users` | `id (UUID)` | Identidad de usuario (incluye usuario semilla de demostración). |
| `user_preferences` | `id (UUID)` | Nivel de intervención (`0..3`), hook preferido, audio, música, horario silencioso y zona horaria. |
| `tasks` | `id (UUID)` | Tareas manuales o sincronizadas desde proveedores externos (`manual`, `google_tasks`). |
| `microtasks` | `id (UUID)` | Microacciones concretas asociadas a una tarea, con `estimated_seconds` y `model_name` real. |
| `hooks` | `id (UUID)` | Intervenciones generadas (`challenge`, `fox_bzz`), canal, intensidad y estado. |
| `sessions` | `id (UUID)` | Sesiones de enfoque iniciadas, detenidas o completadas (`completion_source` autorreportado). |
| `session_events` | `id (UUID)` | Bitácora de eventos con columna `metadata (JSONB)`. |
| `reward_transactions` | `id (UUID)` | Transacciones deterministas de XP protegidas con `idempotency_key UNIQUE`. |
| `user_hook_stats` | `id (UUID)` | Estadísticas agregadas por `(user_id, hook_type)` para el `LearningAgent`. |
| `devices` | `id (UUID)` | Prototipos físicos ESP32 y simuladores registrados por usuario. |
| `device_commands` | `id (UUID)` | Cola de comandos (`SHOW_MISSION`, etc.) con `payload (JSONB)` y acuse `acknowledged_at`. |
| `agent_traces` | `id (UUID)` | Trazabilidad por `trace_id`, agente, modelo utilizado, latencia (`duration_ms`) y resúmenes JSONB sin PII. |

## Comandos Útiles (Windows PowerShell)

```powershell
# Aplicar migraciones a la base configurada en backend/.env
.\backend\.venv\Scripts\python.exe -m alembic -c backend\alembic.ini upgrade head

# Verificar conexión SELECT 1 e inventario de tablas (sin exponer credenciales)
.\backend\.venv\Scripts\python.exe backend\scripts\verify_env_and_db.py
```
