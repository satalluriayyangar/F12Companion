package com.f12companion.weather

import com.f12companion.uitl.ByteUtil

object VFitWeatherEncoder {
    private const val ITEM_SIZE = 5
    private const val DAY_COUNT = 3
    private const val PAYLOAD_SIZE = 19

    fun encode(forecast: WeatherForecast): ByteArray {
        val payload = ByteArray(PAYLOAD_SIZE)
        val timeBytes = ByteUtil.intToByte4(forecast.timestampSeconds)
        System.arraycopy(timeBytes, 0, payload, 0, 4)

        for (i in 0 until DAY_COUNT) {
            val day = forecast.days.getOrNull(i) ?: WeatherDay(0, 0, 0)
            val offset = 4 + i * ITEM_SIZE
            payload[offset] = (day.vfitConditionCode and 0xFF).toByte()
            payload[offset + 1] = (day.lowTempC and 0xFF).toByte()
            payload[offset + 2] = (day.highTempC and 0xFF).toByte()
            payload[offset + 3] = 0
            payload[offset + 4] = 0
        }
        return payload
    }
}
