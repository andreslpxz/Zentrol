package com.d2dremote.network

import android.util.Log
import com.d2dremote.model.TouchEvent
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

class ControlClient {

    companion object {
        private const val TAG = "ControlClient"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val AUTH_TIMEOUT_MS = 5000
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 15000L
    }

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var clientJob: Job? = null
    private val eventQueue = ConcurrentLinkedQueue<TouchEvent>()

    @Volatile
    var isConnected = false
        private set

    @Volatile
    private var shouldReconnect = true

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onReconnecting: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(host: String, pairingCode: String, scope: CoroutineScope) {
        if (isConnected) return
        shouldReconnect = true

        clientJob = scope.launch(Dispatchers.IO) {
            var attempt = 0

            while (isActive && shouldReconnect) {
                try {
                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(host, NetworkUtils.CONTROL_PORT), CONNECT_TIMEOUT_MS)

                    if (!performPairingHandshake(sock, pairingCode)) {
                        sock.close()
                        withContext(Dispatchers.Main) {
                            onError?.invoke("Pairing code rejected by target device")
                        }
                        break
                    }

                    socket = sock
                    writer = PrintWriter(sock.getOutputStream(), true)
                    isConnected = true
                    attempt = 0

                    Log.i(TAG, "Control connected to $host:${NetworkUtils.CONTROL_PORT}")
                    withContext(Dispatchers.Main) { onConnected?.invoke() }

                    while (isActive && isConnected && !sock.isClosed && shouldReconnect) {
                        val event = eventQueue.poll()
                        if (event != null) {
                            try {
                                writer?.println(event.toJson())
                            } catch (e: SocketException) {
                                Log.w(TAG, "Send failed: ${e.message}")
                                break
                            }
                        } else {
                            delay(5)
                        }
                    }
                } catch (e: Exception) {
                    isConnected = false
                    try { writer?.close() } catch (_: Exception) {}
                    try { socket?.close() } catch (_: Exception) {}
                    writer = null
                    socket = null

                    if (!shouldReconnect || !isActive) break

                    attempt++
                    if (attempt > MAX_RECONNECT_ATTEMPTS) {
                        Log.e(TAG, "Max reconnect attempts reached for control")
                        withContext(Dispatchers.Main) {
                            onError?.invoke("Control connection failed after $MAX_RECONNECT_ATTEMPTS attempts")
                        }
                        break
                    }

                    val backoff = min(INITIAL_BACKOFF_MS * (1L shl (attempt - 1)), MAX_BACKOFF_MS)
                    Log.w(TAG, "Control reconnecting in ${backoff}ms (attempt $attempt)")
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

    fun sendTouchEvent(event: TouchEvent) {
        if (!isConnected) return
        eventQueue.offer(event)
    }

    fun disconnect() {
        shouldReconnect = false
        isConnected = false
        clientJob?.cancel()
        eventQueue.clear()
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
        Log.i(TAG, "Control disconnected")
    }
}
