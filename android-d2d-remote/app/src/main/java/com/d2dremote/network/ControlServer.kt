package com.d2dremote.network

import android.util.Log
import com.d2dremote.model.TouchEvent
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class ControlServer {

    companion object {
        private const val TAG = "ControlServer"
        private const val AUTH_TIMEOUT_MS = 5000
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var serverJob: Job? = null

    @Volatile
    var isRunning = false
        private set

    var onTouchEvent: ((TouchEvent) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start(scope: CoroutineScope, pairingCode: String? = null) {
        if (isRunning) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(NetworkUtils.CONTROL_PORT).apply {
                    reuseAddress = true
                }
                isRunning = true
                Log.i(TAG, "Control server started on port ${NetworkUtils.CONTROL_PORT}")

                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        socket.tcpNoDelay = true

                        if (pairingCode != null) {
                            if (!authenticateClient(socket, pairingCode)) {
                                Log.w(TAG, "Client failed pairing authentication, rejecting")
                                socket.close()
                                continue
                            }
                        }

                        clientSocket?.close()
                        clientSocket = socket

                        Log.i(TAG, "Control client connected: ${socket.inetAddress.hostAddress}")

                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        var line: String?

                        while (isActive && isRunning) {
                            line = reader.readLine() ?: break
                            val event = TouchEvent.fromJson(line)
                            if (event != null) {
                                onTouchEvent?.invoke(event)
                            }
                        }
                    } catch (e: SocketException) {
                        if (isRunning) {
                            Log.w(TAG, "Control client disconnected: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error reading control data: ${e.message}")
                    }
                }
            } catch (e: SocketException) {
                if (isRunning) {
                    Log.e(TAG, "Control server error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Control server error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Control server error", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Control error: ${e.message}")
                }
            } finally {
                isRunning = false
            }
        }
    }

    private fun authenticateClient(socket: Socket, expectedCode: String): Boolean {
        return try {
            socket.soTimeout = AUTH_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val authLine = reader.readLine() ?: return false
            socket.soTimeout = 0
            val result = authLine.startsWith("PAIR:") && authLine.substringAfter("PAIR:").trim() == expectedCode
            if (result) {
                socket.getOutputStream().write("AUTH:OK\n".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            } else {
                socket.getOutputStream().write("AUTH:FAIL\n".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Auth handshake failed: ${e.message}")
            false
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
        Log.i(TAG, "Control server stopped")
    }
}
