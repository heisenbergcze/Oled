package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.Esp32FirmwareGenerator
import com.example.ui.components.I2cWiringDiagram
import com.example.ui.theme.LocalHardwareColors
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.RadarOledUiState

@Composable
fun FirmwareWiringScreen(
    uiState: RadarOledUiState,
    modifier: Modifier = Modifier
) {
    val hw = LocalHardwareColors.current
    val context = LocalContext.current
    val firmwareCode = remember(uiState.esp32Config) { uiState.generatedFirmwareCode }
    val cHeaderCode = remember(uiState.currentOledFrame, uiState.esp32Config.bufferLayout) {
        uiState.generatedCHeader
    }
    val requiredLibs = remember(uiState.esp32Config) {
        Esp32FirmwareGenerator.requiredLibraries(uiState.esp32Config)
    }

    var copiedBanner by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        copiedBanner = "$label zkopírováno do schránky"
    }

    fun shareText(subject: String, text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(sendIntent, subject))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (copiedBanner != null) {
            item(key = "copied_notice") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = hw.liveEmeraldContainer,
                    border = BorderStroke(1.dp, hw.liveEmerald)
                ) {
                    Text(
                        text = copiedBanner ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = hw.liveEmerald,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 1. Visual I2C Wiring Diagram (ESP32 <-> 0.96" OLED)
        item(key = "wiring_diagram") {
            I2cWiringDiagram(config = uiState.esp32Config)
        }

        // 2. Required Arduino IDE / PlatformIO Libraries
        item(key = "required_libs") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, hw.hairline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LibraryBooks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Potřebné knihovny (Arduino IDE / PlatformIO)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    requiredLibs.forEach { lib ->
                        Text(
                            text = "• $lib",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Dynamically Generated Ready-to-Flash ESP32 Sketch
        item(key = "esp32_sketch") {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.firmware_code_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Text(
                        text = "Přizpůsobeno pro: ${uiState.esp32Config.transportMode.labelCz} · ${uiState.esp32Config.controller.labelCz} · SDA=${uiState.esp32Config.sdaPin}, SCL=${uiState.esp32Config.sclPin}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { copyToClipboard("ESP32 Firmware (.ino)", firmwareCode) },
                            modifier = Modifier.testTag("btn_copy_firmware")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.copy_code))
                        }
                        OutlinedButton(
                            onClick = { shareText("karvina_radar_oled.ino", firmwareCode) },
                            modifier = Modifier.testTag("btn_share_firmware")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.share_code))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = hw.oledGlassBlack,
                        border = BorderStroke(1.dp, hw.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = firmwareCode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }
        }

        // 4. Current Karvina Radar Frame C PROGMEM Byte Array Export
        item(key = "c_header_export") {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DataObject,
                            contentDescription = null,
                            tint = hw.radarAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.export_c_header_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { copyToClipboard("C PROGMEM pole (1024 B)", cHeaderCode) },
                            modifier = Modifier.testTag("btn_copy_c_array")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kopírovat 1024 B pole")
                        }
                        OutlinedButton(
                            onClick = { shareText("radar_karvina_frame.h", cHeaderCode) },
                            modifier = Modifier.testTag("btn_share_c_array")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.share_code))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = hw.oledGlassBlack,
                        border = BorderStroke(1.dp, hw.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cHeaderCode.lines().take(12).joinToString("\n") + "\n  // ... (celkem 1024 bajtů)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontSize = 10.sp,
                                lineHeight = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
