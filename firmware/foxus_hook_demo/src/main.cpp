#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <SPI.h>
#include <Adafruit_GC9A01A.h>

// ESP32-WROOM DevKit: GPIO numbers, not physical header positions.
constexpr int CS = 27, DC = 26, RESET = 25, BUZZER = 33;
// Three-pin modules may be active HIGH or active LOW. Change if needed.
constexpr bool ACTIVE_BUZZER = true, BUZZER_ACTIVE_LOW = false;
Adafruit_GC9A01A display(CS, DC, RESET);
WebServer server(80);
unsigned long beepStarted = 0;
bool sounding = false;
const uint16_t ORANGE = 0xFD20, NAVY = 0x0843;

void sound(bool on) {
  if (ACTIVE_BUZZER) digitalWrite(BUZZER, (on != BUZZER_ACTIVE_LOW) ? HIGH : LOW);
  else ledcWriteTone(0, on ? 2400 : 0);
}

void centered(const String &text, int y, int size, uint16_t color) {
  display.setTextSize(size);
  display.setTextColor(color);
  int16_t x1, y1; uint16_t w, h;
  display.getTextBounds(text, 0, y, &x1, &y1, &w, &h);
  display.setCursor((240 - int(w)) / 2, y);
  display.print(text);
}

void face(bool hook, String task = "") {
  display.fillScreen(NAVY);
  display.drawCircle(120, 120, 116, ORANGE);
  centered("FOXUS", 32, 2, ORANGE);
  centered(hook ? "THE HOOK!" : "LISTO", 70, 3, 0xFFFF);
  if (hook) {
    centered("HOY TOCA", 110, 2, ORANGE);
    // Max 48 ASCII chars in three centered lines, away from round edges.
    for (int i = 0; i < 3; i++) centered(task.substring(i * 16, (i + 1) * 16), 138 + i * 18, 1, 0xFFFF);
    centered("SOLO 5 MIN", 202, 1, ORANGE);
  } else {
    centered("Wi-Fi: FOXUS-DEMO", 120, 1, 0xFFFF);
    centered("Clave: foxusdemo", 143, 1, 0xFFFF);
    centered("192.168.4.1", 169, 1, ORANGE);
  }
}

void setup() {
  Serial.begin(115200);
  SPI.begin(18, -1, 23, CS);
  display.begin(); display.setRotation(0); display.setTextWrap(false);
  if (ACTIVE_BUZZER) pinMode(BUZZER, OUTPUT);
  else { ledcSetup(0, 2400, 8); ledcAttachPin(BUZZER, 0); }
  sound(false);
  WiFi.mode(WIFI_AP);
  WiFi.softAP("FOXUS-DEMO", "foxusdemo");
  face(false);
  server.on("/", HTTP_GET, []() {
    server.send(200, "text/html", "<meta name='viewport' content='width=device-width'><h1>FOXUS DEMO</h1><form method='post' action='/hook'><input name='task' maxlength='48' value='Hacer 6 diapositivas'><button>THE HOOK!</button></form>");
  });
  server.on("/hook", HTTP_POST, []() {
    String task = server.arg("task"); task.trim();
    if (task.length() == 0 || task.length() > 48) {
      server.send(400, "text/plain", "Usa entre 1 y 48 caracteres"); return;
    }
    // Built-in font is ASCII; reject unsupported glyphs instead of displaying garbage.
    for (size_t i = 0; i < task.length(); i++) if (task[i] < 32 || task[i] > 126) {
      server.send(400, "text/plain", "Usa texto sin tildes"); return;
    }
    face(true, task);
    beepStarted = millis(); sounding = true; sound(true);
    server.send(200, "text/plain", "Hook recibido por ESP32");
  });
  server.begin();
  Serial.println("FOXUS-DEMO / foxusdemo / http://192.168.4.1");
}

void loop() {
  server.handleClient();
  if (sounding) {
    unsigned long elapsed = millis() - beepStarted;
    if (elapsed >= 650) { sound(false); sounding = false; }
    else sound(elapsed < 180 || elapsed > 350);
  }
}
