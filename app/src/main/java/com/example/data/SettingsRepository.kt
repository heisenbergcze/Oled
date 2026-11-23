package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BufferLayout
import com.example.model.DitherAlgorithm
import com.example.model.Esp32Config
import com.example.model.OledColorVariant
import com.example.model.OledController
import com.example.model.OledRenderConfig
import com.example.model.RadarZoomRadius
import com.example.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "karvina_oled_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TRANSPORT = stringPreferencesKey("transport_mode")
        val HOST = stringPreferencesKey("esp32_host")
        val HTTP_PORT = intPreferencesKey("esp32_http_port")
        val UDP_PORT = intPreferencesKey("esp32_udp_port")
        val BT_NAME = stringPreferencesKey("bt_name")
        val BT_ADDR = stringPreferencesKey("bt_addr")
        val CONTROLLER = stringPreferencesKey("oled_controller")
        val BUFFER_LAYOUT = stringPreferencesKey("buffer_layout")
        val SDA_PIN = intPreferencesKey("sda_pin")
        val SCL_PIN = intPreferencesKey("scl_pin")

        val DITHER = stringPreferencesKey("dither_algo")
        val THRESHOLD = intPreferencesKey("threshold")
        val SHOW_HUD = booleanPreferencesKey("show_hud")
        val SHOW_CROSSHAIR = booleanPreferencesKey("show_crosshair")
        val INVERT = booleanPreferencesKey("invert_pixels")
        val OLED_COLOR = stringPreferencesKey("oled_color")
        val ZOOM_RADIUS = stringPreferencesKey("zoom_radius")
        val FPS = intPreferencesKey("animation_fps")
    }

    val esp32ConfigFlow: Flow<Esp32Config> = context.dataStore.data.map { prefs ->
        Esp32Config(
            transportMode = prefs[Keys.TRANSPORT]?.let { runCatching { TransportMode.valueOf(it) }.getOrNull() }
                ?: TransportMode.WIFI_HTTP,
            host = prefs[Keys.HOST] ?: "192.168.1.50",
            httpPort = prefs[Keys.HTTP_PORT] ?: 80,
            udpPort = prefs[Keys.UDP_PORT] ?: 4210,
            btDeviceName = prefs[Keys.BT_NAME] ?: "",
            btDeviceAddress = prefs[Keys.BT_ADDR] ?: "",
            controller = prefs[Keys.CONTROLLER]?.let { runCatching { OledController.valueOf(it) }.getOrNull() }
                ?: OledController.SSD1306_3C,
            bufferLayout = prefs[Keys.BUFFER_LAYOUT]?.let { runCatching { BufferLayout.valueOf(it) }.getOrNull() }
                ?: BufferLayout.HORIZONTAL_MSB,
            sdaPin = prefs[Keys.SDA_PIN] ?: 21,
            sclPin = prefs[Keys.SCL_PIN] ?: 22
        )
    }

    val renderConfigFlow: Flow<OledRenderConfig> = context.dataStore.data.map { prefs ->
        OledRenderConfig(
            ditherAlgorithm = prefs[Keys.DITHER]?.let { runCatching { DitherAlgorithm.valueOf(it) }.getOrNull() }
                ?: DitherAlgorithm.BAYER_4X4,
            threshold = prefs[Keys.THRESHOLD] ?: 78,
            showHudBar = prefs[Keys.SHOW_HUD] ?: true,
            showCrosshairAndRings = prefs[Keys.SHOW_CROSSHAIR] ?: true,
            invertPixels = prefs[Keys.INVERT] ?: false,
            oledColorVariant = prefs[Keys.OLED_COLOR]?.let { runCatching { OledColorVariant.valueOf(it) }.getOrNull() }
                ?: OledColorVariant.DUAL_YELLOW_BLUE,
            zoomRadius = prefs[Keys.ZOOM_RADIUS]?.let { runCatching { RadarZoomRadius.valueOf(it) }.getOrNull() }
                ?: RadarZoomRadius.KM_50,
            animationFps = prefs[Keys.FPS] ?: 3
        )
    }

    suspend fun saveEsp32Config(config: Esp32Config) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TRANSPORT] = config.transportMode.name
            prefs[Keys.HOST] = config.host
            prefs[Keys.HTTP_PORT] = config.httpPort
            prefs[Keys.UDP_PORT] = config.udpPort
            prefs[Keys.BT_NAME] = config.btDeviceName
            prefs[Keys.BT_ADDR] = config.btDeviceAddress
            prefs[Keys.CONTROLLER] = config.controller.name
            prefs[Keys.BUFFER_LAYOUT] = config.bufferLayout.name
            prefs[Keys.SDA_PIN] = config.sdaPin
            prefs[Keys.SCL_PIN] = config.sclPin
        }
    }

    suspend fun saveRenderConfig(config: OledRenderConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DITHER] = config.ditherAlgorithm.name
            prefs[Keys.THRESHOLD] = config.threshold
            prefs[Keys.SHOW_HUD] = config.showHudBar
            prefs[Keys.SHOW_CROSSHAIR] = config.showCrosshairAndRings
            prefs[Keys.INVERT] = config.invertPixels
            prefs[Keys.OLED_COLOR] = config.oledColorVariant.name
            prefs[Keys.ZOOM_RADIUS] = config.zoomRadius.name
            prefs[Keys.FPS] = config.animationFps
        }
    }
}
