package com.f12companion.weather

import com.f12companion.model.Direction
import com.f12companion.F12BleManager

data class WeatherDay(
    val vfitConditionCode: Int,
    val lowTempC: Int,
    val highTempC: Int
)

data class WeatherForecast(
    val timestampSeconds: Int,
    val days: List<WeatherDay>
)
