package com.f12companion.weather

import org.junit.Assert.*
import org.junit.Test

class VFitWeatherEncoderTest {
    @Test
    fun encodeProducesExactly19Bytes() {
        val forecast = WeatherForecast(
            timestampSeconds = 1_700_000_000,
            days = listOf(
                WeatherDay(0, 20, 30),
                WeatherDay(1, 15, 25),
                WeatherDay(2, 10, 20)
            )
        )
        val payload = VFitWeatherEncoder.encode(forecast)
        assertEquals(19, payload.size)
    }

    @Test
    fun encodeTimestampIsLittleEndian() {
        val forecast = WeatherForecast(
            timestampSeconds = 0x01020304,
            days = listOf(WeatherDay(0, 0, 0), WeatherDay(0, 0, 0), WeatherDay(0, 0, 0))
        )
        val payload = VFitWeatherEncoder.encode(forecast)
        assertEquals(0x04, payload[0].toInt() and 0xFF)
        assertEquals(0x03, payload[1].toInt() and 0xFF)
        assertEquals(0x02, payload[2].toInt() and 0xFF)
        assertEquals(0x01, payload[3].toInt() and 0xFF)
    }

    @Test
    fun encodeThreeDailyRecords() {
        val forecast = WeatherForecast(
            timestampSeconds = 1_700_000_000,
            days = listOf(
                WeatherDay(5, 18, 28),
                WeatherDay(6, 16, 26),
                WeatherDay(7, 14, 24)
            )
        )
        val payload = VFitWeatherEncoder.encode(forecast)
        assertEquals(5, payload[4].toInt() and 0xFF)
        assertEquals(18, payload[5].toInt() and 0xFF)
        assertEquals(28, payload[6].toInt() and 0xFF)
        assertEquals(0, payload[7].toInt() and 0xFF)
        assertEquals(0, payload[8].toInt() and 0xFF)
        assertEquals(6, payload[9].toInt() and 0xFF)
        assertEquals(16, payload[10].toInt() and 0xFF)
        assertEquals(26, payload[11].toInt() and 0xFF)
        assertEquals(7, payload[14].toInt() and 0xFF)
        assertEquals(14, payload[15].toInt() and 0xFF)
        assertEquals(24, payload[16].toInt() and 0xFF)
    }

    @Test
    fun encodeNegativeTemperatureAsSignedByte() {
        val forecast = WeatherForecast(
            timestampSeconds = 1_700_000_000,
            days = listOf(
                WeatherDay(0, -5, 10),
                WeatherDay(0, 0, 0),
                WeatherDay(0, 0, 0)
            )
        )
        val payload = VFitWeatherEncoder.encode(forecast)
        assertEquals(0xFB, payload[5].toInt() and 0xFF) // -5 in two's complement
        assertEquals(10, payload[6].toInt() and 0xFF)
    }

    @Test
    fun encodePadsReservedBytesToZero() {
        val forecast = WeatherForecast(
            timestampSeconds = 1_700_000_000,
            days = listOf(
                WeatherDay(0, 20, 30),
                WeatherDay(0, 20, 30),
                WeatherDay(0, 20, 30)
            )
        )
        val payload = VFitWeatherEncoder.encode(forecast)
        for (i in listOf(7, 8, 12, 13, 17, 18)) {
            assertEquals(0, payload[i].toInt() and 0xFF)
        }
    }
}
