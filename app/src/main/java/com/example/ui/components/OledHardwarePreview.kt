package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.OledColorVariant
import com.example.model.OledController
import com.example.model.OledFrame
import com.example.ui.theme.LocalHardwareColors
import com.example.ui.theme.MonoFontFamily

/**
 * Authentic 0.96" I2C OLED (SSD1306 / SH1106 128x64) hardware bezel & pixel-matrix renderer.
 * Displays the exact 1-bit pixel buffer transmitted to the ESP32 microcontroller.
 */
@Composable
fun OledHardwarePreview(
    oledFrame: OledFrame,
    colorVariant: OledColorVariant,
    controller: OledController,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val hw = LocalHardwareColors.current
    val cdText = stringResource(R.string.cd_oled_preview)

    val pulseTransition = rememberInfiniteTransition(label = "stream_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = hw.pcbSubstrate,
        border = BorderStroke(1.2.dp, hw.hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top 4-pin I2C PCB header silkscreen row (GND VCC SCL SDA)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isStreaming) hw.liveEmerald.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.outline)
                    )
                    Text(
                        text = stringResource(R.string.oled_preview_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Authentic 4-pin I2C solder pads
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("GND", "VCC", "SCL", "SDA").forEach { pinLabel ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(hw.oledGlassBlack)
                                    .border(1.5.dp, hw.pcbCopperGold, CircleShape)
                            )
                            Text(
                                text = pinLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = hw.pcbCopperGold.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recessed 128x64 OLED Glass Display Panel (exact 2:1 aspect ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(hw.oledGlassBlack)
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                                Color.Black
                            )
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(6.dp)
                    .semantics { contentDescription = cdText }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cols = oledFrame.width
                    val rows = oledFrame.height
                    val cellW = size.width / cols.toFloat()
                    val cellH = size.height / rows.toFloat()
                    val gapX = (cellW * 0.14f).coerceAtMost(0.85f)
                    val gapY = (cellH * 0.14f).coerceAtMost(0.85f)
                    val drawW = (cellW - gapX).coerceAtLeast(1f)
                    val drawH = (cellH - gapY).coerceAtLeast(1f)

                    // Subtle unlit pixel matrix backdrop
                    drawRoundRect(
                        color = hw.oledPixelOff.copy(alpha = 0.42f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    val pixels = oledFrame.pixels
                    for (y in 0 until rows) {
                        val activeColor = when (colorVariant) {
                            OledColorVariant.DUAL_YELLOW_BLUE ->
                                if (y < 16) hw.oledZoneYellow else hw.oledZoneBlue
                            OledColorVariant.ICE_CYAN -> hw.oledIceCyan
                            OledColorVariant.PURE_WHITE -> hw.oledPureWhite
                        }
                        val rowOffset = y * cols
                        val py = y * cellH
                        for (x in 0 until cols) {
                            if (pixels[rowOffset + x]) {
                                val px = x * cellW
                                drawRect(
                                    color = activeColor,
                                    topLeft = Offset(px, py),
                                    size = Size(drawW, drawH)
                                )
                            }
                        }
                    }

                    // Hairline physical boundary gap between yellow & blue zones on dual-color 0.96" OLED
                    if (colorVariant == OledColorVariant.DUAL_YELLOW_BLUE) {
                        val splitY = 16f * cellH
                        drawLine(
                            color = Color.Black.copy(alpha = 0.65f),
                            start = Offset(0f, splitY),
                            end = Offset(size.width, splitY),
                            strokeWidth = 1.5f
                        )
                    }

                    // Subtle glass reflection sheen along top bezel
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.25f
                        ),
                        size = Size(size.width, size.height * 0.25f)
                    )
                    drawRoundRect(
                        color = hw.hairline.copy(alpha = 0.3f),
                        style = Stroke(width = 1f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware telemetry readout strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryChip(
                    label = "I2C",
                    value = "${controller.i2cAddressHex} · 1024 B"
                )
                TelemetryChip(
                    label = "SRÁŽKY",
                    value = "${oledFrame.coveragePercent}% · ${oledFrame.intensityLabel}",
                    highlight = oledFrame.coveragePercent > 0
                )
                TelemetryChip(
                    label = "ČAS",
                    value = oledFrame.timestampLabel
                )
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
