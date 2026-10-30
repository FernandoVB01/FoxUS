@echo off
cd /d "%~dp0\.."
echo ====================================================
echo   FOXUS Backend (FastAPI + Supabase + Gemma 4)
echo   Escuchando en http://0.0.0.0:8000
echo ====================================================
.\backend\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir backend --reload --host 0.0.0.0 --port 8000
pause
