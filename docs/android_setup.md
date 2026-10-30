# Configuración, Compilación y Prueba de la APK (`com.foxus.app`)

## 1. Requisitos Verificados en Windows

- **Android Studio:** `2026.2.1` o superior (JBR OpenJDK 25 en `C:\Program Files\Android\Android Studio\jbr`).
- **Android SDK:** `compileSdk = 37`, `minSdk = 26`, `targetSdk = 36`, Emulador detectado `Medium_Phone_API_37.0`.
- **Backend Python:** `.venv` en `backend/.venv` (Python 3.12 / 3.14), conectado a **Supabase PostgreSQL** y **Google Gemma 4 (`gemma-4-26b-a4b-it`)**.

---

## 2. Forma Más Rápida de Probar Todo (1 Comando en PowerShell)

Desde la raíz `c:\Users\luisf\Documents\Foxus`:

```powershell
# Arranca el emulador Android Studio, instala app-debug.apk, abre FOXUS y levanta el backend FastAPI en :8000
powershell -ExecutionPolicy Bypass -File .\scripts\run_demo_windows.ps1 -StartEmulator -InstallOnDevice
```

Si ya tienes tu emulador o celular conectado por USB, omite `-StartEmulator`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run_demo_windows.ps1 -InstallOnDevice
```

---

## 3. Paso a Paso Manual (Dos Terminales o Android Studio)

### Terminal 1 — Levantar el Backend con Gemma 4 + Supabase
```powershell
cd c:\Users\luisf\Documents\Foxus
.\backend\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir backend --reload --host 0.0.0.0 --port 8000
```
- Puedes verificar el backend en el navegador abriendo `http://localhost:8000/docs` o `http://localhost:8000/api/v1/health`.

### Opción A — Probar en el Emulador de Android Studio (`Medium_Phone_API_37.0`)
1. Abre **Android Studio** -> **Open** -> selecciona la carpeta `c:\Users\luisf\Documents\Foxus\android` y pulsa **Run ▶**, **o** instálala directo por consola:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_37.0
   # En otra ventana cuando encienda el emulador:
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\android\app\build\outputs\apk\debug\app-debug.apk
   ```
2. En el emulador **no necesitas cambiar nada en Ajustes**: la app ya trae preconfigurado `http://10.0.2.2:8000`, que conecta automáticamente con el puerto `8000` de tu PC.

### Opción B — Probar en tu Celular Android Físico
- **Por cable USB (sin depender del firewall Wi-Fi):**
  1. Activa *Depuración por USB* en tu teléfono y conéctalo a la PC.
  2. Ejecuta:
     ```powershell
     & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
     & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\android\app\build\outputs\apk\debug\app-debug.apk
     ```
  3. En FOXUS -> icono de **Ajustes (⚙)** arriba a la derecha en *Inicio*, pon URL Base `http://127.0.0.1:8000` y pulsa **Guardar y probar conexión**.
- **Por red Wi-Fi local (o pasando el archivo `app-debug.apk` por WhatsApp/Drive/USB):**
  1. Obtén la IP Wi-Fi de tu PC ejecutando `ipconfig` en PowerShell (ej. `192.168.1.35`).
  2. Instala `android\app\build\outputs\apk\debug\app-debug.apk` en tu celular.
  3. En FOXUS -> **Ajustes (⚙)**, cambia URL Base a `http://<TU_IP_PC>:8000` y pulsa **Guardar y probar conexión**.

---

## 4. Recompilar la APK Manualmente desde Terminal

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Set-Location c:\Users\luisf\Documents\Foxus\android
.\gradlew.bat assembleDebug testDebugUnitTest
```
APK generada en: `android/app/build/outputs/apk/debug/app-debug.apk`.
