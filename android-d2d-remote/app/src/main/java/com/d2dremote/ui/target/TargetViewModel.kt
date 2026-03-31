package com.d2dremote.ui.target

import androidx.lifecycle.ViewModel
import com.d2dremote.network.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class TargetViewModel : ViewModel() {
    private val _ipAddress = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _pairingCode = MutableStateFlow(generatePairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private fun generatePairingCode(): String {
        return (100000..999999).random().toString()
    }

    fun refreshPairingCode() {
        _pairingCode.value = generatePairingCode()
    }
    
    fun refreshIpAddress() {
        _ipAddress.value = NetworkUtils.getLocalIpAddress()
    }
}
