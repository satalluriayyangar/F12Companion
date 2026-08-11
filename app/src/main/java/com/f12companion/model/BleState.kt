package com.f12companion.model

sealed interface BleState {
    data object Idle : BleState
    data object Scanning : BleState
    data class Connecting(val address: String) : BleState
    data class Connected(val address: String) : BleState
    data class Disconnected(val reason: String? = null) : BleState
    data class Error(val message: String) : BleState
}
