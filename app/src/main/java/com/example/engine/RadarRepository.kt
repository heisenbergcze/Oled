package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.example.model.KarvinaGeo
import com.example.model.RadarFrame
import com.example.model.RadarZoomRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

class RadarRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val czechTimeFormat = SimpleDateFormat("HH:mm", Locale("cs", "CZ")).apply {
        timeZone = TimeZone.getTimeZone("Europe/Prague")
    }

    data class RadarFetchResult(
        val frames: List<RadarFrame>,
        val sourceLabel: String,
        val updatedLabel: String
    )

    /**
     * Fetches the live sequence of real precipitation radar frames centered accurately on
     * Karvina (49.8540°N, 18.5410°E) from RainViewer Public Weather Maps API.
     */
    suspend fun fetchKarvinaRadarAnimation(zoomRadius: RadarZoomRadius): RadarFetchResult =
        withContext(Dispatchers.IO) {
            val metaRequest = Request.Builder()
                .url("https://api.rainviewer.com/public/weather-maps.json")
                .header("User-Agent", "KarvinaRadarOLED-Android/1.0")
                .build()

            val metaJson = client.newCall(metaRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Chyba radarového API (HTTP ${response.code})")
                }
                response.body?.string() ?: throw IllegalStateException("Prázdná odpověď radarového API")
            }

            val root = JSONObject(metaJson)
            val host = root.getString("host")
            val radarObj = root.getJSONObject("radar")
            val pastArray = radarObj.optJSONArray("past")
            val nowcastArray = radarObj.optJSONArray("nowcast")

            data class FrameSpec(val timeSec: Long, val path: String, val isForecast: Boolean)

            val specs = mutableListOf<FrameSpec>()
            if (pastArray != null) {
                val startIdx = (pastArray.length() - 8).coerceAtLeast(0)
                for (i in startIdx until pastArray.length()) {
                    val item = pastArray.getJSONObject(i)
                    specs.add(FrameSpec(item.getLong("time"), item.getString("path"), false))
                }
            }
            if (nowcastArray != null) {
                val count = nowcastArray.length().coerceAtMost(3)
                for (i in 0 until count) {
                    val item = nowcastArray.getJSONObject(i)
                    specs.add(FrameSpec(item.getLong("time"), item.getString("path"), true))
                }
            }

            if (specs.isEmpty()) {
                throw IllegalStateException("Žádné aktuální radarové snímky nejsou k dispozici")
            }

            val z = zoomRadius.tileZoom
            // Compute exact fractional Web Mercator tile coordinates for Karvina
            val (exactX, exactY) = latLonToExactTile(KarvinaGeo.LATITUDE, KarvinaGeo.LONGITUDE, z)

            // Fetch a 2x2 tile neighborhood around Karvina so the city sits dead-center in the crop
            val baseTileX = floor(exactX - 0.5).toInt()
            val baseTileY = floor(exactY - 0.5).toInt()
            val karvinaPxInStitchX = ((exactX - baseTileX) * TILE_SIZE).roundToInt()
            val karvinaPxInStitchY = ((exactY - baseTileY) * TILE_SIZE).roundToInt()

            val frames = coroutineScope {
                specs.map { spec ->
                    async {
                        val bitmap = fetchCenteredRadarBitmap(
                            host = host,
                            path = spec.path,
                            z = z,
                            baseTileX = baseTileX,
                            baseTileY = baseTileY,
                            centerPxX = karvinaPxInStitchX,
                            centerPxY = karvinaPxInStitchY
                        )
                        if (bitmap != null) {
                            RadarFrame(
                                timestampEpochSec = spec.timeSec,
                                formattedTime = czechTimeFormat.format(Date(spec.timeSec * 1000L)),
                                sourceBitmap = bitmap,
                                isForecast = spec.isForecast
                            )
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (frames.isEmpty()) {
                throw IllegalStateException("Nepodařilo se stáhnout radarové dlaždice pro Karvinou")
            }

            val updated = czechTimeFormat.format(Date())
            RadarFetchResult(
                frames = frames,
                sourceLabel = "RainViewer Live · z$z (${zoomRadius.labelCz})",
                updatedLabel = updated
            )
        }

    private suspend fun fetchCenteredRadarBitmap(
        host: String,
        path: String,
        z: Int,
        baseTileX: Int,
        baseTileY: Int,
        centerPxX: Int,
        centerPxY: Int
    ): Bitmap? = coroutineScope {
        val tiles = listOf(
            0 to 0,
            1 to 0,
            0 to 1,
            1 to 1
        ).map { (dx, dy) ->
            async {
                val tx = baseTileX + dx
                val ty = baseTileY + dy
                // Color scheme 2 (Universal Blue / vivid precipitation), smooth=1, snow=1
                val url = "$host$path/$TILE_SIZE/$z/$tx/$ty/2/1_1.png"
                Triple(dx, dy, downloadBitmap(url))
            }
        }.awaitAll()

        // Create 512x512 stitched canvas
        val stitched = Bitmap.createBitmap(TILE_SIZE * 2, TILE_SIZE * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stitched)
        var anyDownloaded = false
        for ((dx, dy, tileBmp) in tiles) {
            if (tileBmp != null) {
                anyDownloaded = true
                canvas.drawBitmap(tileBmp, (dx * TILE_SIZE).toFloat(), (dy * TILE_SIZE).toFloat(), null)
                tileBmp.recycle()
            }
        }
        if (!anyDownloaded) {
            stitched.recycle()
            return@coroutineScope null
        }

        // Crop a 2:1 aspect window (256x128) centered precisely on Karvina for 128x64 OLED mapping
        val cropWidth = 256
        val cropHeight = 128
        val left = (centerPxX - cropWidth / 2).coerceIn(0, stitched.width - cropWidth)
        val top = (centerPxY - cropHeight / 2).coerceIn(0, stitched.height - cropHeight)

        val cropped = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val cropCanvas = Canvas(cropped)
        cropCanvas.drawBitmap(
            stitched,
            Rect(left, top, left + cropWidth, top + cropHeight),
            Rect(0, 0, cropWidth, cropHeight),
            null
        )
        stitched.recycle()
        cropped
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "KarvinaRadarOLED-Android/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun latLonToExactTile(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom.toDouble())
        val x = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return x to y
    }

    companion object {
        private const val TILE_SIZE = 256
    }
}
