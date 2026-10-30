# FOXUS: demo física sin Internet

## Cableado

Desconecta USB antes de cablear. Esta asignación es para ESP32-WROOM DevKit clásico
de 38 pines; usa los números GPIO impresos, no la posición física del pin.
La TFT es SPI: SDA significa MOSI y SCL significa reloj SPI en este módulo.
Usa 3.3 V; no se ha confirmado que este módulo genérico tolere 5 V.

| TFT GC9A01 | ESP32 |
|---|---|
| VCC | 3V3 |
| GND | GND |
| SCL | GPIO18 |
| SDA | GPIO23 |
| CS | GPIO27 |
| DC | GPIO26 |
| RES | GPIO25 |
| BLK | 3V3 (retroiluminación encendida) |

| Buzzer de tres pines | ESP32 |
|---|---|
| S / IN | GPIO33 |
| − / GND | GND |
| + / VCC | 3V3 si el módulo admite alimentación de 3.3 V |

Los tres pines no identifican por sí solos la tensión ni si es activo/pasivo.
Revisa la serigrafía/modelo: si exige 5 V, confirma compatibilidad de entrada con
señal de 3.3 V antes de conectarlo. No alimentes el buzzer desde un GPIO.
En main.cpp, ACTIVE_BUZZER=true usa encendido/apagado para módulo activo;
false genera un tono de 2400 Hz para módulo pasivo. BUZZER_ACTIVE_LOW=true
invierte la señal de un módulo activo que suene con nivel bajo.

## Carga con PlatformIO en VS Code

1. Abre PlatformIO Home → Open Project.
2. Selecciona `C:\Users\luisf\Documents\Foxus\firmware\foxus_hook_demo`.
   Es el proyecto nuevo para TFT; `esp32_foxus` sigue siendo el proyecto LCD anterior.
3. Espera a que PlatformIO descargue plataforma y librerías.
4. Pulsa Build (✓). Conecta el ESP32 con cable USB de datos.
5. Pulsa Upload (→). La placa se detectó en COM3 durante la preparación.
   Si hay varias placas, añade `upload_port = COM3` a platformio.ini tras verificar el puerto.
6. Si queda en Connecting, mantén BOOT durante el intento y suéltalo al comenzar la escritura.
7. Abre Serial Monitor a 115200. Debe anunciar FOXUS-DEMO y 192.168.4.1.
8. La pantalla debe mostrar LISTO. Si queda negra, comprueba BLK, masa común y el cableado.

## Demostración

1. Instala `android/app/build/outputs/apk/debug/app-debug.apk` en un teléfono Android físico.
2. Conecta su Wi-Fi a **FOXUS-DEMO**, contraseña **foxusdemo**.
3. Acepta mantener la conexión aunque Android indique «sin Internet».
4. Abre FOXUS → FoxusWatch → **Demo en vivo · ESP32**.
5. Escribe una microacción breve, por ejemplo «Hacer 6 diapositivas».
6. Pulsa **THE HOOK! · Activar reloj**. La app solo confirma tras recibir HTTP 200 del ESP32.
7. La pantalla muestra THE HOOK!, HOY TOCA y la microacción. El buzzer emite dos pulsos.

Respaldo: conectado a esa misma red, abre http://192.168.4.1 en el navegador y pulsa
THE HOOK! en el formulario. No requiere app, servidor Python ni Supabase.
La página de respaldo admite hasta 48 caracteres ASCII sin tildes; la app elimina
tildes al enviar para usar la fuente integrada de la pantalla.

La demo genera un aviso real por Wi-Fi local. No guarda XP, perfil ni evidencia en
la nube. Usa teléfono físico; el emulador no se conecta directamente a esta red Wi-Fi.
El firmware queda preparado, pero la comprobación de pantalla/sonido necesita el
montaje y la carga manual del usuario.

## Texto breve para diapositiva

**FOXUS convierte la intención en el primer paso.**

App → enganche → aviso físico → acción de 5 minutos.

- Una microacción concreta en tu muñeca.
- Señal visual y sonora para empezar.
- Prototipo funcional por Wi-Fi local, sin Internet.

Frase para acompañar la demo: «No te pedimos terminar doce diapositivas. Te damos
el impulso para empezar una. Este aviso lleva el enganche del teléfono al mundo físico».

Esta prueba demuestra el disparo manual de un aviso. La personalización inteligente,
el perfil y las rachas son funciones de la aplicación que necesitan sus servicios;
no se demuestran con este botón.

Referencias técnicas: https://docs.espressif.com/projects/arduino-esp32/en/latest/api/wifi.html
y https://adafruit.github.io/Adafruit_GC9A01A/html/index.html
