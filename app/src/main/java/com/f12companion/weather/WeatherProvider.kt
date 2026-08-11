package com.f12companion.weather

interface WeatherProvider {
    suspend fun forecast(latitude: Double, longitude: Double): WeatherForecast
}
