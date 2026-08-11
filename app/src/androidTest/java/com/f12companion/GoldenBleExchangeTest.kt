package com.f12companion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.f12companion.model.Direction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldenBleExchangeTest {
    private lateinit var manager: F12BleManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = F12BleManager(context)
    }

    @Test
    fun goldenTxIsExactly20Bytes() {
        val tx = F12BleManager.GOLDEN_TX
        assertEquals(20, tx.size)
        val expected = "00 01 00 00 03 84 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
        assertEquals(expected, tx.joinToString(" ") { "%02X".format(it) })
    }

    @Test
    fun goldenRxIsExactly20Bytes() {
        val rx = F12BleManager.GOLDEN_RX
        assertEquals(20, rx.size)
        val expected = "00 FF 00 19 04 00 00 00 01 00 02 00 00 00 00 00 00 00 00"
        assertEquals(expected, rx.joinToString(" ") { "%02X".format(it) })
    }

    @Test
    fun logEntryTracksDirectionAndHex() {
        val entry = com.f12companion.model.BleLogEntry(
            direction = Direction.TX,
            hex = F12BleManager.GOLDEN_TX.joinToString(" ") { "%02X".format(it) }
        )
        assertEquals(Direction.TX, entry.direction)
        assertTrue(entry.hex.startsWith("00 01 00 00"))
    }
}
