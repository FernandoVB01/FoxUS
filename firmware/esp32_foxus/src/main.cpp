#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <LiquidCrystal_I2C.h>
#include <ArduinoJson.h>

#if __has_include("foxus_config.h")
#include "foxus_config.h"
#else
#include "foxus_config.example.h"
#endif

#include "lcd_utils.h"

// ============================================================================
// FOXUS ESP32 Smartwatch Prototype Firmware (Section 10)
// State Machine: DISCONNECTED -> CONNECTING -> IDLE -> MISSION_RECEIVED
//                -> MISSION_RUNNING -> MISSION_COMPLETED
// Non-blocking `millis()` architecture with hardware INPUT_PULLUP debounce.
// ============================================================================

enum class WatchState {
    DISCONNECTED,
    CONNECTING,
    IDLE,
    MISSION_RECEIVED,
    MISSION_RUNNING,
    MISSION_COMPLETED
};

struct DebouncedButton {
    uint8_t pin;
    bool lastReading = HIGH;
    bool stableState = HIGH;
    unsigned long lastChangeMs = 0;

    void begin(uint8_t gpioPin) {
        pin = gpioPin;
        pinMode(pin, INPUT_PULLUP);
        lastReading = digitalRead(pin);
        stableState = lastReading;
        lastChangeMs = millis();
    }

    // Returns true once per physical press (falling edge HIGH -> LOW)
    bool pressedEdge() {
        bool reading = digitalRead(pin);
        unsigned long now = millis();
        if (reading != lastReading) {
            lastChangeMs = now;
            lastReading = reading;
        }
        if ((now - lastChangeMs) >= DEBOUNCE_MS && reading != stableState) {
            stableState = reading;
            if (stableState == LOW) {
                return true;
            }
        }
        return false;
    }
};

LiquidCrystal_I2C lcd(LCD_I2C_ADDR, LCD_COLS, LCD_ROWS);
DebouncedButton btnAccept;
DebouncedButton btnPostpone;
DebouncedButton btnComplete;

WatchState currentState = WatchState::DISCONNECTED;
String prevLcd1 = "";
String prevLcd2 = "";

String registeredDeviceId = "";
String activeCommandId = "";
String activeMicrotaskId = "";
String activeHookId = "";
String activeSessionId = "";
String missionLine1 = "FOX MISSION 3m";
String missionLine2 = "Accept? [BTN1]";
unsigned long missionDurationSec = 180UL;
unsigned long missionStartedMs = 0UL;
unsigned long completedEnteredMs = 0UL;
int lastAwardedXp = 25;

unsigned long lastWifiAttemptMs = 0UL;
unsigned long lastPollMs = 0UL;
unsigned long lastHeartbeatMs = 0UL;
unsigned int eventSeq = 0U;

String makeIdempotencyKey(const char* prefix) {
    eventSeq++;
    return String(FOXUS_DEVICE_CODE) + "-" + String(prefix) + "-" + String(millis()) + "-" + String(eventSeq);
}

bool registerWatchOnBackend() {
    if (WiFi.status() != WL_CONNECTED) return false;

    HTTPClient http;
    String url = String(FOXUS_BACKEND_BASE_URL) + "/api/v1/devices/register";
    http.begin(url);
    http.setTimeout(4000);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-Token", FOXUS_DEVICE_TOKEN);

    JsonDocument reqDoc;
    reqDoc["device_name"] = FOXUS_DEVICE_NAME;
    reqDoc["device_type"] = "esp32_watch";
    String body;
    serializeJson(reqDoc, body);

    int code = http.POST(body);
    if (code == 200 || code == 201) {
        String resp = http.getString();
        JsonDocument resDoc;
        if (deserializeJson(resDoc, resp) == DeserializationError::Ok) {
            registeredDeviceId = resDoc["id"] | "";
            http.end();
            Serial.printf("[FOXUS] Registered device_id=%s\n", registeredDeviceId.c_str());
            return registeredDeviceId.length() > 0;
        }
    } else {
        Serial.printf("[FOXUS] Register HTTP error: %d\n", code);
    }
    http.end();
    return false;
}

