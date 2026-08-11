package com.f12companion.weather

import org.junit.Assert.*
import org.junit.Test

class CEProtocolBEncoderTest {
    @Test
    fun encodeWeatherProducesCorrectFrameStructure() {
        val payload = ByteArray(19) { it.toByte() }
        val frames = CEProtocolBEncoder.encodeWeather(payload)
        assertEquals(2, frames.size)
        assertEquals(20, frames[0].size)
        assertEquals(20, frames[1].size)
    }

    @Test
    fun firstFrameHasCorrectHeader() {
        val payload = ByteArray(19) { 0 }
        val frames = CEProtocolBEncoder.encodeWeather(payload)
        val header = frames[0]
        assertEquals(0, header[0].toInt() and 0xFF)
        assertEquals(1, header[1].toInt() and 0xFF)
        assertEquals(1, header[2].toInt() and 0xFF)
        assertEquals(0, header[3].toInt() and 0xFF)
        assertEquals(1, header[4].toInt() and 0xFF)
        assertEquals(105, header[5].toInt() and 0xFF)
        assertEquals(0, header[6].toInt() and 0xFF)
        assertEquals(0, header[7].toInt() and 0xFF)
        assertEquals(19, header[8].toInt() and 0xFF)
        assertEquals(0, header[9].toInt() and 0xFF)
    }

    @Test
    fun firstFrameContainsFirst10BytesOfPayload() {
        val payload = ByteArray(19) { it.toByte() }
        val frames = CEProtocolBEncoder.encodeWeather(payload)
        for (i in 0 until 10) {
            assertEquals(i, frames[0][10 + i].toInt() and 0xFF)
        }
    }

    @Test
    fun continuationFrameHasPageIndexAndRemainingPayload() {
        val payload = ByteArray(19) { it.toByte() }
        val frames = CEProtocolBEncoder.encodeWeather(payload)
        val cont = frames[1]
        assertEquals(1, cont[0].toInt() and 0xFF)
        for (i in 0 until 9) {
            assertEquals(10 + i, cont[1 + i].toInt() and 0xFF)
        }
    }

    @Test
    fun weatherPayloadBytesArePreservedAcrossFrames() {
        val payload = ByteArray(19) { (it * 2).toByte() }
        val frames = CEProtocolBEncoder.encodeWeather(payload)
        for (i in 0 until 10) {
            assertEquals((i * 2) and 0xFF, frames[0][10 + i].toInt() and 0xFF)
        }
        for (i in 0 until 9) {
            assertEquals(((10 + i) * 2) and 0xFF, frames[1][1 + i].toInt() and 0xFF)
        }
    }

    @Test
    fun shortPayloadFitsInSingleFrame() {
        val payload = ByteArray(5) { it.toByte() }
        val frames = CEProtocolBEncoder.encode(payload, cmd = 1, dataType = 105)
        assertEquals(1, frames.size)
        for (i in 0 until 5) {
            assertEquals(i, frames[0][10 + i].toInt() and 0xFF)
        }
    }
}
