# Licencias de Recursos Visuales y Sonoros (FOXUS)

El código fuente de este repositorio se distribuye bajo la licencia **Apache License 2.0** (ver [`LICENSE`](../LICENSE)).

## Identidad Visual Oficial

- `assets/brand/foxus_logo.png`: Logotipo oficial original de **FOXUS** proporcionado por el equipo propietario del proyecto para Hacktoberfest ESPOL: Clubs Royale. Se conserva íntegro como referencia visual obligatoria para íconos adaptables, splash screen y mascota animada `FoxMascot`.

## Paleta de Diseño Derivada del Logo Oficial

| Token | Hex | Uso Principal |
| :--- | :--- | :--- |
| `DeepNavy` | `#093B4A` | Contenedor de emblema y encabezados profundos |
| `FoxOrange` | `#F58220` | Acento primario, botones de acción y pelaje lateral |
| `FoxGold` | `#F8B62D` | Acento secundario, frente geométrica, recompensas XP |
| `WarmCream` | `#F5EBD7` | Tipografía clara sobre fondo oscuro y mejillas de FOX |
| `SlateBlue` | `#607F87` | Texto secundario, bordes suaves y superficies de soporte |
| `DarkSurface` | `#0B1720` | Fondo principal en modo enfoque y pantallas nocturnas |

## Audio y Música Original (`assets/audio/` y `android/app/src/main/res/raw/`)

Todos los recursos sonoros fueron sintetizados programáticamente desde cero para FOXUS mediante [`backend/scripts/generate_audio_assets.py`](../backend/scripts/generate_audio_assets.py) (PCM 16-bit mono, `22050 Hz`, cero material comercial de terceros). Se publican bajo doble licencia **Apache-2.0 / CC0-1.0**:

| Archivo | Uso en `FoxAudioManager` | Duración | Licencia |
| :--- | :--- | :--- | :--- |
| `fox_appear.wav` | Aparición / curiosidad de FOX (`SoundPool`) | `0.44 s` | Apache-2.0 / CC0-1.0 |
| `challenge_start.wav` | Inicio de **Desafío Relámpago** (`SoundPool`) | `0.62 s` | Apache-2.0 / CC0-1.0 |
| `mission_accept.wav` | Confirmación al aceptar microacción (`SoundPool`) | `0.40 s` | Apache-2.0 / CC0-1.0 |
| `mission_reject.wav` | Tono neutro al posponer/detener (`SoundPool`) | `0.40 s` | Apache-2.0 / CC0-1.0 |
| `mission_complete.wav` | Arpegio de microacción completada (`SoundPool`) | `0.95 s` | Apache-2.0 / CC0-1.0 |
| `xp_gain.wav` | Destello cristalino de ganancia de XP (`SoundPool`) | `0.42 s` | Apache-2.0 / CC0-1.0 |
| `level_up.wav` | Fanfarria de subida de nivel (`SoundPool`) | `1.12 s` | Apache-2.0 / CC0-1.0 |
| `fly_buzz.wav` | Zumbido acústico breve + marimba para **FOX BZZ** (`SoundPool`) | `0.62 s` | Apache-2.0 / CC0-1.0 |
| `fox_sleep.wav` | Acorde descendente suave al silenciar a FOX (`SoundPool`) | `0.80 s` | Apache-2.0 / CC0-1.0 |
| `focus_ambient_loop.wav` | Pista instrumental ambiental en bucle continuo (`ExoPlayer`) | `12.00 s` | Apache-2.0 / CC0-1.0 |
| `referee_whistle.wav` | Silbato deportivo de inicio del Árbitro FOX (`SoundPool`) | `0.62 s` | Apache-2.0 / CC0-1.0 |
| `crowd_cheer.wav` | Fanfarria breve de aliento al completar viñetas (`SoundPool`) | `0.62 s` | Apache-2.0 / CC0-1.0 |

## Animaciones Vectoriales y Geométricas (`assets/animations/` y Jetpack Compose)

- `assets/animations/fox_geometric_emblem.svg` y `assets/animations/fox_animation_states.json`: Derivaciones vectoriales abiertas (**Apache-2.0**) basadas en las facetas angulares del logo oficial (`assets/brand/foxus_logo.png`).
- `android/app/src/main/res/drawable/avd_fox_splash.xml`: `AnimatedVectorDrawable` nativo de arranque con rebote elástico y movimiento de orejas.
- `android/app/src/main/java/com/foxus/app/ui/components/FoxMascot.kt`: Animación procedural en Jetpack Compose para los 7 estados (`IDLE`, `CURIOUS`, `CHALLENGE`, `FOCUS`, `VICTORY`, `SLEEP`, `PLAYFUL`) más ráfaga de confeti geométrico `FoxCelebrationBurst`.