bool sendWatchEvent(const char* eventType, int& outXpAwarded) {
    if (WiFi.status() != WL_CONNECTED || registeredDeviceId.length() == 0) return false;

    HTTPClient http;
    String url = String(FOXUS_BACKEND_BASE_URL) + "/api/v1/devices/" + registeredDeviceId + "/events";
    http.begin(url);
    http.setTimeout(4500);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-Token", FOXUS_DEVICE_TOKEN);

    JsonDocument doc;
    doc["event_type"] = eventType;
    doc["source"] = "esp32";
    doc["idempotency_key"] = makeIdempotencyKey(eventType);
    if (activeCommandId.length() > 0) doc["command_id"] = activeCommandId;
    if (activeMicrotaskId.length() > 0) doc["microtask_id"] = activeMicrotaskId;
    if (activeHookId.length() > 0) doc["hook_id"] = activeHookId;
    if (activeSessionId.length() > 0) doc["session_id"] = activeSessionId;

    String payload;
    serializeJson(doc, payload);
    int code = http.POST(payload);
    if (code >= 200 && code < 300) {
        String resp = http.getString();
        JsonDocument resDoc;
        if (deserializeJson(resDoc, resp) == DeserializationError::Ok) {
            const char* sid = resDoc["session_id"] | "";
            if (strlen(sid) > 0) {
                activeSessionId = String(sid);
            }
            outXpAwarded = resDoc["xp_awarded"] | 0;
        }
        http.end();
        return true;
    }
    Serial.printf("[FOXUS] Event %s failed HTTP %d\n", eventType, code);
    http.end();
    return false;
}

void sendWatchHeartbeat() {
    if (WiFi.status() != WL_CONNECTED || registeredDeviceId.length() == 0) return;

    HTTPClient http;
    String url = String(FOXUS_BACKEND_BASE_URL) + "/api/v1/devices/" + registeredDeviceId + "/heartbeat";
    http.begin(url);
    http.setTimeout(3000);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-Token", FOXUS_DEVICE_TOKEN);

    JsonDocument doc;
    doc["status"] = (currentState == WatchState::MISSION_RUNNING) ? "mission_running" : "idle";
    doc["firmware_version"] = "0.2.0";
    doc["rssi_dbm"] = WiFi.RSSI();

    String body;
    serializeJson(doc, body);
    http.POST(body);
    http.end();
}

void pollNextCommand() {
    if (WiFi.status() != WL_CONNECTED) return;
    if (registeredDeviceId.length() == 0) {
        registerWatchOnBackend();
        return;
    }

    HTTPClient http;
    String url = String(FOXUS_BACKEND_BASE_URL) + "/api/v1/devices/" + registeredDeviceId + "/commands/next";
    http.begin(url);
    http.setTimeout(3500);
    http.addHeader("X-Device-Token", FOXUS_DEVICE_TOKEN);

    int code = http.GET();
    if (code == 200) {
        String resp = http.getString();
        JsonDocument doc;
        if (deserializeJson(doc, resp) == DeserializationError::Ok) {
            bool hasCmd = doc["has_command"] | false;
            if (hasCmd) {
                JsonObject cmd = doc["command"];
                String nextCmdId = cmd["id"] | "";
                if (nextCmdId.length() > 0 && nextCmdId != activeCommandId) {
                    activeCommandId = nextCmdId;
                    JsonObject p = cmd["payload"];
                    activeMicrotaskId = p["microtask_id"] | "";
                    activeHookId = p["hook_id"] | "";
                    activeSessionId = "";
                    missionDurationSec = p["duration_seconds"] | 180UL;

                    const char* l1 = p["lcd_line_1"] | "FOX MISSION 3m";
                    const char* l2 = p["lcd_line_2"] | "Accept? [BTN1]";
                    missionLine1 = String(l1);
                    missionLine2 = String(l2);

                    int dummyXp = 0;
                    sendWatchEvent("COMMAND_ACK", dummyXp);
                    currentState = WatchState::MISSION_RECEIVED;
                    Serial.printf("[FOXUS] Mission received cmd=%s\n", activeCommandId.c_str());
                }
            }
        }
    }
    http.end();
}

void setup() {
    Serial.begin(115200);
    Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
    lcd.init();
    lcd.backlight();

    btnAccept.begin(PIN_BTN_ACCEPT);
    btnPostpone.begin(PIN_BTN_POSTPONE);
    btnComplete.begin(PIN_BTN_COMPLETE);

    WiFi.mode(WIFI_STA);
    WiFi.begin(FOXUS_WIFI_SSID, FOXUS_WIFI_PASSWORD);
    currentState = WatchState::CONNECTING;
    lastWifiAttemptMs = millis();

    renderLcdLines(lcd, prevLcd1, prevLcd2, "FOXUS BOOTING", "Connecting WiFi");
}

