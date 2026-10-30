param(
    [switch]$InstallOnDevice,
    [switch]$StartEmulator
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$PythonExe = Join-Path $RepoRoot "backend\.venv\Scripts\python.exe"
$ApkPath = Join-Path $RepoRoot "android\app\build\outputs\apk\debug\app-debug.apk"
$AdbExe = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$EmulatorExe = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"

Write-Host "=== FOXUS Demo Launcher ===" -ForegroundColor Cyan

if ($StartEmulator -and (Test-Path $EmulatorExe)) {
    $avds = & $EmulatorExe -list-avds
    if ($avds) {
        $targetAvd = ($avds | Select-Object -First 1)
        Write-Host "Iniciando emulador Android: $targetAvd ..." -ForegroundColor Yellow
        Start-Process -FilePath $EmulatorExe -ArgumentList "-avd", $targetAvd
        Start-Sleep -Seconds 6
    }
}

if ($InstallOnDevice -and (Test-Path $AdbExe) -and (Test-Path $ApkPath)) {
    Write-Host "Esperando dispositivo/emulador ADB conectado..." -ForegroundColor Yellow
    & $AdbExe wait-for-device
    # Habilita tunel USB por si es celular fisico usando http://127.0.0.1:8000
    & $AdbExe reverse tcp:8000 tcp:8000 | Out-Null
    Write-Host "Instalando $ApkPath ..." -ForegroundColor Green
    & $AdbExe install -r $ApkPath
    & $AdbExe shell am start -n com.foxus.app/.MainActivity
}

Write-Host "Iniciando Backend FastAPI + Google Gemma 4 + Supabase PostgreSQL en http://0.0.0.0:8000 ..." -ForegroundColor Green
Write-Host "Swagger UI interactivo: http://localhost:8000/docs" -ForegroundColor Cyan
& $PythonExe -m uvicorn app.main:app --app-dir (Join-Path $RepoRoot "backend") --host 0.0.0.0 --port 8000
