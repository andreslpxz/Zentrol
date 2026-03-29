package com.d2dremote.model

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    data object Disconnected : ConnectionState()
}

sealed class ServerState {
    data object Stopped : ServerState()
    data object Starting : ServerState()
    data object Running : ServerState()
    data class Error(val message: String) : ServerState()
}
