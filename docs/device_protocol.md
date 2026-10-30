# Protocolo de Dispositivo ESP32 y Simulador — FOXUS

Tanto el firmware físico ([`main.cpp`](../firmware/esp32_foxus/src/main.cpp)) como el simulador integrado en Android (`DeviceScreen.kt`) consumen el mismo contrato REST autenticado mediante el encabezado `X-Device-Token`.

## Máquina de Estados del Reloj
`DISCONNECTED` -> `CONNECTING` -> `IDLE` -> `MISSION_RECEIVED` -> `MISSION_RUNNING` -> `MISSION_COMPLETED` -> `IDLE`

## Pantallas LCD 16x2

| Estado | Línea 1 (16 chars) | Línea 2 (16 chars) |
|---|---|---|
| `IDLE` | `FOXUS READY` | `Waiting mission` |
| `MISSION_RECEIVED` | `FOX MISSION 3m` *(o dinámica)* | `Accept? [BTN1]` |
| `MISSION_RUNNING` | `FOCUS 02:41` | `Keep going!` |
| `MISSION_COMPLETED` | `DONE! +25 XP` | `Good start!` |

## Endpoints Utilizados

1. **`POST /api/v1/devices/register`** — Registra o reconecta el reloj (`device_type: "esp32_watch"` o `"android_simulator"`).
2. **`GET /api/v1/devices/{device_id}/commands/next`** — Sondeo ligero no bloqueante (`millis()`) para recibir misiones generadas por Google Gemma 4 (`lcd_line_1`, `lcd_line_2`, `duration_seconds`, `microtask_id`, `hook_id`).
3. **`POST /api/v1/devices/{device_id}/events`** — Reporta pulsaciones físicas con `idempotency_key` única:
   - `COMMAND_ACK`: confirma recepción en LCD.
   - `BUTTON_ACCEPT`: inicia `FocusSession` y otorga `+5 XP` por arranque.
   - `BUTTON_POSTPONE`: pospone sin culpa y alimenta a `LearningAgent`.
   - `BUTTON_COMPLETE`: cierra la microtarea y otorga `+20 XP` (+ bono racha).
4. **`POST /api/v1/devices/{device_id}/heartbeat`** — Actualiza `last_seen_at`, estado actual e intensidad Wi-Fi (`rssi_dbm`).
