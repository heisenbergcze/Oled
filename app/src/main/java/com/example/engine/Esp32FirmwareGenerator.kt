package com.example.engine

import com.example.model.BufferLayout
import com.example.model.Esp32Config
import com.example.model.TransportMode

object Esp32FirmwareGenerator {

    fun generateSketch(config: Esp32Config): String {
        val displayIncludes = if (config.controller.isSh1106) {
            """
            #include <Wire.h>
            #include <Adafruit_GFX.h>
            #include <Adafruit_SH110X.h>
            """.trimIndent()
        } else {
            """
            #include <Wire.h>
            #include <Adafruit_GFX.h>
            #include <Adafruit_SSD1306.h>
            """.trimIndent()
        }

        val displayDecl = if (config.controller.isSh1106) {
            """
            #define SCREEN_WIDTH 128
            #define SCREEN_HEIGHT 64
            #define OLED_ADDR ${config.controller.i2cAddressHex}
            #define I2C_SDA ${config.sdaPin}
            #define I2C_SCL ${config.sclPin}

            Adafruit_SH1106G display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);
            """.trimIndent()
        } else {
            """
            #define SCREEN_WIDTH 128
            #define SCREEN_HEIGHT 64
            #define OLED_ADDR ${config.controller.i2cAddressHex}
            #define I2C_SDA ${config.sdaPin}
            #define I2C_SCL ${config.sclPin}

            Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);
            """.trimIndent()
        }

        val displayBegin = if (config.controller.isSh1106) {
            """
            Wire.begin(I2C_SDA, I2C_SCL);
            Wire.setClock(400000); // Fast I2C 400 kHz pro plynulou animaci
            if (!display.begin(OLED_ADDR, true)) {
              Serial.println("SH1106 OLED nenalezen!");
              while (true) delay(500);
            }
            """.trimIndent()
        } else {
            """
            Wire.begin(I2C_SDA, I2C_SCL);
            Wire.setClock(400000); // Fast I2C 400 kHz pro plynulou animaci
            if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR)) {
              Serial.println("SSD1306 OLED nenalezen!");
              while (true) delay(500);
            }
            """.trimIndent()
        }

        val renderFn = if (config.bufferLayout == BufferLayout.HORIZONTAL_MSB) {
            """
            void renderFrameBuffer(const uint8_t* buf) {
              display.clearDisplay();
              display.drawBitmap(0, 0, buf, 128, 64, 1);
              display.display();
            }
            """.trimIndent()
        } else {
            """
            void renderFrameBuffer(const uint8_t* buf) {
              display.clearDisplay();
              for (int page = 0; page < 8; page++) {
                for (int x = 0; x < 128; x++) {
                  uint8_t col = buf[page * 128 + x];
                  for (int bit = 0; bit < 8; bit++) {
                    if (col & (1 << bit)) {
                      display.drawPixel(x, page * 8 + bit, 1);
                    }
                  }
                }
              }
              display.display();
            }
            """.trimIndent()
        }

        return when (config.transportMode) {
            TransportMode.WIFI_HTTP -> generateWifiHttpSketch(displayIncludes, displayDecl, displayBegin, renderFn, config)
            TransportMode.WIFI_UDP -> generateWifiUdpSketch(displayIncludes, displayDecl, displayBegin, renderFn, config)
            TransportMode.BLUETOOTH_SPP -> generateBluetoothSketch(displayIncludes, displayDecl, displayBegin, renderFn)
        }
    }

    private fun generateWifiHttpSketch(
        includes: String,
        decl: String,
        begin: String,
        renderFn: String,
        config: Esp32Config
    ): String = """
// ============================================================
// Karvina Radar -> ESP32 + 0.96" I2C OLED (Wi-Fi HTTP Receiver)
// Vygenerovano aplikaci Karvina OLED
// ============================================================
#include <WiFi.h>
#include <WebServer.h>
$includes

const char* WIFI_SSID = "VASE_WIFI_JMENO";
const char* WIFI_PASS = "VASE_WIFI_HESLO";

$decl

WebServer server(${config.httpPort});
uint8_t frameBuf[1024];
uint32_t rxFrames = 0;

$renderFn

void showSplash(const String& line2) {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(1);
  display.setCursor(0, 2);
  display.println("KARVINA RADAR OLED");
  display.drawLine(0, 14, 127, 14, 1);
  display.setCursor(0, 24);
  display.println(line2);
  display.setCursor(0, 42);
  display.println("I2C SDA:" + String(I2C_SDA) + " SCL:" + String(I2C_SCL));
  display.display();
}

void handleStatus() {
  String msg = "OK KarvinaOLED frames=" + String(rxFrames);
  server.send(200, "text/plain", msg);
}

void handleFrame() {
  if (!server.hasArg("plain")) {
    server.send(400, "text/plain", "Missing body");
    return;
  }
  String body = server.arg("plain");
  if (body.length() != 1024) {
    server.send(400, "text/plain", "Expected 1024 bytes");
    return;
  }
  memcpy(frameBuf, body.c_str(), 1024);
  renderFrameBuffer(frameBuf);
  rxFrames++;
  server.send(200, "text/plain", "OK");
}

void setup() {
  Serial.begin(115200);
$begin
  showSplash("Pripojovani Wi-Fi...");

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  String ip = WiFi.localIP().toString();
  Serial.println("\nIP: " + ip);
  showSplash("IP: " + ip);

  server.on("/status", HTTP_GET, handleStatus);
  server.on("/frame", HTTP_POST, handleFrame);
  server.begin();
}

void loop() {
  server.handleClient();
}
""".trimIndent()

