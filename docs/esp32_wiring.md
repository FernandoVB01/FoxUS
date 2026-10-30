# Cableado Prototipo Smartwatch ESP32 — FOXUS

## Componentes de Hardware
- **Microcontrolador:** ESP32 DevKit V1 (30/38 pines, Wi-Fi + BLE).
- **Pantalla:** Módulo LCD 16x2 con mochila I2C (`PCF8574`, dirección por defecto `0x27`).
- **Controles físicos:** 3 pulsadores táctiles momentáneos (`NO`) conectados entre GPIO y `GND` (usando resistencias internas `INPUT_PULLUP`, sin resistencias externas requeridas).

## Tabla Oficial de Pines

| Componente | Pin Módulo | Pin ESP32 | Configuración |
|---|---|---|---|
| LCD I2C 16x2 | `VCC` | `VIN` (`5V`) o `3V3` | Alimentación pantalla |
| LCD I2C 16x2 | `GND` | `GND` | Tierra común |
| LCD I2C 16x2 | `SDA` | `GPIO 21` | Bus I2C Datos |
| LCD I2C 16x2 | `SCL` | `GPIO 22` | Bus I2C Reloj |
| Pulsador 1 (`ACCEPT`) | Pin A / Pin B | `GPIO 25` / `GND` | `INPUT_PULLUP` (Activo en `LOW`) |
| Pulsador 2 (`POSTPONE`) | Pin A / Pin B | `GPIO 26` / `GND` | `INPUT_PULLUP` (Activo en `LOW`) |
| Pulsador 3 (`COMPLETE`) | Pin A / Pin B | `GPIO 27` / `GND` | `INPUT_PULLUP` (Activo en `LOW`) |

## Compilación y Flasheo (PlatformIO)

1. Copia `firmware/esp32_foxus/include/foxus_config.example.h` a `firmware/esp32_foxus/include/foxus_config.h`.
2. Configura `FOXUS_WIFI_SSID`, `FOXUS_WIFI_PASSWORD` y `FOXUS_BACKEND_BASE_URL` con la IP LAN de tu PC (`ipconfig`).
3. Compila y sube el firmware:
   ```powershell
   cd firmware\esp32_foxus
   pio run -t upload
   pio device monitor
   ```
