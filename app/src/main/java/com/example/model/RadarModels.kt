package com.example.model

import android.graphics.Bitmap

object KarvinaGeo {
    const val LATITUDE = 49.8540
    const val LONGITUDE = 18.5410
    const val METEOPRESS_KARVINA_URL = "https://www.meteopress.cz/radar/#8/49.854/18.541"
    const val METEOPRESS_CR_URL = "https://www.meteopress.cz/radar/#6/49.700/15.600"
}

enum class RadarSourceMode(val labelCz: String) {
    DIRECT_API("Radar API (Animace)"),
    METEOPRESS_WEB("Meteopress Web")
}

enum class DitherAlgorithm(val labelCz: String, val shortLabel: String) {
    FLOYD_STEINBERG("Floyd-Steinberg", "Floyd-S"),
    BAYER_4X4("Bayer 4×4", "Bayer"),
    THRESHOLD("Pevný práh", "Práh"),
    HALFTONE_DOTS("Intenzitní rastr", "Rastr")
}

enum class OledColorVariant(val labelCz: String) {
    DUAL_YELLOW_BLUE("Žluto-modrý (0.96\")"),
    ICE_CYAN("Ledově azurový"),
    PURE_WHITE("Čistě bílý")
}

enum class OledController(val labelCz: String, val i2cAddressHex: String, val isSh1106: Boolean) {
    SSD1306_3C("SSD1306 (0x3C)", "0x3C", false),
    SSD1306_3D("SSD1306 (0x3D)", "0x3D", false),
    SH1106_3C("SH1106 (0x3C)", "0x3C", true)
}

enum class BufferLayout(val labelCz: String, val codeTag: String) {
    HORIZONTAL_MSB("Horizontální (Adafruit GFX)", "HORIZ_MSB"),
    PAGE_VERTICAL("Stránkový (SSD1306 GDDRAM)", "PAGE_VERT")
}

enum class TransportMode(val labelCz: String) {
    WIFI_HTTP("Wi-Fi HTTP"),
    WIFI_UDP("Wi-Fi UDP"),
    BLUETOOTH_SPP("Bluetooth SPP")
}

enum class RadarZoomRadius(val labelCz: String, val tileZoom: Int, val radiusKm: Int) {
    KM_25("25 km", 9, 25),
    KM_50("50 km", 8, 50),
    KM_100("100 km", 7, 100),
    KM_200("200 km", 6, 200)
}

data class RadarFrame(
    val timestampEpochSec: Long,
    val formattedTime: String,
    val sourceBitmap: Bitmap,
    val isForecast: Boolean = false
)

data class OledFrame(
    val width: Int = 128,
    val height: Int = 64,
    val pixels: BooleanArray,
    val packedBytes: ByteArray,
    val timestampLabel: String,
    val coveragePercent: Int,
    val intensityLabel: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OledFrame) return false
        return width == other.width &&
            height == other.height &&
            timestampLabel == other.timestampLabel &&
            coveragePercent == other.coveragePercent &&
            intensityLabel == other.intensityLabel &&
            packedBytes.contentEquals(other.packedBytes)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + packedBytes.contentHashCode()
        result = 31 * result + timestampLabel.hashCode()
        result = 31 * result + coveragePercent
        return result
    }
}

data class OledRenderConfig(
    val ditherAlgorithm: DitherAlgorithm = DitherAlgorithm.BAYER_4X4,
    val threshold: Int = 78,
    val showHudBar: Boolean = true,
    val showCrosshairAndRings: Boolean = true,
    val invertPixels: Boolean = false,
    val oledColorVariant: OledColorVariant = OledColorVariant.DUAL_YELLOW_BLUE,
    val zoomRadius: RadarZoomRadius = RadarZoomRadius.KM_50,
    val animationFps: Int = 3
)

data class Esp32Config(
    val transportMode: TransportMode = TransportMode.WIFI_HTTP,
    val host: String = "192.168.1.50",
    val httpPort: Int = 80,
    val udpPort: Int = 4210,
    val btDeviceName: String = "",
    val btDeviceAddress: String = "",
    val controller: OledController = OledController.SSD1306_3C,
    val bufferLayout: BufferLayout = BufferLayout.HORIZONTAL_MSB,
    val sdaPin: Int = 21,
    val sclPin: Int = 22
)

data class PairedBtDevice(
    val name: String,
    val address: String
)

data class ConnectionTelemetry(
    val isConnected: Boolean = false,
    val isStreaming: Boolean = false,
    val isTesting: Boolean = false,
    val statusMessage: String = "Nepřipojeno k ESP32",
    val latencyMs: Long? = null,
    val framesSent: Long = 0L,
    val lastError: String? = null
)

sealed interface RadarLoadState {
    data object Loading : RadarLoadState
    data class Ready(val frameCount: Int, val sourceName: String, val updatedLabel: String) : RadarLoadState
    data class Error(val message: String) : RadarLoadState
}
