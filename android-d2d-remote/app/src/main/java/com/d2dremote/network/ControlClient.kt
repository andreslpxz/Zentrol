package com.d2dremote.network

import android.util.Log
import com.d2dremote.model.TouchEvent
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue

class ControlClient {

    companion object {
        private const val TAG = "ControlClient"
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var clientJob: Job? = null
    private val eventQueue = ConcurrentLinkedQueue<TouchEvent>()

    @Volatile
    var isConnected = false
        private set

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(host: String, scope: CoroutineScope) {
        if (isConnected) return

        clientJob = scope.launch(Dispatchers.IO) {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(host, NetworkUtils.CONTROL_PORT), CONNECT_TIMEOUT_MS)

                socket = sock
                writer = PrintWriter(sock.getOutputStream(), true)
                isConnected = true

                Log.i(TAG, "Control connected to $host:${NetworkUtils.CONTROL_PORT}")
                withContext(Dispatchers.Main) { onConnected?.invoke() }

                while (isActive && isConnected && !sock.isClosed) {
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
            } catch (e: SocketException) {
                if (isConnected) {
                    Log.w(TAG, "Control connection lost: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Control lost: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Control client error", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Control error: ${e.message}")
                }
            } finally {
                isConnected = false
                withContext(Dispatchers.Main) { onDisconnected?.invoke() }
            }
        }
    }

    fun sendTouchEvent(event: TouchEvent) {
        if (!isConnected) return
        eventQueue.offer(event)
    }

    fun disconnect() {
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
