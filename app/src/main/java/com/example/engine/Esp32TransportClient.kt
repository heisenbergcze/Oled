package com.example.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import com.example.model.Esp32Config
import com.example.model.PairedBtDevice
import com.example.model.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

class Esp32TransportClient(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private val btMutex = Mutex()
    private var btSocket: BluetoothSocket? = null
    private var connectedBtAddress: String? = null
    private var udpSequence = 0

    data class TransportResult(
        val success: Boolean,
        val latencyMs: Long,
        val message: String
    )

    suspend fun testConnection(config: Esp32Config): TransportResult = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        when (config.transportMode) {
            TransportMode.WIFI_HTTP -> testHttpStatus(config, start)
            TransportMode.WIFI_UDP -> testUdpReachability(config, start)
            TransportMode.BLUETOOTH_SPP -> testBluetoothConnection(config, start)
        }
    }

    suspend fun sendFrame(config: Esp32Config, packedBytes: ByteArray): TransportResult =
        withContext(Dispatchers.IO) {
            require(packedBytes.size == OledFrameConverter.BUFFER_SIZE_BYTES) {
                "OLED buffer musí mít přesně 1024 bajtů"
            }
            val start = SystemClock.elapsedRealtime()
            when (config.transportMode) {
                TransportMode.WIFI_HTTP -> sendHttpFrame(config, packedBytes, start)
                TransportMode.WIFI_UDP -> sendUdpFrame(config, packedBytes, start)
                TransportMode.BLUETOOTH_SPP -> sendBluetoothFrame(config, packedBytes, start)
            }
        }

    private fun testHttpStatus(config: Esp32Config, startMs: Long): TransportResult {
        val host = config.host.trim()
        if (host.isEmpty()) {
            return TransportResult(false, 0, "Zadejte IP adresu ESP32")
        }
        return try {
            val url = "http://$host:${config.httpPort}/status"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                val elapsed = SystemClock.elapsedRealtime() - startMs
                if (resp.isSuccessful) {
                    val body = resp.body?.string()?.take(60)?.trim().orEmpty()
                    TransportResult(
                        true,
                        elapsed,
                        if (body.isNotEmpty()) "ESP32 online ($body) · ${elapsed} ms" else "ESP32 HTTP připraveno · ${elapsed} ms"
                    )
                } else {
                    TransportResult(false, elapsed, "ESP32 odpovědělo HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            TransportResult(false, elapsed, "Nedostupné (${host}:${config.httpPort}): ${e.localizedMessage ?: "chyba sítě"}")
        }
    }

    private fun sendHttpFrame(config: Esp32Config, payload: ByteArray, startMs: Long): TransportResult {
        val host = config.host.trim()
        if (host.isEmpty()) return TransportResult(false, 0, "Chybí IP adresa ESP32")
        return try {
            val url = "http://$host:${config.httpPort}/frame"
            val body = payload.toRequestBody(OCTET_STREAM)
            val req = Request.Builder()
                .url(url)
                .header("X-OLED-Format", config.bufferLayout.codeTag)
                .header("X-OLED-Driver", config.controller.name)
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val elapsed = SystemClock.elapsedRealtime() - startMs
                if (resp.isSuccessful) {
                    TransportResult(true, elapsed, "Odesláno 1024 B přes HTTP · ${elapsed} ms")
                } else {
                    TransportResult(false, elapsed, "Chyba přenosu HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            TransportResult(false, elapsed, "HTTP přenos selhal: ${e.localizedMessage ?: "timeout"}")
        }
    }

    private fun testUdpReachability(config: Esp32Config, startMs: Long): TransportResult {
        val host = config.host.trim()
        if (host.isEmpty()) return TransportResult(false, 0, "Zadejte IP adresu ESP32")
        return try {
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(1800)
            val elapsed = SystemClock.elapsedRealtime() - startMs
            if (reachable) {
                TransportResult(true, elapsed, "ESP32 dosažitelné pro UDP:${config.udpPort} · ${elapsed} ms")
            } else {
                // Local firewalls sometimes drop ICMP; UDP packets may still succeed
                TransportResult(true, elapsed, "Cíl $host:${config.udpPort} připraven (UDP stream)")
            }
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            TransportResult(false, elapsed, "Neplatná adresa $host: ${e.localizedMessage ?: "chyba"}")
        }
    }

    private fun sendUdpFrame(config: Esp32Config, payload: ByteArray, startMs: Long): TransportResult {
        val host = config.host.trim()
        if (host.isEmpty()) return TransportResult(false, 0, "Chybí IP adresa ESP32")
        return try {
            val packetBytes = ByteArray(4 + payload.size)
            packetBytes[0] = 0xAA.toByte()
            packetBytes[1] = 0x55.toByte()
            val seq = (udpSequence++) and 0xFFFF
            packetBytes[2] = ((seq shr 8) and 0xFF).toByte()
            packetBytes[3] = (seq and 0xFF).toByte()
            System.arraycopy(payload, 0, packetBytes, 4, payload.size)

            DatagramSocket().use { socket ->
                val addr = InetAddress.getByName(host)
                val packet = DatagramPacket(packetBytes, packetBytes.size, addr, config.udpPort)
                socket.send(packet)
            }
            val elapsed = SystemClock.elapsedRealtime() - startMs
            TransportResult(true, elapsed, "UDP paket #${seq} (1028 B) → $host:${config.udpPort}")
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            TransportResult(false, elapsed, "UDP chyba: ${e.localizedMessage ?: "síť nedostupná"}")
        }
    }

    @SuppressLint("MissingPermission")
    fun listPairedBluetoothDevices(): List<PairedBtDevice> {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter = manager?.adapter ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices.orEmpty().map { dev ->
                PairedBtDevice(
                    name = dev.name ?: "Neznámé BT zařízení",
                    address = dev.address
                )
            }.sortedByDescending { it.name.contains("ESP", ignoreCase = true) || it.name.contains("OLED", ignoreCase = true) }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureBluetoothSocket(address: String): BluetoothSocket {
        val existing = btSocket
        if (existing != null && existing.isConnected && connectedBtAddress == address) {
            return existing
        }
        closeBluetooth()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw IOException("Bluetooth není na zařízení podporován")
        val adapter = manager.adapter ?: throw IOException("Bluetooth adaptér není dostupný")
        if (!adapter.isEnabled) throw IOException("Zapněte Bluetooth v nastavení telefonu")
        val device = adapter.getRemoteDevice(address)
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket.connect()
        btSocket = socket
        connectedBtAddress = address
        return socket
    }

    private suspend fun testBluetoothConnection(config: Esp32Config, startMs: Long): TransportResult {
        if (config.btDeviceAddress.isBlank()) {
            return TransportResult(false, 0, "Vyberte spárované ESP32 Bluetooth zařízení")
        }
        return btMutex.withLock {
            try {
                ensureBluetoothSocket(config.btDeviceAddress)
                val elapsed = SystemClock.elapsedRealtime() - startMs
                TransportResult(true, elapsed, "BT SPP připojeno k ${config.btDeviceName.ifBlank { config.btDeviceAddress }} · ${elapsed} ms")
            } catch (e: SecurityException) {
                TransportResult(false, 0, "Chybí oprávnění Bluetooth")
            } catch (e: Exception) {
                closeBluetooth()
                val elapsed = SystemClock.elapsedRealtime() - startMs
                TransportResult(false, elapsed, "BT připojení selhalo: ${e.localizedMessage ?: "zařízení mimo dosah"}")
            }
        }
    }

    private suspend fun sendBluetoothFrame(
        config: Esp32Config,
        payload: ByteArray,
        startMs: Long
    ): TransportResult {
        if (config.btDeviceAddress.isBlank()) {
            return TransportResult(false, 0, "Není vybráno Bluetooth ESP32")
        }
        return btMutex.withLock {
            try {
                val socket = ensureBluetoothSocket(config.btDeviceAddress)
                val out = socket.outputStream
                // Framed binary protocol: [0xAA, 0x55, 1024 bytes, XOR checksum]
                var xorSum = 0
                for (b in payload) {
                    xorSum = xorSum xor (b.toInt() and 0xFF)
                }
                val frame = ByteArray(2 + payload.size + 1)
                frame[0] = 0xAA.toByte()
                frame[1] = 0x55.toByte()
                System.arraycopy(payload, 0, frame, 2, payload.size)
                frame[frame.lastIndex] = xorSum.toByte()
                out.write(frame)
                out.flush()
                val elapsed = SystemClock.elapsedRealtime() - startMs
                TransportResult(true, elapsed, "BT SPP 1027 B odesláno · ${elapsed} ms")
            } catch (e: SecurityException) {
                TransportResult(false, 0, "Chybí oprávnění Bluetooth")
            } catch (e: Exception) {
                closeBluetooth()
                val elapsed = SystemClock.elapsedRealtime() - startMs
                TransportResult(false, elapsed, "BT chyba přenosu: ${e.localizedMessage ?: "odpojeno"}")
            }
        }
    }

    fun closeBluetooth() {
        try {
            btSocket?.close()
        } catch (_: Exception) {
        } finally {
            btSocket = null
            connectedBtAddress = null
        }
    }

    companion object {
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
