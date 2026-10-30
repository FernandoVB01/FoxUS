#ifndef FOXUS_LCD_UTILS_H
#define FOXUS_LCD_UTILS_H

#include <Arduino.h>
#include <LiquidCrystal_I2C.h>

/**
 * Formats and pads a string to exactly 16 ASCII characters for a 16x2 LCD line
 * without flickering (`lcd.clear()` is avoided when updating in place).
 */
inline String fitLcd16(const String& raw) {
    String clean = raw;
    clean.trim();
    if (clean.length() > 16) {
        return clean.substring(0, 16);
    }
    while (clean.length() < 16) {
        clean += ' ';
    }
    return clean;
}

inline String formatCountdownMMSS(unsigned long remainingSeconds) {
    unsigned int mins = static_cast<unsigned int>(remainingSeconds / 60UL);
    unsigned int secs = static_cast<unsigned int>(remainingSeconds % 60UL);
    char buf[17];
    snprintf(buf, sizeof(buf), "FOCUS %02u:%02u", mins % 100U, secs);
    return fitLcd16(String(buf));
}

inline void renderLcdLines(
    LiquidCrystal_I2C& lcd,
    String& prevLine1,
    String& prevLine2,
    const String& nextLine1,
    const String& nextLine2
) {
    String f1 = fitLcd16(nextLine1);
    String f2 = fitLcd16(nextLine2);
    if (f1 != prevLine1) {
        lcd.setCursor(0, 0);
        lcd.print(f1);
        prevLine1 = f1;
    }
    if (f2 != prevLine2) {
        lcd.setCursor(0, 1);
        lcd.print(f2);
        prevLine2 = f2;
    }
}

#endif // FOXUS_LCD_UTILS_H
