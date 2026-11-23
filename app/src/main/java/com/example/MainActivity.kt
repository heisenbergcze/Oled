package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.Esp32ConnectionScreen
import com.example.ui.screens.FirmwareWiringScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.theme.KarvinaOledTheme
import com.example.ui.theme.LocalHardwareColors
import com.example.viewmodel.RadarOledViewModel

private enum class AppDestination(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    RADAR("radar_dashboard", R.string.nav_radar, Icons.Filled.Radar, Icons.Outlined.Radar),
    ESP32("esp32_connection", R.string.nav_esp32, Icons.Filled.DeveloperBoard, Icons.Outlined.DeveloperBoard),
    FIRMWARE("firmware_wiring", R.string.nav_firmware, Icons.Filled.Code, Icons.Outlined.Code)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            KarvinaOledTheme {
                KarvinaOledApp()
            }
        }
    }
}

@Composable
private fun KarvinaOledApp(
    viewModel: RadarOledViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(AppDestination.RADAR) }

    BackHandler(enabled = currentTab != AppDestination.RADAR) {
        currentTab = AppDestination.RADAR
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavRail = maxWidth >= 600.dp

        if (useNavRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    AppDestination.entries.forEach { dest ->
                        val selected = currentTab == dest
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = dest },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag("nav_${dest.routeId}")
                        )
                    }
                }
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TelemetryTopBar(
                            isStreaming = uiState.telemetry.isStreaming,
                            isConnected = uiState.telemetry.isConnected,
                            transportShort = uiState.esp32Config.transportMode.labelCz,
                            onStatusClick = { currentTab = AppDestination.ESP32 }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        ScreenContent(
                            currentTab = currentTab,
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToEsp32 = { currentTab = AppDestination.ESP32 },
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TelemetryTopBar(
                        isStreaming = uiState.telemetry.isStreaming,
                        isConnected = uiState.telemetry.isConnected,
                        transportShort = uiState.esp32Config.transportMode.labelCz,
                        onStatusClick = { currentTab = AppDestination.ESP32 }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.routeId}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                ScreenContent(
                    currentTab = currentTab,
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToEsp32 = { currentTab = AppDestination.ESP32 },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    currentTab: AppDestination,
    uiState: com.example.viewmodel.RadarOledUiState,
    viewModel: RadarOledViewModel,
    onNavigateToEsp32: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (currentTab) {
        AppDestination.RADAR -> RadarScreen(
            uiState = uiState,
            viewModel = viewModel,
            onNavigateToEsp32 = onNavigateToEsp32,
            modifier = modifier
        )
        AppDestination.ESP32 -> Esp32ConnectionScreen(
            uiState = uiState,
            viewModel = viewModel,
            modifier = modifier
        )
        AppDestination.FIRMWARE -> FirmwareWiringScreen(
            uiState = uiState,
            modifier = modifier
        )
    }
}

@Composable
private fun TelemetryTopBar(
    isStreaming: Boolean,
    isConnected: Boolean,
    transportShort: String,
    onStatusClick: () -> Unit
) {
    val hw = LocalHardwareColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, hw.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.karvina_coords),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = when {
                    isStreaming -> hw.liveEmeraldContainer
                    isConnected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        isStreaming -> hw.liveEmerald
                        isConnected -> MaterialTheme.colorScheme.primary
                        else -> hw.hairline
                    }
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onStatusClick)
                    .testTag("pill_esp32_status")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isStreaming -> hw.liveEmerald
                                    isConnected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                    )
                    Text(
                        text = if (isStreaming) "LIVE · $transportShort" else transportShort,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStreaming) hw.liveEmerald else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
