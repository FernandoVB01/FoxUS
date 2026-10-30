#ifndef FOXUS_CONFIG_H
#define FOXUS_CONFIG_H

// ============================================================================
// FOXUS ESP32 Smartwatch Prototype Configuration Template
// Copy this file to `include/foxus_config.h` and fill in your local network
// values before flashing. `foxus_config.h` is gitignored.
// ============================================================================

#define FOXUS_WIFI_SSID        "YOUR_WIFI_SSID"
#define FOXUS_WIFI_PASSWORD    "YOUR_WIFI_PASSWORD"

// Local IP of the PC running `uvicorn app.main:app --host 0.0.0.0 --port 8000`
#define FOXUS_BACKEND_BASE_URL "http://192.168.1.100:8000"
#define FOXUS_DEVICE_TOKEN     "foxus-device-dev-2026"
#define FOXUS_DEVICE_CODE      "esp32-foxwatch-01"
#define FOXUS_DEVICE_NAME      "FOXUS Watch Prototype"

// Hardware Pinout (ESP32 DevKit V1)
#define PIN_I2C_SDA            21
#define PIN_I2C_SCL            22
#define LCD_I2C_ADDR           0x27
#define LCD_COLS               16
#define LCD_ROWS               2

// Pushbuttons wired to GND using internal INPUT_PULLUP (Active LOW)
#define PIN_BTN_ACCEPT         25
#define PIN_BTN_POSTPONE       26
#define PIN_BTN_COMPLETE       27

// Timing Constants (non-blocking millis)
#define DEBOUNCE_MS            60UL
#define POLL_INTERVAL_MS       2500UL
#define HEARTBEAT_INTERVAL_MS  30000UL
#define CELEBRATION_HOLD_MS    4000UL

#endif // FOXUS_CONFIG_H
