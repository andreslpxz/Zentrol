package com.d2dremote.network

import android.util.Log
import com.d2dremote.model.ScreenInfo
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

class VideoStreamClient {

    companion object {
        private const val TAG = "VideoStreamClient"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 10000
        private const val MAX_FRAME_SIZE = 2 * 1024 * 1024
    }

    private var socket: Socket? = null
    private var clientJob: Job? = null

    @Volatile
    var isConnected = false
        private set

    var onFrameReceived: ((ByteArray, Int) -> Unit)? = null
    var onScreenInfoReceived: ((ScreenInfo) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(host: String, scope: CoroutineScope) {
        if (isConnected) return

        clientJob = scope.launch(Dispatchers.IO) {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.receiveBufferSize = 1024 * 1024
                sock.soTimeout = READ_TIMEOUT_MS
                sock.connect(InetSocketAddress(host, NetworkUtils.VIDEO_PORT), CONNECT_TIMEOUT_MS)

                socket = sock
                isConnected = true

                Log.i(TAG, "Connected to $host:${NetworkUtils.VIDEO_PORT}")
                withContext(Dispatchers.Main) { onConnected?.invoke() }

                val input = DataInputStream(sock.getInputStream())
                val frameBuffer = ByteArray(MAX_FRAME_SIZE)

                while (isActive && isConnected && !sock.isClosed) {
                    try {
                        val sizeHeader = input.readInt()

                        if (sizeHeader < 0) {
                            val infoSize = -sizeHeader
                            if (infoSize > 1024) continue
                            val infoBytes = ByteArray(infoSize)
                            input.readFully(infoBytes)
                            val infoStr = String(infoBytes, Charsets.UTF_8)
                            val screenInfo = ScreenInfo.fromHeader("$infoStr")
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
            } catch (e: SocketException) {
                if (isConnected) {
                    Log.w(TAG, "Connection lost: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Connection lost: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client error", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Connection error: ${e.message}")
                }
            } finally {
                isConnected = false
                withContext(Dispatchers.Main) { onDisconnected?.invoke() }
            }
        }
    }

    fun disconnect() {
        isConnected = false
        clientJob?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        Log.i(TAG, "Disconnected")
    }
}
