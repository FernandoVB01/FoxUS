# Guion Oficial de Demostración — Hacktoberfest ESPOL: Clubs Royale

## Categorías Objetivo
- **Best Open-Source AI Project**
- **Best Use of Google Gemma (`gemma-4-26b-a4b-it`)**

## Preparación Previa (60 segundos)
1. **Backend FastAPI + Supabase + Gemma 4:**
   ```powershell
   .\backend\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir backend --reload --host 0.0.0.0 --port 8000
   ```
2. **App Android (`emulator-5554` o celular físico):** instalada con `app-debug.apk`.
3. *(Opcional)* **Monitor técnico para jueces:**
   ```powershell
   .\backend\.venv\Scripts\streamlit.exe run dashboard/streamlit_app.py
   ```

## Guion en Vivo (3 minutos)

1. **El Problema (0:00 - 0:30):** Mostrar en la pestaña **Tareas** una actividad intimidante (*"Preparar 12 diapositivas para mi exposición"*), activar el interruptor **`🔥 Admito que necesito presión`** y opcionalmente sincronizar **Google Calendar / Google Tasks** desde el botón desplegable `Google`.
2. **Modo Presión Inteligente e Interrupción Estilo Duolingo (0:30 - 1:15):**
   - En **Inicio**, tocar **`⏱ En 5s (Salir)`** y salir al escritorio de Android.
   - A los 5 segundos, el orquestador evalúa la urgencia real con **Google Gemma 4** y lanza una notificación emergente de alta prioridad (y tarjeta flotante opcional si se activó el permiso avanzado).
   - También mostrar **`⚡ Gesto ahora`**: deslizar la tarjeta a la derecha para aceptar el reto o a la izquierda para posponer sin culpa.
3. **Retos por Viñetas + Audio + Animación (1:15 - 2:00):**
   - En **Reto**, mostrar cómo Gemma 4 partió la tarea en **3 viñetas accionables** marcables con un toque mientras corre el cronómetro de 3 minutos y `FoxMascot` pasa al estado `FOCUS`.
   - Desplegar `Más info y traza IA` para evidenciar ante el jurado que `gemma-4-26b-a4b-it` generó la misión en vivo (`fallback_used = false`).
4. **FOX BZZ — La Mosca Traviesa + Smartwatch ESP32 (2:00 - 3:00):**
   - Activar **FOX BZZ**, atrapar o espantar la mosca animada para desbloquear la microacción.
   - En **Reloj** (o el prototipo físico ESP32 + LCD 16x2), enviar la misión y pulsar `ACCEPT` -> `COMPLETE`, sumando `+25 XP` deterministas en `RewardEngine` y actualizando `LearningAgent`.
