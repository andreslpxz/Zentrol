package com.d2dremote.ui.target

import android.app.Application
import android.content.Intent
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

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _hasClient = MutableStateFlow(false)
    val hasClient: StateFlow<Boolean> = _hasClient.asStateFlow()

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

    fun checkAccessibilityStatus() {
        _isAccessibilityEnabled.value = TouchAccessibilityService.isServiceEnabled
    }

    fun startServer(resultCode: Int, resultData: Intent) {
        val context = getApplication<Application>()
        _serverState.value = ServerState.Starting

        ScreenCaptureService.startCapture(context, resultCode, resultData)

        TouchAccessibilityService.instance?.startControlServer()
    }

    fun stopServer() {
        val context = getApplication<Application>()
        ScreenCaptureService.stopCapture(context)
        TouchAccessibilityService.instance?.stopControlServer()
        _serverState.value = ServerState.Stopped
        _hasClient.value = false
    }

    override fun onCleared() {
        super.onCleared()
        ScreenCaptureService.onServiceStateChanged = null
        TouchAccessibilityService.onServiceStateChanged = null
    }
}
