package com.d2dremote.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer

class VideoStreamServer {

    companion object {
        private const val TAG = "VideoStreamServer"
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private var serverJob: Job? = null

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var hasClient = false
        private set

    var onClientConnected: (() -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start(scope: CoroutineScope) {
        if (isRunning) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(NetworkUtils.VIDEO_PORT).apply {
                    reuseAddress = true
                    soTimeout = 0
                }
                isRunning = true
                Log.i(TAG, "Video server started on port ${NetworkUtils.VIDEO_PORT}")

                while (isActive && isRunning) {
                    try {
                        Log.i(TAG, "Waiting for client connection...")
                        val socket = serverSocket?.accept() ?: break
                        socket.tcpNoDelay = true
                        socket.setSendBufferSize(1024 * 1024)

                        clientSocket?.close()
                        clientSocket = socket
                        outputStream = socket.getOutputStream()
                        hasClient = true

                        Log.i(TAG, "Client connected: ${socket.inetAddress.hostAddress}")
                        withContext(Dispatchers.Main) {
                            onClientConnected?.invoke()
                        }

                        while (isActive && socket.isConnected && !socket.isClosed) {
                            delay(500)
                        }
                    } catch (e: SocketException) {
                        if (isRunning) {
                            Log.w(TAG, "Client disconnected: ${e.message}")
                        }
                    } finally {
                        hasClient = false
                        withContext(Dispatchers.Main) {
                            onClientDisconnected?.invoke()
                        }
                    }
                }
            } catch (e: SocketException) {
                if (isRunning) {
                    Log.e(TAG, "Server socket error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Server error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video server error", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Server error: ${e.message}")
                }
            } finally {
                isRunning = false
            }
        }
    }

    fun sendFrame(data: ByteArray, offset: Int, size: Int) {
        if (!hasClient) return
        try {
            val stream = outputStream ?: return
            val header = ByteBuffer.allocate(4).putInt(size).array()
            stream.write(header)
            stream.write(data, offset, size)
            stream.flush()
        } catch (e: SocketException) {
            Log.w(TAG, "Failed to send frame: ${e.message}")
            hasClient = false
            clientSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send frame: ${e.message}")
        }
    }

    fun sendScreenInfo(width: Int, height: Int, density: Int) {
        if (!hasClient) return
        try {
            val stream = outputStream ?: return
            val info = "SCRN:${width}x${height}:${density}\n".toByteArray(Charsets.UTF_8)
            val header = ByteBuffer.allocate(4).putInt(-info.size).array()
            stream.write(header)
            stream.write(info)
            stream.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send screen info: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        hasClient = false
        serverJob?.cancel()
        try { outputStream?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        outputStream = null
        clientSocket = null
        serverSocket = null
        Log.i(TAG, "Video server stopped")
    }
}
