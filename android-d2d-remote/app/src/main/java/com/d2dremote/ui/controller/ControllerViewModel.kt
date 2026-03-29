package com.d2dremote.ui.controller

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.d2dremote.model.ConnectionState
import com.d2dremote.model.ScreenInfo
import com.d2dremote.model.TouchEvent
import com.d2dremote.network.ControlClient
import com.d2dremote.network.VideoStreamClient
import com.d2dremote.service.VideoDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _targetIp = MutableStateFlow("")
    val targetIp: StateFlow<String> = _targetIp.asStateFlow()

    private val _screenInfo = MutableStateFlow<ScreenInfo?>(null)
    val screenInfo: StateFlow<ScreenInfo?> = _screenInfo.asStateFlow()

    private val _isInRemoteView = MutableStateFlow(false)
    val isInRemoteView: StateFlow<Boolean> = _isInRemoteView.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private var videoClient: VideoStreamClient? = null
    private var controlClient: ControlClient? = null
    private var videoDecoder: VideoDecoder? = null

    private var pendingSurface: Surface? = null
    private var decoderInitialized = false

    fun updateTargetIp(ip: String) {
        _targetIp.value = ip
    }

    fun updatePairingCode(code: String) {
        _pairingCode.value = code
    }

    fun connect() {
        val ip = _targetIp.value.trim()
        if (ip.isEmpty()) return

        val code = _pairingCode.value.trim()
        if (code.isEmpty()) return

        _connectionState.value = ConnectionState.Connecting
        decoderInitialized = false

        videoClient = VideoStreamClient().apply {
            onConnected = {
                viewModelScope.launch {
                    _connectionState.value = ConnectionState.Connected
                    _isInRemoteView.value = true
                }
            }
            onDisconnected = {
                viewModelScope.launch {
                    _connectionState.value = ConnectionState.Disconnected
                    _isInRemoteView.value = false
                }
            }
            onScreenInfoReceived = { info ->
                viewModelScope.launch {
                    _screenInfo.value = info
                    tryInitDecoder()
                }
            }
            onReconnecting = { attempt ->
                viewModelScope.launch {
                    _connectionState.value = ConnectionState.Connecting
                }
            }
            onError = { msg ->
                viewModelScope.launch {
                    _connectionState.value = ConnectionState.Error(msg)
                    _isInRemoteView.value = false
                }
            }
        }

        controlClient = ControlClient().apply {
            onReconnecting = { attempt ->
                viewModelScope.launch {
                    if (_connectionState.value is ConnectionState.Connected) {
                        _connectionState.value = ConnectionState.Connecting
                    }
                }
            }
            onError = { msg ->
                viewModelScope.launch {
                    if (_connectionState.value !is ConnectionState.Error) {
                        _connectionState.value = ConnectionState.Error(msg)
                    }
                }
            }
        }

        videoClient?.connect(ip, code, viewModelScope)
        controlClient?.connect(ip, code, viewModelScope)
    }

    fun onSurfaceAvailable(surface: Surface) {
        pendingSurface = surface
        tryInitDecoder()
    }

    fun onSurfaceDestroyed() {
        pendingSurface = null
        decoderInitialized = false
        videoDecoder?.stop()
        videoDecoder = null
    }

    private fun tryInitDecoder() {
        if (decoderInitialized) return
        val surface = pendingSurface ?: return
        val info = _screenInfo.value ?: return

        decoderInitialized = true
        videoDecoder?.stop()
        videoDecoder = VideoDecoder(surface).apply {
            start(info.width / 2, info.height / 2)
        }

        videoClient?.onFrameReceived = { data, size ->
            videoDecoder?.decodeFrame(data, size)
        }
    }

    fun sendTouchEvent(type: String, x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        val info = _screenInfo.value ?: return
        val scaledX = (x / viewWidth) * info.width
        val scaledY = (y / viewHeight) * info.height

        val event = TouchEvent(
            type = type,
            x = scaledX,
            y = scaledY,
            targetWidth = info.width,
            targetHeight = info.height
        )
        controlClient?.sendTouchEvent(event)
    }

    fun disconnect() {
        videoDecoder?.stop()
        videoDecoder = null
        videoClient?.disconnect()
        videoClient = null
        controlClient?.disconnect()
        controlClient = null
        _connectionState.value = ConnectionState.Idle
        _isInRemoteView.value = false
        _screenInfo.value = null
        pendingSurface = null
        decoderInitialized = false
    }

    fun goBackFromRemoteView() {
        disconnect()
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
