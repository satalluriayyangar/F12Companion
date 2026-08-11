package com.f12companion

import com.f12companion.model.Direction
import org.junit.Assert.*
import org.junit.Test

class GoldenExchangeUnitTest {
    @Test
    fun goldenTxIs20BytesAndMatchesCapture() {
        val tx = F12BleManager.GOLDEN_TX
        assertEquals(20, tx.size)
        val expected = "00 01 00 00 03 84 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
        assertEquals(expected, tx.joinToString(" ") { "%02X".format(it) })
    }

    @Test
    fun goldenRxIs19BytesAndMatchesCapture() {
        val rx = F12BleManager.GOLDEN_RX
        assertEquals(19, rx.size)
        val expected = "00 FF 00 19 04 00 00 00 01 00 02 00 00 00 00 00 00 00 00"
        assertEquals(expected, rx.joinToString(" ") { "%02X".format(it) })
    }

    @Test
    fun logEntryFormatsCorrectly() {
        val entry = com.f12companion.model.BleLogEntry(
            direction = Direction.TX,
            hex = F12BleManager.GOLDEN_TX.joinToString(" ") { "%02X".format(it) }
        )
        assertEquals(Direction.TX, entry.direction)
        assertTrue(entry.hex.startsWith("00 01 00 00"))
        assertFalse(entry.hex.contains("FF"))
    }
}
