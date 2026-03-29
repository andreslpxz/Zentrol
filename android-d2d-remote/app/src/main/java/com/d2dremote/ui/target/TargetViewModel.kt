package com.d2dremote.ui.target

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.d2dremote.model.ServerState
import com.d2dremote.network.NetworkUtils
import com.d2dremote.service.ScreenCaptureService
import com.d2dremote.service.TouchAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TargetViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TargetViewModel"
    }

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _hasClient = MutableStateFlow(false)
    val hasClient: StateFlow<Boolean> = _hasClient.asStateFlow()

    private val _pairingCode = MutableStateFlow(generatePairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    init {
        checkAccessibilityStatus()

        ScreenCaptureService.onServiceStateChanged = { running ->
            viewModelScope.launch {
                _serverState.value = if (running) ServerState.Running else ServerState.Stopped
            }
        }

        TouchAccessibilityService.onServiceStateChanged = { enabled ->
            viewModelScope.launch {
                _isAccessibilityEnabled.value = enabled
            }
        }
    }

    fun refreshIp() {
        _localIp.value = NetworkUtils.getLocalIpAddress()
    }

    fun regeneratePairingCode() {
        _pairingCode.value = generatePairingCode()
    }

    fun checkAccessibilityStatus() {
        _isAccessibilityEnabled.value = TouchAccessibilityService.isServiceEnabled
    }

    fun startServer(resultCode: Int, resultData: Intent) {
        val context = getApplication<Application>()
        _serverState.value = ServerState.Starting

        val code = _pairingCode.value
        ScreenCaptureService.startCapture(context, resultCode, resultData, code)

        val serviceInstance = TouchAccessibilityService.instance
        if (serviceInstance != null) {
            serviceInstance.startControlServer(code)
        } else {
            Log.w(TAG, "AccessibilityService not bound yet, queuing control server start")
            TouchAccessibilityService.pendingControlServerStart = true
            TouchAccessibilityService.pendingPairingCode = code
        }
    }

    fun stopServer() {
        val context = getApplication<Application>()
        ScreenCaptureService.stopCapture(context)
        TouchAccessibilityService.instance?.stopControlServer()
        TouchAccessibilityService.pendingControlServerStart = false
        _serverState.value = ServerState.Stopped
        _hasClient.value = false
    }

    override fun onCleared() {
        super.onCleared()
        ScreenCaptureService.onServiceStateChanged = null
        TouchAccessibilityService.onServiceStateChanged = null
    }

    private fun generatePairingCode(): String {
        val chars = "0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
