package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DitherAlgorithm
import com.example.model.OledColorVariant
import com.example.model.RadarLoadState
import com.example.model.RadarSourceMode
import com.example.model.RadarZoomRadius
import com.example.ui.components.MeteopressWebCaptureCard
import com.example.ui.components.OledHardwarePreview
import com.example.ui.theme.LocalHardwareColors
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.RadarOledUiState
import com.example.viewmodel.RadarOledViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarScreen(
    uiState: RadarOledUiState,
    viewModel: RadarOledViewModel,
    onNavigateToEsp32: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hw = LocalHardwareColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Source Mode Selector (Radar API Animation vs Meteopress Live WebView)
        item(key = "source_selector") {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("source_mode_segmented")
            ) {
                RadarSourceMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = uiState.sourceMode == mode,
                        onClick = { viewModel.setSourceMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = RadarSourceMode.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = if (mode == RadarSourceMode.DIRECT_API) {
                                    Icons.Filled.Radar
                                } else {
                                    Icons.Filled.Language
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("source_mode_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.labelCz,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 2. Authentic 0.96" I2C OLED Hardware Live Preview
        item(key = "oled_preview") {
            OledHardwarePreview(
                oledFrame = uiState.currentOledFrame,
                colorVariant = uiState.renderConfig.oledColorVariant,
                controller = uiState.esp32Config.controller,
                isStreaming = uiState.telemetry.isStreaming
            )
        }

        // 3. Primary ESP32 Live Stream Controls & Status Bar
        item(key = "stream_controls") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (uiState.telemetry.isStreaming) hw.liveEmerald.copy(alpha = 0.65f) else hw.hairline
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.toggleLiveStream() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_toggle_stream"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.telemetry.isStreaming) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.telemetry.isStreaming) Icons.Filled.Stop else Icons.Filled.Cast,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (uiState.telemetry.isStreaming) R.string.stream_stop else R.string.stream_start
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.sendCurrentFrameOnce() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_send_single_frame")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = stringResource(R.string.send_single_frame)
                            )
                        }
                    }

                    // Live transport telemetry line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            uiState.telemetry.lastError != null -> MaterialTheme.colorScheme.error
                                            uiState.telemetry.isConnected -> hw.liveEmerald
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                            )
                            Text(
                                text = uiState.telemetry.statusMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (uiState.telemetry.lastError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }

                        Text(
                            text = "${uiState.telemetry.framesSent} sn.",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (uiState.telemetry.lastError != null) {
                        OutlinedButton(
                            onClick = onNavigateToEsp32,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_configure_esp32_hint"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Nastavit připojení ESP32 (${uiState.esp32Config.transportMode.labelCz})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // 4. Radar Source Card (Direct API Color Map OR Meteopress.cz Live WebView)
        item(key = "radar_source_view") {
            if (uiState.sourceMode == RadarSourceMode.METEOPRESS_WEB) {
                MeteopressWebCaptureCard(
                    url = uiState.meteopressUrl,
                    autoCaptureEnabled = uiState.autoWebCapture,
                    captureFps = uiState.renderConfig.animationFps,
                    captureCount = uiState.webCaptureCounter,
                    onUrlChange = viewModel::setMeteopressUrl,
                    onAutoCaptureToggle = viewModel::setAutoWebCapture,
                    onFrameCaptured = viewModel::onWebViewFrameCaptured
                )
            } else {
                DirectRadarApiCard(
                    uiState = uiState,
                    onRefresh = viewModel::refreshRadarApiData,
                    onTogglePlay = viewModel::togglePlayPause,
                    onSeek = viewModel::seekToFrame,
                    onZoomSelected = viewModel::updateZoomRadius
                )
            }
        }

        // 5. OLED Image Processing & Dithering Pipeline Controls
        item(key = "image_pipeline") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, hw.hairline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.image_pipeline_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Dither algorithm chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.dither_mode_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DitherAlgorithm.entries.forEach { algo ->
                                FilterChip(
                                    selected = uiState.renderConfig.ditherAlgorithm == algo,
                                    onClick = {
                                        viewModel.updateRenderConfig { it.copy(ditherAlgorithm = algo) }
                                    },
                                    label = { Text(algo.shortLabel) },
                                    modifier = Modifier.testTag("chip_dither_${algo.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Sensitivity / Threshold slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.threshold_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.renderConfig.threshold}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = uiState.renderConfig.threshold.toFloat(),
                            onValueChange = { v ->
                                viewModel.updateRenderConfig { it.copy(threshold = v.roundToInt()) }
                            },
                            valueRange = 20f..220f,
                            modifier = Modifier.testTag("slider_threshold")
                        )
                    }

                    // Animation FPS slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.fps_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.renderConfig.animationFps} FPS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = uiState.renderConfig.animationFps.toFloat(),
                            onValueChange = { v ->
                                viewModel.updateRenderConfig { it.copy(animationFps = v.roundToInt()) }
                            },
                            valueRange = 1f..6f,
                            steps = 4,
                            modifier = Modifier.testTag("slider_fps")
                        )
                    }

                    // OLED Color Variant (Dual Yellow/Blue, Ice Cyan, Pure White)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.oled_color_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OledColorVariant.entries.forEach { variant ->
                                FilterChip(
                                    selected = uiState.renderConfig.oledColorVariant == variant,
                                    onClick = {
                                        viewModel.updateRenderConfig { it.copy(oledColorVariant = variant) }
                                    },
                                    label = { Text(variant.labelCz) },
                                    modifier = Modifier.testTag("chip_oled_color_${variant.name.lowercase()}")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = hw.hairline)

                    // Boolean toggles
                    PipelineSwitchRow(
                        label = stringResource(R.string.hud_toggle_label),
                        checked = uiState.renderConfig.showHudBar,
                        testTag = "switch_hud_bar",
                        onCheckedChange = { checked ->
                            viewModel.updateRenderConfig { it.copy(showHudBar = checked) }
                        }
                    )
                    PipelineSwitchRow(
                        label = stringResource(R.string.crosshair_toggle_label),
                        checked = uiState.renderConfig.showCrosshairAndRings,
                        testTag = "switch_crosshair",
                        onCheckedChange = { checked ->
                            viewModel.updateRenderConfig { it.copy(showCrosshairAndRings = checked) }
                        }
                    )
                    PipelineSwitchRow(
                        label = stringResource(R.string.invert_toggle_label),
                        checked = uiState.renderConfig.invertPixels,
                        testTag = "switch_invert",
                        onCheckedChange = { checked ->
                            viewModel.updateRenderConfig { it.copy(invertPixels = checked) }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectRadarApiCard(
    uiState: RadarOledUiState,
    onRefresh: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onZoomSelected: (RadarZoomRadius) -> Unit
) {
    val hw = LocalHardwareColors.current
    val currentFrame = uiState.radarFrames.getOrNull(uiState.currentFrameIndex)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, hw.hairline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.karvina_location_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val subtitle = when (val load = uiState.radarLoadState) {
                        is RadarLoadState.Loading -> "Načítám živé srážkové dlaždice..."
                        is RadarLoadState.Ready -> "${load.sourceName} · aktualizováno ${load.updatedLabel}"
                        is RadarLoadState.Error -> load.message
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.radarLoadState is RadarLoadState.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("btn_refresh_radar")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_radar),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Color source radar viewport (2:1 matching Karvina crop)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(hw.oledGlassBlack),
                contentAlignment = Alignment.Center
            ) {
                // Technical radar coordinate gridlines beneath the transparent radar PNG
                val gridColor = hw.hairline.copy(alpha = 0.55f)
                val amberColor = hw.radarAmber
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(gridColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)
                    drawCircle(
                        color = gridColor,
                        radius = size.height * 0.24f,
                        center = Offset(cx, cy),
                        style = Stroke(
                            width = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    )
                    drawCircle(
                        color = gridColor,
                        radius = size.height * 0.44f,
                        center = Offset(cx, cy),
                        style = Stroke(
                            width = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    )
                }

                when (val load = uiState.radarLoadState) {
                    is RadarLoadState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                    is RadarLoadState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = load.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onRefresh) {
                                Text(stringResource(R.string.refresh_radar))
                            }
                        }
                    }
                    is RadarLoadState.Ready -> {
                        val bmp = currentFrame?.sourceBitmap
                        if (bmp != null) {
                            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Barevný srážkový radar pro Karvinou",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Karvina pinpoint marker on top of radar map
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            drawCircle(color = amberColor, radius = 5f, center = Offset(cx, cy))
                            drawCircle(
                                color = Color.Black,
                                radius = 7f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }

                // Timestamp + Forecast pill overlay
                if (currentFrame != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                        border = BorderStroke(1.dp, hw.hairline)
                    ) {
                        val tag = if (currentFrame.isForecast) {
                            "${currentFrame.formattedTime} · PŘEDPOVĚĎ"
                        } else {
                            "${currentFrame.formattedTime} · ŽIVĚ"
                        }
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (currentFrame.isForecast) hw.radarAmber else hw.liveEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f)
                ) {
                    Text(
                        text = "KARVINÁ · ${uiState.renderConfig.zoomRadius.labelCz}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Animation Play/Pause + Frame Scrubber
            if (uiState.radarFrames.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_play_pause_radar")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlayingAnimation) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause)
                        )
                    }

                    Slider(
                        value = uiState.currentFrameIndex.toFloat(),
                        onValueChange = { onSeek(it.roundToInt()) },
                        valueRange = 0f..(uiState.radarFrames.lastIndex.coerceAtLeast(1)).toFloat(),
                        steps = (uiState.radarFrames.size - 2).coerceAtLeast(0),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_radar_frame")
                    )

                    Text(
                        text = "${uiState.currentFrameIndex + 1}/${uiState.radarFrames.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Zoom radius chips around Karvina
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.zoom_radius_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RadarZoomRadius.entries.forEach { radius ->
                        FilterChip(
                            selected = uiState.renderConfig.zoomRadius == radius,
                            onClick = { onZoomSelected(radius) },
                            label = { Text(radius.labelCz) },
                            modifier = Modifier.testTag("chip_zoom_${radius.radiusKm}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineSwitchRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