    private fun generateWifiUdpSketch(
        includes: String,
        decl: String,
        begin: String,
        renderFn: String,
        config: Esp32Config
    ): String = """
// ============================================================
// Karvina Radar -> ESP32 + 0.96" I2C OLED (Wi-Fi UDP Stream)
// Rychly nízkolatencni stream animace radaru (1028 B paket)
// ============================================================
#include <WiFi.h>
#include <WiFiUdp.h>
$includes

const char* WIFI_SSID = "VASE_WIFI_JMENO";
const char* WIFI_PASS = "VASE_WIFI_HESLO";
const uint16_t UDP_PORT = ${config.udpPort};

$decl

WiFiUDP udp;
uint8_t packetBuf[1028];

$renderFn

void setup() {
  Serial.begin(115200);
$begin
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(1);
  display.setCursor(0, 2);
  display.println("KARVINA UDP STREAM");
  display.display();

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(250);

  udp.begin(UDP_PORT);
  display.setCursor(0, 24);
  display.println("IP: " + WiFi.localIP().toString());
  display.setCursor(0, 38);
  display.println("UDP port: " + String(UDP_PORT));
  display.display();
}

void loop() {
  int len = udp.parsePacket();
  if (len == 1028) {
    int readLen = udp.read(packetBuf, 1028);
    if (readLen == 1028 && packetBuf[0] == 0xAA && packetBuf[1] == 0x55) {
      renderFrameBuffer(&packetBuf[4]);
    }
  }
}
""".trimIndent()

    private fun generateBluetoothSketch(
        includes: String,
        decl: String,
        begin: String,
        renderFn: String
    ): String = """
// ============================================================
// Karvina Radar -> ESP32 + 0.96" I2C OLED (Bluetooth SPP)
// Protokol: [0xAA, 0x55, 1024 B OLED data, XOR checksum]
// ============================================================
#include "BluetoothSerial.h"
$includes

$decl

BluetoothSerial SerialBT;
uint8_t frameBuf[1024];

$renderFn

void setup() {
  Serial.begin(115200);
$begin
  SerialBT.begin("Karvina-Radar-OLED");
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(1);
  display.setCursor(0, 2);
  display.println("KARVINA RADAR BT");
  display.drawLine(0, 14, 127, 14, 1);
  display.setCursor(0, 26);
  display.println("Sparujte zarizeni:");
  display.setCursor(0, 40);
  display.println("Karvina-Radar-OLED");
  display.display();
}

void loop() {
  if (SerialBT.available() >= 2) {
    if (SerialBT.read() == 0xAA && SerialBT.peek() == 0x55) {
      SerialBT.read(); // 0x55
      size_t got = SerialBT.readBytes(frameBuf, 1024);
      if (got == 1024) {
        uint8_t rxCheck = 0;
        unsigned long t0 = millis();
        while (!SerialBT.available() && millis() - t0 < 200) delay(1);
        if (SerialBT.available()) {
          rxCheck = SerialBT.read();
          uint8_t calc = 0;
          for (int i = 0; i < 1024; i++) calc ^= frameBuf[i];
          if (calc == rxCheck) {
            renderFrameBuffer(frameBuf);
          }
        }
      }
    }
  }
}
""".trimIndent()

    fun requiredLibraries(config: Esp32Config): List<String> = buildList {
        add("Wire (vestavěná v ESP32 Arduino Core)")
        add("Adafruit GFX Library")
        if (config.controller.isSh1106) {
            add("Adafruit SH110X")
        } else {
            add("Adafruit SSD1306")
        }
        when (config.transportMode) {
            TransportMode.WIFI_HTTP -> add("WiFi + WebServer (vestavěné v ESP32 Core)")
            TransportMode.WIFI_UDP -> add("WiFi + WiFiUdp (vestavěné v ESP32 Core)")
            TransportMode.BLUETOOTH_SPP -> add("BluetoothSerial (vestavěná v ESP32 Core)")
        }
    }
}
