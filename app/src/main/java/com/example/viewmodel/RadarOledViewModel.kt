package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.engine.Esp32FirmwareGenerator
import com.example.engine.Esp32TransportClient
import com.example.engine.OledFrameConverter
import com.example.engine.RadarRepository
import com.example.model.ConnectionTelemetry
import com.example.model.Esp32Config
import com.example.model.KarvinaGeo
import com.example.model.OledFrame
import com.example.model.OledRenderConfig
import com.example.model.PairedBtDevice
import com.example.model.RadarFrame
import com.example.model.RadarLoadState
import com.example.model.RadarSourceMode
import com.example.model.RadarZoomRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RadarOledUiState(
    val sourceMode: RadarSourceMode = RadarSourceMode.DIRECT_API,
    val meteopressUrl: String = KarvinaGeo.METEOPRESS_KARVINA_URL,
    val autoWebCapture: Boolean = true,
    val radarLoadState: RadarLoadState = RadarLoadState.Loading,
    val radarFrames: List<RadarFrame> = emptyList(),
    val currentFrameIndex: Int = 0,
    val isPlayingAnimation: Boolean = true,
    val currentOledFrame: OledFrame = OledFrameConverter.createStandbyFrame(
        statusText = "NACITAM RADAR...",
        config = OledRenderConfig(),
        bufferLayout = Esp32Config().bufferLayout
    ),
    val renderConfig: OledRenderConfig = OledRenderConfig(),
    val esp32Config: Esp32Config = Esp32Config(),
    val telemetry: ConnectionTelemetry = ConnectionTelemetry(),
    val pairedBtDevices: List<PairedBtDevice> = emptyList(),
    val webCaptureCounter: Int = 0
) {
    val activeSourceBitmap: Bitmap?
        get() = radarFrames.getOrNull(currentFrameIndex)?.sourceBitmap

    val generatedFirmwareCode: String
        get() = Esp32FirmwareGenerator.generateSketch(esp32Config)

    val generatedCHeader: String
        get() = OledFrameConverter.exportAsCHeader(currentOledFrame, esp32Config.bufferLayout)
}

class RadarOledViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val radarRepo = RadarRepository()
    private val transportClient = Esp32TransportClient(application)

    private val _uiState = MutableStateFlow(RadarOledUiState())
    val uiState: StateFlow<RadarOledUiState> = _uiState.asStateFlow()

    private var animationJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var lastWebCapturedBitmap: Bitmap? = null
    private var isSendingFrame = false

    private val czechClock = SimpleDateFormat("HH:mm", Locale("cs", "CZ")).apply {
        timeZone = TimeZone.getTimeZone("Europe/Prague")
    }

    init {
        viewModelScope.launch {
            val savedEsp = settingsRepo.esp32ConfigFlow.first()
            val savedRender = settingsRepo.renderConfigFlow.first()
            _uiState.update {
                it.copy(
                    esp32Config = savedEsp,
                    renderConfig = savedRender,
                    currentOledFrame = OledFrameConverter.createStandbyFrame(
                        statusText = "NACITAM RADAR...",
                        config = savedRender,
                        bufferLayout = savedEsp.bufferLayout
                    )
                )
            }
            refreshRadarApiData()
            startAnimationLoop()
            startPeriodicRadarRefresh()
        }
    }

    fun setSourceMode(mode: RadarSourceMode) {
        _uiState.update { it.copy(sourceMode = mode) }
        if (mode == RadarSourceMode.DIRECT_API) {
            if (_uiState.value.radarFrames.isEmpty()) {
                refreshRadarApiData()
            } else {
                reconvertCurrentApiFrame()
            }
        } else {
            lastWebCapturedBitmap?.let { bmp ->
                processWebCapturedBitmap(bmp)
            }
        }
    }

    fun setMeteopressUrl(url: String) {
        _uiState.update { it.copy(meteopressUrl = url) }
    }

    fun setAutoWebCapture(enabled: Boolean) {
        _uiState.update { it.copy(autoWebCapture = enabled) }
    }

    fun refreshRadarApiData() {
        val zoom = _uiState.value.renderConfig.zoomRadius
        _uiState.update { it.copy(radarLoadState = RadarLoadState.Loading) }
        viewModelScope.launch {
            try {
                val result = radarRepo.fetchKarvinaRadarAnimation(zoom)
                val lastIdx = result.frames.lastIndex.coerceAtLeast(0)
                _uiState.update { state ->
                    state.copy(
                        radarLoadState = RadarLoadState.Ready(
                            frameCount = result.frames.size,
                            sourceName = result.sourceLabel,
                            updatedLabel = result.updatedLabel
                        ),
                        radarFrames = result.frames,
                        currentFrameIndex = lastIdx
                    )
                }
                if (_uiState.value.sourceMode == RadarSourceMode.DIRECT_API) {
                    reconvertCurrentApiFrame()
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Nepodařilo se načíst radarová data"
                _uiState.update { state ->
                    state.copy(
                        radarLoadState = RadarLoadState.Error(msg),
                        currentOledFrame = if (state.radarFrames.isEmpty()) {
                            OledFrameConverter.createStandbyFrame(
                                statusText = "OFFLINE RADAR",
                                config = state.renderConfig,
                                bufferLayout = state.esp32Config.bufferLayout
                            )
                        } else {
                            state.currentOledFrame
                        }
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlayingAnimation = !it.isPlayingAnimation) }
    }

    fun seekToFrame(index: Int) {
        val frames = _uiState.value.radarFrames
        if (frames.isEmpty()) return
        val clamped = index.coerceIn(0, frames.lastIndex)
        _uiState.update { it.copy(currentFrameIndex = clamped) }
        reconvertCurrentApiFrame()
    }

    fun onWebViewFrameCaptured(bitmap: Bitmap) {
        lastWebCapturedBitmap = bitmap
        if (_uiState.value.sourceMode != RadarSourceMode.METEOPRESS_WEB) return
        processWebCapturedBitmap(bitmap)
    }

    private fun processWebCapturedBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val state = _uiState.value
            val nowLabel = czechClock.format(Date())
            val nextCount = state.webCaptureCounter + 1
            val oledFrame = withContext(Dispatchers.Default) {
                OledFrameConverter.convertToOledFrame(
                    sourceBitmap = bitmap,
                    timestampLabel = nowLabel,
                    frameIndex = nextCount % 10,
                    totalFrames = 10,
                    config = state.renderConfig,
                    bufferLayout = state.esp32Config.bufferLayout,
                    isWebCapture = true
                )
            }
            _uiState.update {
                it.copy(
                    currentOledFrame = oledFrame,
                    webCaptureCounter = nextCount
                )
            }
            if (_uiState.value.telemetry.isStreaming) {
                transmitFrameIfIdle(oledFrame.packedBytes)
            }
        }
    }

    fun updateRenderConfig(transform: (OledRenderConfig) -> OledRenderConfig) {
        val oldZoom = _uiState.value.renderConfig.zoomRadius
        val updated = transform(_uiState.value.renderConfig)
        _uiState.update { it.copy(renderConfig = updated) }
        viewModelScope.launch {
            settingsRepo.saveRenderConfig(updated)
        }
        if (updated.zoomRadius != oldZoom && _uiState.value.sourceMode == RadarSourceMode.DIRECT_API) {
            refreshRadarApiData()
        } else {
            reconvertActiveSource()
        }
    }

    fun updateZoomRadius(radius: RadarZoomRadius) {
        updateRenderConfig { it.copy(zoomRadius = radius) }
    }

    fun updateEsp32Config(transform: (Esp32Config) -> Esp32Config) {
        val oldLayout = _uiState.value.esp32Config.bufferLayout
        val updated = transform(_uiState.value.esp32Config)
        _uiState.update { it.copy(esp32Config = updated) }
        viewModelScope.launch {
            settingsRepo.saveEsp32Config(updated)
        }
        if (updated.bufferLayout != oldLayout) {
            reconvertActiveSource()
        }
    }

    fun testEsp32Connection() {
        val config = _uiState.value.esp32Config
        _uiState.update {
            it.copy(
                telemetry = it.telemetry.copy(
                    isTesting = true,
                    lastError = null,
                    statusMessage = "Ověřuji spojení s ESP32..."
                )
            )
        }
        viewModelScope.launch {
            val result = transportClient.testConnection(config)
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        isTesting = false,
                        isConnected = result.success,
                        latencyMs = if (result.success) result.latencyMs else null,
                        statusMessage = result.message,
                        lastError = if (result.success) null else result.message
                    )
                )
            }
        }
    }

    fun sendCurrentFrameOnce() {
        val frameBytes = _uiState.value.currentOledFrame.packedBytes
        val config = _uiState.value.esp32Config
        viewModelScope.launch {
            val result = transportClient.sendFrame(config, frameBytes)
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        isConnected = result.success,
                        latencyMs = if (result.success) result.latencyMs else state.telemetry.latencyMs,
                        framesSent = if (result.success) state.telemetry.framesSent + 1 else state.telemetry.framesSent,
                        statusMessage = result.message,
                        lastError = if (result.success) null else result.message
                    )
                )
            }
        }
    }

    fun toggleLiveStream() {
        val currentlyStreaming = _uiState.value.telemetry.isStreaming
        if (currentlyStreaming) {
            _uiState.update {
                it.copy(
                    telemetry = it.telemetry.copy(
                        isStreaming = false,
                        statusMessage = "Živý přenos zastaven"
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    telemetry = it.telemetry.copy(
                        isStreaming = true,
                        lastError = null,
                        statusMessage = "Spuštěn živý přenos do ESP32..."
                    )
                )
            }
            transmitFrameIfIdle(_uiState.value.currentOledFrame.packedBytes)
        }
    }

    fun refreshPairedBluetoothDevices() {
        val devices = transportClient.listPairedBluetoothDevices()
        _uiState.update { it.copy(pairedBtDevices = devices) }
    }

    private fun reconvertActiveSource() {
        if (_uiState.value.sourceMode == RadarSourceMode.DIRECT_API) {
            reconvertCurrentApiFrame()
        } else {
            lastWebCapturedBitmap?.let { processWebCapturedBitmap(it) }
        }
    }

    private fun reconvertCurrentApiFrame() {
        val state = _uiState.value
        val frames = state.radarFrames
        if (frames.isEmpty()) return
        val idx = state.currentFrameIndex.coerceIn(0, frames.lastIndex)
        val frame = frames[idx]
        viewModelScope.launch {
            val oled = withContext(Dispatchers.Default) {
                OledFrameConverter.convertToOledFrame(
                    sourceBitmap = frame.sourceBitmap,
                    timestampLabel = frame.formattedTime,
                    frameIndex = idx,
                    totalFrames = frames.size,
                    config = state.renderConfig,
                    bufferLayout = state.esp32Config.bufferLayout,
                    isWebCapture = false
                )
            }
            _uiState.update { it.copy(currentOledFrame = oled) }
            if (_uiState.value.telemetry.isStreaming) {
                transmitFrameIfIdle(oled.packedBytes)
            }
        }
    }

    private fun transmitFrameIfIdle(packedBytes: ByteArray) {
        if (isSendingFrame) return
        isSendingFrame = true
        val config = _uiState.value.esp32Config
        viewModelScope.launch {
            try {
                val result = transportClient.sendFrame(config, packedBytes)
                _uiState.update { state ->
                    state.copy(
                        telemetry = state.telemetry.copy(
                            isConnected = result.success,
                            latencyMs = if (result.success) result.latencyMs else state.telemetry.latencyMs,
                            framesSent = if (result.success) state.telemetry.framesSent + 1 else state.telemetry.framesSent,
                            statusMessage = result.message,
                            lastError = if (result.success) null else result.message
                        )
                    )
                }
            } finally {
                isSendingFrame = false
            }
        }
    }

    private fun startAnimationLoop() {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val fps = state.renderConfig.animationFps.coerceIn(1, 6)
                val frameDelayMs = (1000L / fps)
                if (state.sourceMode == RadarSourceMode.DIRECT_API &&
                    state.isPlayingAnimation &&
                    state.radarFrames.size > 1
                ) {
                    val nextIndex = (state.currentFrameIndex + 1) % state.radarFrames.size
                    _uiState.update { it.copy(currentFrameIndex = nextIndex) }
                    reconvertCurrentApiFrame()
                    // Brief pause on the most recent frame so user clearly reads live precipitation
                    val extraPause = if (nextIndex == state.radarFrames.lastIndex) 550L else 0L
                    delay(frameDelayMs + extraPause)
                } else {
                    delay(300L)
                }
            }
        }
    }

    private fun startPeriodicRadarRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L) // Refresh live radar every 5 minutes
                if (_uiState.value.sourceMode == RadarSourceMode.DIRECT_API) {
                    refreshRadarApiData()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transportClient.closeBluetooth()
    }
}
