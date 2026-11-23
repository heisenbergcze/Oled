package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class HardwareColors(
    val hairline: Color = HairlineSteel,
    val liveEmerald: Color = TelemetryEmerald,
    val liveEmeraldContainer: Color = TelemetryEmeraldContainer,
    val radarAmber: Color = RadarAmber,
    val radarAmberContainer: Color = RadarAmberContainer,
    val pcbSubstrate: Color = PcbSubstrate,
    val pcbCopperGold: Color = PcbCopperGold,
    val oledGlassBlack: Color = OledGlassBlack,
    val oledPixelOff: Color = OledPixelOff,
    val oledZoneYellow: Color = OledZoneYellow,
    val oledZoneBlue: Color = OledZoneBlue,
    val oledIceCyan: Color = OledIceCyan,
    val oledPureWhite: Color = OledPureWhite,
    val wireRed: Color = WireRed,
    val wireBlack: Color = WireBlack,
    val wireYellow: Color = WireYellow,
    val wireBlue: Color = WireBlue
)

val LocalHardwareColors = staticCompositionLocalOf { HardwareColors() }

private val KarvinaDarkColorScheme = darkColorScheme(
    primary = OledCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = OledCyanContainer,
    onPrimaryContainer = OledCyan,
    secondary = RadarAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = RadarAmberContainer,
    onSecondaryContainer = RadarAmber,
    tertiary = TelemetryEmerald,
    onTertiary = ObsidianVoid,
    tertiaryContainer = TelemetryEmeraldContainer,
    onTertiaryContainer = TelemetryEmerald,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = SlateGraphite,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedGraphite,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = SlateGraphite,
    surfaceContainer = ElevatedGraphite,
    surfaceContainerHigh = SurfaceOverlay,
    outline = HairlineSteel,
    outlineVariant = Color(0xFF1D2938),
    error = StormCoral,
    onError = ObsidianVoid,
    errorContainer = StormCoralContainer,
    onErrorContainer = StormCoral
)

@Composable
fun KarvinaOledTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalHardwareColors provides HardwareColors()) {
        MaterialTheme(
            colorScheme = KarvinaDarkColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
