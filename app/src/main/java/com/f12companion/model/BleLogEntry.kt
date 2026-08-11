package com.f12companion.model

enum class Direction {
    TX,
    RX
}

data class BleLogEntry(
    val direction: Direction,
    val hex: String,
    val timestamp: Long = System.currentTimeMillis()
)