void loop() {
    unsigned long now = millis();

    // Connectivity management
    if (WiFi.status() != WL_CONNECTED) {
        if (currentState != WatchState::CONNECTING && currentState != WatchState::MISSION_RUNNING) {
            currentState = WatchState::DISCONNECTED;
        }
        if ((now - lastWifiAttemptMs) >= 6000UL) {
            lastWifiAttemptMs = now;
            WiFi.disconnect();
            WiFi.begin(FOXUS_WIFI_SSID, FOXUS_WIFI_PASSWORD);
            if (currentState != WatchState::MISSION_RUNNING) {
                currentState = WatchState::CONNECTING;
            }
        }
    } else if (currentState == WatchState::DISCONNECTED || currentState == WatchState::CONNECTING) {
        registerWatchOnBackend();
        currentState = WatchState::IDLE;
    }

    // Read physical buttons (non-blocking debounced edges)
    bool acceptPressed = btnAccept.pressedEdge();
    bool postponePressed = btnPostpone.pressedEdge();
    bool completePressed = btnComplete.pressedEdge();

    switch (currentState) {
        case WatchState::DISCONNECTED:
            renderLcdLines(lcd, prevLcd1, prevLcd2, "FOXUS OFFLINE", "Reconnecting...");
            break;

        case WatchState::CONNECTING:
            renderLcdLines(lcd, prevLcd1, prevLcd2, "FOXUS WIFI...", FOXUS_WIFI_SSID);
            break;

        case WatchState::IDLE:
            renderLcdLines(lcd, prevLcd1, prevLcd2, "FOXUS READY", "Waiting mission");
            if ((now - lastPollMs) >= POLL_INTERVAL_MS) {
                lastPollMs = now;
                pollNextCommand();
            }
            break;

        case WatchState::MISSION_RECEIVED:
            renderLcdLines(lcd, prevLcd1, prevLcd2, missionLine1, missionLine2);
            if (acceptPressed) {
                int xp = 0;
                sendWatchEvent("BUTTON_ACCEPT", xp);
                missionStartedMs = now;
                currentState = WatchState::MISSION_RUNNING;
            } else if (postponePressed) {
                int xp = 0;
                sendWatchEvent("BUTTON_POSTPONE", xp);
                activeCommandId = "";
                currentState = WatchState::IDLE;
            }
            break;

        case WatchState::MISSION_RUNNING: {
            unsigned long elapsedSec = (now - missionStartedMs) / 1000UL;
            unsigned long remainingSec = (elapsedSec < missionDurationSec)
                ? (missionDurationSec - elapsedSec)
                : 0UL;

            renderLcdLines(
                lcd,
                prevLcd1,
                prevLcd2,
                formatCountdownMMSS(remainingSec),
                remainingSec > 0 ? "Keep going!" : "Time! [BTN3]"
            );

            if (completePressed) {
                int xp = 20;
                if (sendWatchEvent("BUTTON_COMPLETE", xp) && xp > 0) {
                    lastAwardedXp = xp;
                } else {
                    lastAwardedXp = 25;
                }
                completedEnteredMs = now;
                currentState = WatchState::MISSION_COMPLETED;
            } else if (postponePressed) {
                int xp = 0;
                sendWatchEvent("BUTTON_POSTPONE", xp);
                activeCommandId = "";
                activeSessionId = "";
                currentState = WatchState::IDLE;
            }
            break;
        }

        case WatchState::MISSION_COMPLETED: {
            String xpLine = "DONE! +" + String(lastAwardedXp) + " XP";
            renderLcdLines(lcd, prevLcd1, prevLcd2, xpLine, "Good start!");
            if ((now - completedEnteredMs) >= CELEBRATION_HOLD_MS || acceptPressed || completePressed) {
                activeCommandId = "";
                activeSessionId = "";
                currentState = WatchState::IDLE;
            }
            break;
        }
    }

    if (WiFi.status() == WL_CONNECTED && (now - lastHeartbeatMs) >= HEARTBEAT_INTERVAL_MS) {
        lastHeartbeatMs = now;
        sendWatchHeartbeat();
    }
}
