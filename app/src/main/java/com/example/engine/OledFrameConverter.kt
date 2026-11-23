package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.BufferLayout
import com.example.model.DitherAlgorithm
import com.example.model.OledFrame
import com.example.model.OledRenderConfig
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object OledFrameConverter {

    const val OLED_WIDTH = 128
    const val OLED_HEIGHT = 64
    const val HUD_HEIGHT = 16
    const val BUFFER_SIZE_BYTES = (OLED_WIDTH * OLED_HEIGHT) / 8 // 1024 bytes

    // 4x4 Bayer ordered dithering threshold matrix normalized to 0..255
    private val bayer4x4 = arrayOf(
        intArrayOf(0, 128, 32, 160),
        intArrayOf(192, 64, 224, 96),
        intArrayOf(48, 176, 16, 144),
        intArrayOf(240, 112, 208, 80)
    )

    /**
     * Converts a radar source Bitmap into a 128x64 monochrome 1-bit [OledFrame].
     *
     * @param sourceBitmap Color radar bitmap (transparent background for RainViewer API, or full
     *   opaque WebView capture for Meteopress Web).
     * @param isWebCapture When true, applies HSV chromatic filtering to isolate Meteopress
     *   precipitation echoes (blue/cyan/green/yellow/orange/red/violet) from the basemap.
     */
    fun convertToOledFrame(
        sourceBitmap: Bitmap,
        timestampLabel: String,
        frameIndex: Int,
        totalFrames: Int,
        config: OledRenderConfig,
        bufferLayout: BufferLayout,
        isWebCapture: Boolean
    ): OledFrame {
        val mapTop = if (config.showHudBar) HUD_HEIGHT else 0
        val mapHeight = OLED_HEIGHT - mapTop

        // Sample source bitmap into a 128 x mapHeight intensity grid (0..255)
        val intensity = FloatArray(OLED_WIDTH * mapHeight)
        val scaled = Bitmap.createScaledBitmap(sourceBitmap, OLED_WIDTH, mapHeight, true)
        val argbPixels = IntArray(OLED_WIDTH * mapHeight)
        scaled.getPixels(argbPixels, 0, OLED_WIDTH, 0, 0, OLED_WIDTH, mapHeight)
        if (scaled !== sourceBitmap) {
            scaled.recycle()
        }

        val hsv = FloatArray(3)
        var rainyPixelCount = 0
        var maxIntensity = 0f

        for (i in argbPixels.indices) {
            val px = argbPixels[i]
            val value = if (isWebCapture) {
                extractMeteopressPrecipitationIntensity(px, hsv)
            } else {
                extractTransparentTilePrecipitationIntensity(px, hsv)
            }
            intensity[i] = value
            if (value >= config.threshold.coerceAtLeast(18)) {
                rainyPixelCount++
            }
            if (value > maxIntensity) {
                maxIntensity = value
            }
        }

        val pixels = BooleanArray(OLED_WIDTH * OLED_HEIGHT)

        // Apply selected dithering algorithm to map region
        when (config.ditherAlgorithm) {
            DitherAlgorithm.FLOYD_STEINBERG -> applyFloydSteinberg(
                intensity, pixels, mapTop, mapHeight, config.threshold
            )
            DitherAlgorithm.BAYER_4X4 -> applyBayerDither(
                intensity, pixels, mapTop, mapHeight, config.threshold
            )
            DitherAlgorithm.THRESHOLD -> applyThreshold(
                intensity, pixels, mapTop, mapHeight, config.threshold
            )
            DitherAlgorithm.HALFTONE_DOTS -> applyHalftoneDots(
                intensity, pixels, mapTop, mapHeight, config.threshold
            )
        }

        // Draw Karvina crosshair & dashed 25km/50km range rings
        if (config.showCrosshairAndRings) {
            drawKarvinaOverlay(pixels, mapTop, mapHeight, frameIndex)
        }

        val coveragePercent = ((rainyPixelCount * 100f) / (OLED_WIDTH * mapHeight)).roundToInt().coerceIn(0, 100)
        val intensityLabel = when {
            maxIntensity < 25f -> "BEZ SRAZEK"
            maxIntensity < 90f -> "SLABE"
            maxIntensity < 165f -> "MIRNE"
            maxIntensity < 220f -> "SILNE"
            else -> "BOURKA"
        }

        // Draw crisp 16px HUD header (fits exactly inside the yellow zone of dual-color 0.96" OLED!)
        if (config.showHudBar) {
            drawHudHeader(
                pixels = pixels,
                timestampLabel = timestampLabel,
                coveragePercent = coveragePercent,
                frameIndex = frameIndex,
                totalFrames = totalFrames,
                zoomKm = config.zoomRadius.radiusKm
            )
        }

        if (config.invertPixels) {
            for (i in pixels.indices) {
                pixels[i] = !pixels[i]
            }
        }

        val packedBytes = packBuffer(pixels, bufferLayout)

        return OledFrame(
            width = OLED_WIDTH,
            height = OLED_HEIGHT,
            pixels = pixels,
            packedBytes = packedBytes,
            timestampLabel = timestampLabel,
            coveragePercent = coveragePercent,
            intensityLabel = intensityLabel
        )
    }

    /**
     * Generates a valid standby/loading OLED frame showing the Karvina reticle and status.
     */
    fun createStandbyFrame(
        statusText: String,
        config: OledRenderConfig,
        bufferLayout: BufferLayout
    ): OledFrame {
        val pixels = BooleanArray(OLED_WIDTH * OLED_HEIGHT)
        if (config.showHudBar) {
            PixelFont5x7.drawText(pixels, OLED_WIDTH, OLED_HEIGHT, "KARVINA", 2, 2)
            PixelFont5x7.drawText(pixels, OLED_WIDTH, OLED_HEIGHT, "OLED", 102, 2)
            for (x in 0 until OLED_WIDTH) {
                pixels[14 * OLED_WIDTH + x] = (x % 2 == 0)
            }
        }
        val mapTop = if (config.showHudBar) HUD_HEIGHT else 0
        drawKarvinaOverlay(pixels, mapTop, OLED_HEIGHT - mapTop, 0)
        PixelFont5x7.drawText(pixels, OLED_WIDTH, OLED_HEIGHT, statusText.take(20), 6, 36)
        val packed = packBuffer(pixels, bufferLayout)
        return OledFrame(
            pixels = pixels,
            packedBytes = packed,
            timestampLabel = "--:--",
            coveragePercent = 0,
            intensityLabel = "CEKANI"
        )
    }

    /**
     * Extracts precipitation intensity (0..255) from a RainViewer / CHMI transparent PNG radar pixel.
     * Transparent pixels (alpha < 20) indicate no precipitation.
     */
    private fun extractTransparentTilePrecipitationIntensity(argb: Int, hsv: FloatArray): Float {
        val alpha = Color.alpha(argb)
        if (alpha < 22) return 0f
        Color.colorToHSV(argb, hsv)
        val hue = hsv[0] // 0..360
        val sat = hsv[1]
        val value = hsv[2]

        // Map meteorological radar spectrum (light blue -> deep blue -> cyan -> green -> yellow -> red -> magenta)
        val hueScore = when {
            hue in 275f..340f -> 1.00f // hail / extreme magenta
            hue <= 20f || hue >= 340f -> 0.95f // heavy storm red
            hue in 20f..55f -> 0.85f // orange-yellow intense rain
            hue in 55f..100f -> 0.72f // yellow-green moderate-heavy
            hue in 100f..170f -> 0.58f // green moderate
            hue in 170f..225f -> 0.45f // cyan-blue light-moderate
            hue in 225f..275f -> 0.36f // light blue drizzle
            else -> 0.40f
        }
        val alphaFactor = (alpha / 255f).coerceIn(0f, 1f)
        val raw = (hueScore * 0.72f + sat * 0.16f + value * 0.12f) * alphaFactor * 255f
        return raw.coerceIn(0f, 255f)
    }

    /**
     * Extracts precipitation echoes from an opaque Meteopress.cz WebView screenshot.
     * Meteopress basemap uses low-saturation greys/beiges/muted map greens; radar precipitation
     * overlays use saturated cyan, blue, cobalt, bright green, yellow, orange, red, and violet.
     */
    private fun extractMeteopressPrecipitationIntensity(argb: Int, hsv: FloatArray): Float {
        Color.colorToHSV(argb, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]

        // Suppress greyscale map labels, borders, dark UI panels, white backgrounds, and muted terrain
        if (sat < 0.34f || value < 0.22f) return 0f

        // Suppress typical muted olive/beige/pale yellow basemap land tones
        if (hue in 35f..95f && sat < 0.52f) return 0f

        val weight = when {
            hue in 275f..335f && sat >= 0.40f -> 1.00f // Violet / hail core
            (hue <= 18f || hue >= 335f) && sat >= 0.48f -> 0.94f // Red intense core
            hue in 18f..45f && sat >= 0.55f -> 0.84f // Orange heavy rain
            hue in 45f..72f && sat >= 0.60f -> 0.74f // Bright yellow rain
            hue in 155f..265f && sat >= 0.36f -> 0.62f // Blue / cyan / turquoise Meteopress rain scale
            hue in 105f..155f && sat >= 0.52f -> 0.54f // Saturated radar green
            else -> 0f
        }
        if (weight <= 0f) return 0f
        return (weight * (0.55f + 0.45f * sat) * 255f).coerceIn(0f, 255f)
    }

    private fun applyFloydSteinberg(
        source: FloatArray,
        dest: BooleanArray,
        mapTop: Int,
        mapHeight: Int,
        threshold: Int
    ) {
        val buf = source.copyOf()
        val cut = (255 - threshold).coerceIn(24, 235).toFloat()
        for (y in 0 until mapHeight) {
            for (x in 0 until OLED_WIDTH) {
                val idx = y * OLED_WIDTH + x
                val oldVal = buf[idx]
                if (oldVal < 14f) {
                    dest[(mapTop + y) * OLED_WIDTH + x] = false
                    continue
                }
                val isOn = oldVal >= cut
                dest[(mapTop + y) * OLED_WIDTH + x] = isOn
                val newVal = if (isOn) 255f else 0f
                val err = oldVal - newVal

                if (x + 1 < OLED_WIDTH) buf[idx + 1] += err * (7f / 16f)
                if (y + 1 < mapHeight) {
                    if (x - 1 >= 0) buf[idx + OLED_WIDTH - 1] += err * (3f / 16f)
                    buf[idx + OLED_WIDTH] += err * (5f / 16f)
                    if (x + 1 < OLED_WIDTH) buf[idx + OLED_WIDTH + 1] += err * (1f / 16f)
                }
            }
        }
    }

    private fun applyBayerDither(
        source: FloatArray,
        dest: BooleanArray,
        mapTop: Int,
        mapHeight: Int,
        threshold: Int
    ) {
        // Higher threshold slider = higher sensitivity (easier to light up precipitation)
        val sensitivityBoost = (threshold - 128) * 0.85f
        for (y in 0 until mapHeight) {
            for (x in 0 until OLED_WIDTH) {
                val valRaw = source[y * OLED_WIDTH + x]
                if (valRaw < 14f) {
                    dest[(mapTop + y) * OLED_WIDTH + x] = false
                } else {
                    val adjusted = (valRaw + sensitivityBoost).coerceIn(0f, 255f)
                    val bayerThreshold = bayer4x4[y and 3][x and 3]
                    dest[(mapTop + y) * OLED_WIDTH + x] = adjusted > bayerThreshold
                }
            }
        }
    }

    private fun applyThreshold(
        source: FloatArray,
        dest: BooleanArray,
        mapTop: Int,
        mapHeight: Int,
        threshold: Int
    ) {
        val cut = (255 - threshold).coerceIn(18, 240)
        for (y in 0 until mapHeight) {
            for (x in 0 until OLED_WIDTH) {
                dest[(mapTop + y) * OLED_WIDTH + x] = source[y * OLED_WIDTH + x] >= cut
            }
        }
    }

    private fun applyHalftoneDots(
        source: FloatArray,
        dest: BooleanArray,
        mapTop: Int,
        mapHeight: Int,
        threshold: Int
    ) {
        val sensitivityBoost = (threshold - 128) * 0.7f
        for (y in 0 until mapHeight) {
            for (x in 0 until OLED_WIDTH) {
                val raw = source[y * OLED_WIDTH + x]
                if (raw < 16f) {
                    dest[(mapTop + y) * OLED_WIDTH + x] = false
                    continue
                }
                val v = (raw + sensitivityBoost).coerceIn(0f, 255f)
                val on = when {
                    v >= 195f -> true // solid core
                    v >= 130f -> ((x + y) % 2 == 0) || (x % 3 == 0) // dense checkerboard
                    v >= 75f -> (x % 2 == 0) && (y % 2 == 0) // medium grid
                    v >= 35f -> (x % 3 == 0) && (y % 3 == 0) // light drizzle dots
                    else -> false
                }
                dest[(mapTop + y) * OLED_WIDTH + x] = on
            }
        }
    }

    private fun drawKarvinaOverlay(
        pixels: BooleanArray,
        mapTop: Int,
        mapHeight: Int,
        frameIndex: Int
    ) {
        val cx = OLED_WIDTH / 2
        val cy = mapTop + mapHeight / 2

        // Concentric dotted radar range rings around Karvina
        val radii = intArrayOf(12, 23)
        for (r in radii) {
            val steps = r * 5
            for (s in 0 until steps step 2) {
                val angle = (2.0 * Math.PI * s) / steps
                val rx = cx + (cos(angle) * r).roundToInt()
                val ry = cy + (sin(angle) * r).roundToInt()
                if (rx in 0 until OLED_WIDTH && ry in mapTop until OLED_HEIGHT) {
                    pixels[ry * OLED_WIDTH + rx] = true
                }
            }
        }

        // Karvina precision crosshair (+) at center
        for (d in -3..3) {
            if (d == 0) continue
            setPixelSafe(pixels, cx + d, cy, true, mapTop)
            setPixelSafe(pixels, cx, cy + d, true, mapTop)
        }
        // Subtle blinking center pixel so user sees live animation heartbeat on OLED
        setPixelSafe(pixels, cx, cy, frameIndex % 2 == 0, mapTop)

        // Corner compass tick "N"
        PixelFont5x7.drawText(pixels, OLED_WIDTH, OLED_HEIGHT, "N", OLED_WIDTH - 8, mapTop + 2, true)
    }

    private fun drawHudHeader(
        pixels: BooleanArray,
        timestampLabel: String,
        coveragePercent: Int,
        frameIndex: Int,
        totalFrames: Int,
        zoomKm: Int
    ) {
        // Clear top 16px HUD strip cleanly
        for (y in 0 until HUD_HEIGHT) {
            for (x in 0 until OLED_WIDTH) {
                pixels[y * OLED_WIDTH + x] = false
            }
        }
        // Left: Location tag
        PixelFont5x7.drawText(pixels, OLED_WIDTH, OLED_HEIGHT, "KARVINA", 1, 2, true)
        // Middle: Frame timestamp
        PixelFont5x7.drawText(pixels, OLED_WIDTH, OLED_HEIGHT, timestampLabel.take(5), 52, 2, true)
        // Right: Radius or Rain %
        val rightTag = if (coveragePercent > 0) "${coveragePercent}%" else "${zoomKm}K"
        PixelFont5x7.drawText(pixels, OLED_WIDTH, OLED_HEIGHT, rightTag, OLED_WIDTH - (rightTag.length * 6), 2, true)

        // Row 12-13: Animation progress scrubber bar on the OLED itself!
        val barWidth = OLED_WIDTH - 4
        val progressPixels = if (totalFrames > 1) {
            ((frameIndex + 1) * barWidth) / totalFrames
        } else {
            barWidth
        }
        for (x in 2 until OLED_WIDTH - 2) {
            val filled = (x - 2) < progressPixels
            pixels[12 * OLED_WIDTH + x] = filled
            pixels[13 * OLED_WIDTH + x] = filled && (x % 2 == 0)
        }
        // Row 15: subtle separator hairline
        for (x in 0 until OLED_WIDTH step 2) {
            pixels[15 * OLED_WIDTH + x] = true
        }
    }

    private fun setPixelSafe(pixels: BooleanArray, x: Int, y: Int, on: Boolean, minY: Int) {
        if (x in 0 until OLED_WIDTH && y in minY until OLED_HEIGHT) {
            pixels[y * OLED_WIDTH + x] = on
        }
    }

    /**
     * Packs 128x64 monochrome pixel array into exactly 1024 bytes for SSD1306 / SH1106.
     */
    fun packBuffer(pixels: BooleanArray, layout: BufferLayout): ByteArray {
        val out = ByteArray(BUFFER_SIZE_BYTES)
        when (layout) {
            BufferLayout.HORIZONTAL_MSB -> {
                // Standard Adafruit_SSD1306::drawBitmap(0, 0, buf, 128, 64, 1)
                var byteIndex = 0
                for (y in 0 until OLED_HEIGHT) {
                    for (xByte in 0 until (OLED_WIDTH / 8)) {
                        var b = 0
                        for (bit in 0 until 8) {
                            val x = xByte * 8 + bit
                            if (pixels[y * OLED_WIDTH + x]) {
                                b = b or (1 shl (7 - bit))
                            }
                        }
                        out[byteIndex++] = b.toByte()
                    }
                }
            }
            BufferLayout.PAGE_VERTICAL -> {
                // Native SSD1306 GDDRAM format: 8 pages x 128 columns, LSB = top pixel in page
                for (page in 0 until 8) {
                    for (x in 0 until OLED_WIDTH) {
                        var b = 0
                        for (bit in 0 until 8) {
                            val y = page * 8 + bit
                            if (pixels[y * OLED_WIDTH + x]) {
                                b = b or (1 shl bit)
                            }
                        }
                        out[page * OLED_WIDTH + x] = b.toByte()
                    }
                }
            }
        }
        return out
    }

    /**
     * Exports the 1024-byte OLED framebuffer as a ready-to-paste C PROGMEM array for ESP32 testing.
     */
    fun exportAsCHeader(frame: OledFrame, layout: BufferLayout): String {
        val sb = StringBuilder()
        sb.appendLine("// Karvina Radar OLED 128x64 (${layout.codeTag}) - ${frame.timestampLabel}")
        sb.appendLine("// Velikost: ${frame.packedBytes.size} bajtu")
        sb.appendLine("const unsigned char PROGMEM radar_karvina_frame[1024] = {")
        frame.packedBytes.forEachIndexed { idx, byteVal ->
            if (idx % 16 == 0) sb.append("  ")
            sb.append(String.format("0x%02X", byteVal.toInt() and 0xFF))
            if (idx < frame.packedBytes.lastIndex) sb.append(", ")
            if (idx % 16 == 15) sb.appendLine()
        }
        sb.appendLine("};")
        return sb.toString()
    }
}
