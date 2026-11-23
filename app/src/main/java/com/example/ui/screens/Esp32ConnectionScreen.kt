package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.BufferLayout
import com.example.model.OledController
import com.example.model.TransportMode
import com.example.ui.theme.LocalHardwareColors
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.RadarOledUiState
import com.example.viewmodel.RadarOledViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Esp32ConnectionScreen(
    uiState: RadarOledUiState,
    viewModel: RadarOledViewModel,
    modifier: Modifier = Modifier
) {
    val hw = LocalHardwareColors.current
    val context = LocalContext.current
    val config = uiState.esp32Config

    var hasBtPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        hasBtPermission = granted
        if (granted) {
            viewModel.refreshPairedBluetoothDevices()
        }
    }

    LaunchedEffect(config.transportMode, hasBtPermission) {
        if (config.transportMode == TransportMode.BLUETOOTH_SPP && hasBtPermission) {
            viewModel.refreshPairedBluetoothDevices()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Transport Mode Card
        item(key = "transport_card") {
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
                            imageVector = Icons.Filled.Router,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.esp32_screen_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        text = stringResource(R.string.transport_mode_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        TransportMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = config.transportMode == mode,
                                onClick = {
                                    viewModel.updateEsp32Config { it.copy(transportMode = mode) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = TransportMode.entries.size
                                ),
                                icon = {
                                    Icon(
                                        imageVector = if (mode == TransportMode.BLUETOOTH_SPP) {
                                            Icons.Filled.Bluetooth
                                        } else {
                                            Icons.Filled.Wifi
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("transport_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    text = mode.labelCz,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    if (config.transportMode != TransportMode.BLUETOOTH_SPP) {
                        // Wi-Fi HTTP / UDP fields
                        OutlinedTextField(
                            value = config.host,
                            onValueChange = { host ->
                                viewModel.updateEsp32Config { it.copy(host = host) }
                            },
                            label = { Text(stringResource(R.string.esp32_host_label)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_esp32_host")
                        )

                        // Quick IP presets
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("192.168.1.50", "192.168.4.1", "karvina-oled.local").forEach { preset ->
                                FilterChip(
                                    selected = config.host == preset,
                                    onClick = { viewModel.updateEsp32Config { it.copy(host = preset) } },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        val isHttp = config.transportMode == TransportMode.WIFI_HTTP
                        OutlinedTextField(
                            value = if (isHttp) config.httpPort.toString() else config.udpPort.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter { it.isDigit() }.toIntOrNull()
                                if (parsed != null && parsed in 1..65535) {
                                    viewModel.updateEsp32Config {
                                        if (isHttp) it.copy(httpPort = parsed) else it.copy(udpPort = parsed)
                                    }
                                }
                            },
                            label = {
                                Text(
                                    if (isHttp) "HTTP Port (výchozí 80)" else "UDP Port (výchozí 4210)"
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_esp32_port")
                        )
                    } else {
                        // Bluetooth Classic SPP selector
                        if (!hasBtPermission) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.esp32_bt_permission_needed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                btPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.BLUETOOTH_CONNECT,
                                                        Manifest.permission.BLUETOOTH_SCAN
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("btn_grant_bt_permission")
                                    ) {
                                        Text(stringResource(R.string.esp32_bt_grant_permission))
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.refreshPairedBluetoothDevices() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_refresh_bt_devices")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.esp32_bt_scan))
                            }

                            if (uiState.pairedBtDevices.isEmpty()) {
                                Text(
                                    text = "Žádná spárovaná zařízení. Nejprve spárujte ESP32 (Karvina-Radar-OLED) v nastavení Bluetooth telefonu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    uiState.pairedBtDevices.forEach { dev ->
                                        val selected = dev.address == config.btDeviceAddress
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) MaterialTheme.colorScheme.primary else hw.hairline
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateEsp32Config {
                                                        it.copy(
                                                            btDeviceName = dev.name,
                                                            btDeviceAddress = dev.address
                                                        )
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = dev.name,
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Text(
                                                        text = dev.address,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Test connection button
                    Button(
                        onClick = { viewModel.testEsp32Connection() },
                        enabled = !uiState.telemetry.isTesting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_test_esp32"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.telemetry.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.esp32_test_connection))
                    }

                    // Connection telemetry feedback box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (uiState.telemetry.isConnected) {
                            hw.liveEmeraldContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = uiState.telemetry.statusMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (uiState.telemetry.isConnected) {
                                    hw.liveEmerald
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            val latency = uiState.telemetry.latencyMs
                            if (latency != null) {
                                Text(
                                    text = "Odezva: ${latency} ms · Odeslaných snímků: ${uiState.telemetry.framesSent}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. I2C OLED Hardware Configuration Card
        item(key = "oled_hardware_card") {
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
                            imageVector = Icons.Filled.DeveloperBoard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.esp32_oled_hardware_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Controller chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.oled_driver_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OledController.entries.forEach { ctrl ->
                                FilterChip(
                                    selected = config.controller == ctrl,
                                    onClick = { viewModel.updateEsp32Config { it.copy(controller = ctrl) } },
                                    label = { Text(ctrl.labelCz) },
                                    modifier = Modifier.testTag("chip_ctrl_${ctrl.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Buffer layout chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.oled_buffer_format_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BufferLayout.entries.forEach { layout ->
                                FilterChip(
                                    selected = config.bufferLayout == layout,
                                    onClick = { viewModel.updateEsp32Config { it.copy(bufferLayout = layout) } },
                                    label = { Text(layout.labelCz) },
                                    modifier = Modifier.testTag("chip_layout_${layout.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // I2C SDA / SCL GPIO pins
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = config.sdaPin.toString(),
                            onValueChange = { raw ->
                                raw.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 0..48 }?.let { pin ->
                                    viewModel.updateEsp32Config { it.copy(sdaPin = pin) }
                                }
                            },
                            label = { Text(stringResource(R.string.i2c_sda_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_i2c_sda")
                        )
                        OutlinedTextField(
                            value = config.sclPin.toString(),
                            onValueChange = { raw ->
                                raw.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 0..48 }?.let { pin ->
                                    viewModel.updateEsp32Config { it.copy(sclPin = pin) }
                                }
                            },
                            label = { Text(stringResource(R.string.i2c_scl_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_i2c_scl")
                        )
                    }

                    // Quick ESP32 board I2C pin presets
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Předvolby desek:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = config.sdaPin == 21 && config.sclPin == 22,
                            onClick = { viewModel.updateEsp32Config { it.copy(sdaPin = 21, sclPin = 22) } },
                            label = { Text("ESP32 DevKit (21/22)") }
                        )
                        FilterChip(
                            selected = config.sdaPin == 8 && config.sclPin == 9,
                            onClick = { viewModel.updateEsp32Config { it.copy(sdaPin = 8, sclPin = 9) } },
                            label = { Text("ESP32-C3 / S3 (8/9)") }
                        )
                        FilterChip(
                            selected = config.sdaPin == 4 && config.sclPin == 15,
                            onClick = { viewModel.updateEsp32Config { it.copy(sdaPin = 4, sclPin = 15) } },
                            label = { Text("Heltec OLED (4/15)") }
                        )
                    }
                }
            }
        }
    }
}
