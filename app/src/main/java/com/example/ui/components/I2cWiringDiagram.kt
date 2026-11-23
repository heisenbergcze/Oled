package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Esp32Config
import com.example.ui.theme.LocalHardwareColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun I2cWiringDiagram(
    config: Esp32Config,
    modifier: Modifier = Modifier
) {
    val hw = LocalHardwareColors.current
    val cdWiring = stringResource(R.string.cd_wiring_diagram)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    data class WireConnection(
        val espPin: String,
        val oledPin: String,
        val color: Color,
        val note: String
    )

    val wires = listOf(
        WireConnection("3V3", "VCC", hw.wireRed, "Napájení 3.3V"),
        WireConnection("GND", "GND", hw.wireBlack, "Zem (Ground)"),
        WireConnection("GPIO ${config.sclPin}", "SCL", hw.wireYellow, "I2C Clock"),
        WireConnection("GPIO ${config.sdaPin}", "SDA", hw.wireBlue, "I2C Data")
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, hw.hairline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wiring_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "I2C ${config.controller.i2cAddressHex} · 400 kHz",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual ESP32 <-> OLED Bezier wiring diagram
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .semantics { contentDescription = cdWiring }
            ) {
                val leftBoardW = size.width * 0.28f
                val rightBoardW = size.width * 0.28f
                val rightBoardX = size.width - rightBoardW
                val boardTop = 10f
                val boardHeight = size.height - 20f

                // ESP32 DevKit Board
                drawRoundRect(
                    color = hw.pcbSubstrate,
                    topLeft = Offset(0f, boardTop),
                    size = Size(leftBoardW, boardHeight),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = hw.hairline,
                    topLeft = Offset(0f, boardTop),
                    size = Size(leftBoardW, boardHeight),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(width = 2.5f)
                )
                // ESP32 RF shield metal can
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(leftBoardW * 0.16f, boardTop + 14f),
                    size = Size(leftBoardW * 0.52f, boardHeight * 0.44f),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                // 0.96" OLED Module Board
                drawRoundRect(
                    color = hw.pcbSubstrate,
                    topLeft = Offset(rightBoardX, boardTop),
                    size = Size(rightBoardW, boardHeight),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = hw.hairline,
                    topLeft = Offset(rightBoardX, boardTop),
                    size = Size(rightBoardW, boardHeight),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(width = 2.5f)
                )
                // OLED Active Screen Miniature
                val screenLeft = rightBoardX + rightBoardW * 0.24f
                val screenW = rightBoardW * 0.64f
                drawRoundRect(
                    color = hw.oledGlassBlack,
                    topLeft = Offset(screenLeft, boardTop + 18f),
                    size = Size(screenW, boardHeight - 36f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.55f),
                    topLeft = Offset(screenLeft, boardTop + 18f),
                    size = Size(screenW, boardHeight - 36f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 1.5f)
                )

                // Draw 4 smooth jumper wires between pins
                val startX = leftBoardW
                val endX = rightBoardX
                val midX = (startX + endX) / 2f

                wires.forEachIndexed { idx, wire ->
                    val y = boardTop + 26f + idx * ((boardHeight - 52f) / 3f)
                    val path = Path().apply {
                        moveTo(startX, y)
                        cubicTo(midX - 20f, y, midX + 20f, y, endX, y)
                    }
                    drawPath(
                        path = path,
                        color = wire.color,
                        style = Stroke(width = 5.5f, cap = StrokeCap.Round)
                    )
                    // Gold pin header pads
                    drawCircle(color = hw.pcbCopperGold, radius = 6.5f, center = Offset(startX, y))
                    drawCircle(color = hw.pcbCopperGold, radius = 6.5f, center = Offset(endX, y))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pin mapping table
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                wires.forEach { w ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = w.color.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, w.color)
                            ) {
                                Text(
                                    text = w.espPin,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = w.color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = w.oledPin,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = w.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
