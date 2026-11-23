package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.KarvinaGeo
import com.example.ui.theme.LocalHardwareColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Interactive WebView pointing directly to https://www.meteopress.cz/radar/ with an overlaid 2:1
 * Karvina OLED crop reticle. Uses GPU-safe PixelCopy (API 26+) so WebGL/Canvas radar maps never
 * crash Chromium's renderer process, and gracefully recovers via onRenderProcessGone.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MeteopressWebCaptureCard(
    url: String,
    autoCaptureEnabled: Boolean,
    captureFps: Int,
    captureCount: Int,
    onUrlChange: (String) -> Unit,
    onAutoCaptureToggle: (Boolean) -> Unit,
    onFrameCaptured: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val hw = LocalHardwareColors.current
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastRequestedUrl by remember { mutableStateOf(url) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var rendererCrashed by remember { mutableStateOf(false) }
    var webViewInstanceKey by remember { mutableIntStateOf(0) }
    var isCaptureInFlight by remember { mutableStateOf(false) }

    fun captureCropFromWebView() {
        if (rendererCrashed || isCaptureInFlight) return
        val wv = webViewRef ?: return
        if (!wv.isAttachedToWindow || !wv.isShown) return
        val w = wv.width
        val h = wv.height
        if (w < 64 || h < 64) return

        val cropW = (w * 0.74f).toInt().coerceIn(64, w)
        val cropH = (cropW / 2).coerceIn(32, h)
        val localLeft = ((w - cropW) / 2).coerceAtLeast(0)
        val localTop = ((h - cropH) / 2).coerceAtLeast(0)

        // Prefer GPU-compositor PixelCopy on API 26+ to avoid forcing Chromium software rasterization
        val window = context.findActivity()?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null) {
            val loc = IntArray(2)
            wv.getLocationInWindow(loc)
            val decor = window.decorView
            val winW = decor.width
            val winH = decor.height
            if (winW <= 0 || winH <= 0) return

            // Inset by 4px inside the reticle border so PixelCopy captures pure radar pixels only
            val inset = 4
            val srcLeft = (loc[0] + localLeft + inset).coerceIn(0, winW - 2)
            val srcTop = (loc[1] + localTop + inset).coerceIn(0, winH - 2)
            val srcRight = (loc[0] + localLeft + cropW - inset).coerceIn(srcLeft + 1, winW)
            val srcBottom = (loc[1] + localTop + cropH - inset).coerceIn(srcTop + 1, winH)
            val rectW = srcRight - srcLeft
            val rectH = srcBottom - srcTop
            if (rectW < 32 || rectH < 16) return

            val destBmp = Bitmap.createBitmap(rectW, rectH, Bitmap.Config.ARGB_8888)
            val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
            isCaptureInFlight = true
            try {
                PixelCopy.request(
                    window,
                    srcRect,
                    destBmp,
                    { copyResult ->
                        isCaptureInFlight = false
                        if (copyResult == PixelCopy.SUCCESS) {
                            onFrameCaptured(destBmp)
                        } else {
                            destBmp.recycle()
                        }
                    },
                    mainHandler
                )
            } catch (_: Exception) {
                isCaptureInFlight = false
                destBmp.recycle()
            }
            return
        }

        // Legacy fallback for API 24-25 only
        isCaptureInFlight = true
        try {
            val fullBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(fullBmp)
            wv.draw(canvas)
            val cropped = Bitmap.createBitmap(fullBmp, localLeft, localTop, cropW, cropH)
            fullBmp.recycle()
            onFrameCaptured(cropped)
        } catch (_: Exception) {
            // Skip frame if WebView is mid-layout
        } finally {
            isCaptureInFlight = false
        }
    }

    // Periodic live animation capture at a renderer-safe cadence
    LaunchedEffect(autoCaptureEnabled, captureFps, webViewRef, rendererCrashed) {
        if (!autoCaptureEnabled || rendererCrashed) return@LaunchedEffect
        // Cap WebView snapshot rate to max ~1.25 Hz (800ms) to keep GPU/WebView smooth
        val periodMs = (1000L / captureFps.coerceIn(1, 3)).coerceAtLeast(800L)
        while (isActive) {
            delay(periodMs)
            if (!rendererCrashed && loadProgress >= 95) {
                captureCropFromWebView()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, hw.hairline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Quick view switchers: Karvina zoomed vs exact prompt URL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = url == KarvinaGeo.METEOPRESS_KARVINA_URL,
                    onClick = { onUrlChange(KarvinaGeo.METEOPRESS_KARVINA_URL) },
                    label = { Text(stringResource(R.string.meteopress_open_karvina)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("btn_meteopress_karvina")
                )
                FilterChip(
                    selected = url == KarvinaGeo.METEOPRESS_CR_URL,
                    onClick = { onUrlChange(KarvinaGeo.METEOPRESS_CR_URL) },
                    label = { Text(stringResource(R.string.meteopress_open_cr)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("btn_meteopress_cr")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!rendererCrashed && loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Live Meteopress WebView + OLED 2:1 Crop Reticle Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(hw.oledGlassBlack)
            ) {
                if (rendererCrashed) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = hw.radarAmber,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Webový náhled Meteopress byl ukončen systémem (nedostatek paměti WebGL).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                rendererCrashed = false
                                loadProgress = 0
                                webViewInstanceKey++
                            },
                            modifier = Modifier.testTag("btn_reload_meteopress_webview")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Obnovit Meteopress")
                        }
                    }
                } else {
                    key(webViewInstanceKey) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.mediaPlaybackRequiresUserGesture = true
                                    settings.setSupportMultipleWindows(false)
                                    settings.builtInZoomControls = false
                                    settings.displayZoomControls = false

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        setRendererPriorityPolicy(
                                            WebView.RENDERER_PRIORITY_IMPORTANT,
                                            true
                                        )
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?
                                        ): Boolean {
                                            // Gracefully handle renderer crash without killing the app process
                                            webViewRef = null
                                            rendererCrashed = true
                                            view?.let { deadView ->
                                                (deadView.parent as? ViewGroup)?.removeView(deadView)
                                                deadView.destroy()
                                            }
                                            return true
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadProgress = newProgress
                                        }
                                    }
                                    lastRequestedUrl = url
                                    loadUrl(url)
                                    webViewRef = this
                                }
                            },
                            update = { wv ->
                                if (lastRequestedUrl != url) {
                                    lastRequestedUrl = url
                                    wv.loadUrl(url)
                                }
                            },
                            onRelease = { wv ->
                                if (webViewRef === wv) {
                                    webViewRef = null
                                }
                                try {
                                    wv.stopLoading()
                                    wv.onPause()
                                    wv.webViewClient = WebViewClient()
                                    wv.webChromeClient = null
                                    wv.destroy()
                                } catch (_: Exception) {
                                    // Already destroyed in onRenderProcessGone
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Non-interactive visual crop reticle matching the 128x64 OLED 2:1 viewport
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val amberColor = hw.radarAmber
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rectW = size.width * 0.74f
                        val rectH = (rectW / 2f).coerceAtMost(size.height * 0.9f)
                        val left = (size.width - rectW) / 2f
                        val top = (size.height - rectH) / 2f

                        // Subtle darkened mask outside crop region
                        val mask = Color.Black.copy(alpha = 0.28f)
                        drawRect(mask, Offset.Zero, Size(size.width, top))
                        drawRect(mask, Offset(0f, top + rectH), Size(size.width, size.height - top - rectH))
                        drawRect(mask, Offset(0f, top), Size(left, rectH))
                        drawRect(mask, Offset(left + rectW, top), Size(size.width - left - rectW, rectH))

                        // Dashed OLED viewport border
                        drawRect(
                            color = primaryColor,
                            topLeft = Offset(left, top),
                            size = Size(rectW, rectH),
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        )

                        // Outer edge alignment ticks pointing to center (keeps interior uncontaminated for PixelCopy)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val tick = 10f
                        drawLine(amberColor, Offset(cx, top - tick), Offset(cx, top), strokeWidth = 2.5f)
                        drawLine(amberColor, Offset(cx, top + rectH), Offset(cx, top + rectH + tick), strokeWidth = 2.5f)
                        drawLine(amberColor, Offset(left - tick, cy), Offset(left, cy), strokeWidth = 2.5f)
                        drawLine(amberColor, Offset(left + rectW, cy), Offset(left + rectW + tick, cy), strokeWidth = 2.5f)
                    }

                    // Reticle badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "VÝŘEZ OLED 128×64 · #$captureCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capture controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Switch(
                        checked = autoCaptureEnabled,
                        onCheckedChange = onAutoCaptureToggle,
                        modifier = Modifier.testTag("switch_web_auto_capture")
                    )
                    Text(
                        text = stringResource(R.string.auto_capture_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledTonalButton(
                    onClick = { captureCropFromWebView() },
                    enabled = !rendererCrashed,
                    modifier = Modifier.testTag("btn_capture_web_now")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.capture_now))
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
