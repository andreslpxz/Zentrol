package com.d2dremote.network

import android.util.Log
import com.d2dremote.model.ScreenInfo
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.math.min

class VideoStreamClient {

    companion object {
        private const val TAG = "VideoStreamClient"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val AUTH_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 10000
        private const val MAX_FRAME_SIZE = 2 * 1024 * 1024
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 15000L
    }

    private var socket: Socket? = null
    private var clientJob: Job? = null
    private var targetHost: String? = null

    @Volatile
    var isConnected = false
        private set

    @Volatile
    private var shouldReconnect = true

    var onFrameReceived: ((ByteArray, Int) -> Unit)? = null
    var onScreenInfoReceived: ((ScreenInfo) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onReconnecting: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(host: String, pairingCode: String, scope: CoroutineScope) {
        if (isConnected) return
        targetHost = host
        shouldReconnect = true

        clientJob = scope.launch(Dispatchers.IO) {
            var attempt = 0

            while (isActive && shouldReconnect) {
                try {
                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.receiveBufferSize = 1024 * 1024
                    sock.connect(InetSocketAddress(host, NetworkUtils.VIDEO_PORT), CONNECT_TIMEOUT_MS)

                    if (!performPairingHandshake(sock, pairingCode)) {
                        sock.close()
                        withContext(Dispatchers.Main) {
                            onError?.invoke("Pairing code rejected by target device")
                        }
                        break
                    }

                    sock.soTimeout = READ_TIMEOUT_MS
                    socket = sock
                    isConnected = true
                    attempt = 0

                    Log.i(TAG, "Connected to $host:${NetworkUtils.VIDEO_PORT}")
                    withContext(Dispatchers.Main) { onConnected?.invoke() }

                    val input = DataInputStream(sock.getInputStream())
                    val frameBuffer = ByteArray(MAX_FRAME_SIZE)

                    while (isActive && isConnected && !sock.isClosed && shouldReconnect) {
                        try {
                            val sizeHeader = input.readInt()

                            if (sizeHeader < 0) {
                                val infoSize = -sizeHeader
                                if (infoSize > 1024) continue
                                val infoBytes = ByteArray(infoSize)
                                input.readFully(infoBytes)
                                val infoStr = String(infoBytes, Charsets.UTF_8)
                                val screenInfo = ScreenInfo.fromHeader(infoStr)
                                if (screenInfo != null) {
                                    withContext(Dispatchers.Main) {
                                        onScreenInfoReceived?.invoke(screenInfo)
                                    }
                                }
                                continue
                            }

                            if (sizeHeader <= 0 || sizeHeader > MAX_FRAME_SIZE) {
                                Log.w(TAG, "Invalid frame size: $sizeHeader")
                                continue
                            }

                            input.readFully(frameBuffer, 0, sizeHeader)
                            onFrameReceived?.invoke(frameBuffer, sizeHeader)
                        } catch (e: SocketTimeoutException) {
                            continue
                        }
                    }
                } catch (e: Exception) {
                    isConnected = false
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null

                    if (!shouldReconnect || !isActive) break

                    attempt++
                    if (attempt > MAX_RECONNECT_ATTEMPTS) {
                        Log.e(TAG, "Max reconnect attempts reached")
                        withContext(Dispatchers.Main) {
                            onError?.invoke("Connection failed after $MAX_RECONNECT_ATTEMPTS attempts")
                        }
                        break
                    }

                    val backoff = min(INITIAL_BACKOFF_MS * (1L shl (attempt - 1)), MAX_BACKOFF_MS)
                    Log.w(TAG, "Connection lost, reconnecting in ${backoff}ms (attempt $attempt)")
                    withContext(Dispatchers.Main) { onReconnecting?.invoke(attempt) }
                    delay(backoff)
                }
            }

            isConnected = false
            withContext(Dispatchers.Main) { onDisconnected?.invoke() }
        }
    }

    private fun performPairingHandshake(socket: Socket, pairingCode: String): Boolean {
        return try {
            socket.getOutputStream().write("PAIR:$pairingCode\n".toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            socket.soTimeout = AUTH_TIMEOUT_MS
            val response = readLineFromRawStream(socket.getInputStream())
            socket.soTimeout = 0
            response?.trim() == "AUTH:OK"
        } catch (e: Exception) {
            Log.w(TAG, "Pairing handshake failed: ${e.message}")
            false
        }
    }

    private fun readLineFromRawStream(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            if (b != '\r'.code) sb.append(b.toChar())
        }
    }

    fun disconnect() {
        shouldReconnect = false
        isConnected = false
        clientJob?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        targetHost = null
        Log.i(TAG, "Disconnected")
    }
}
