package com.f12companion.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenMeteoWeatherProvider : WeatherProvider {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun forecast(latitude: Double, longitude: Double): WeatherForecast {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                "&timezone=auto&forecast_days=3"

        val response = client.get(url).body<OpenMeteoResponse>()
        val daily = response.daily

        val days = mutableListOf<WeatherDay>()
        for (i in daily.time.indices) {
            val wmoCode = daily.weather_code[i]
            val vfitCode = wmoToVFit(wmoCode)
            val low = daily.temperature_2m_min[i].toInt()
            val high = daily.temperature_2m_max[i].toInt()
            days.add(WeatherDay(vfitCode, low, high))
        }

        val timestamp = (System.currentTimeMillis() / 1000).toInt()
        return WeatherForecast(timestamp, days.take(3))
    }

    private fun wmoToVFit(wmoCode: Int): Int {
        return when (wmoCode) {
            0 -> 0
            1 -> 10
            2 -> 11
            3 -> 12
            45, 48 -> 13
            51, 53, 55 -> 14
            61, 63, 65 -> 15
            71, 73, 75 -> 17
            77 -> 18
            80, 81, 82 -> 16
            85, 86 -> 18
            95 -> 19
            96, 99 -> 20
            else -> 0
        }
    }

    @Serializable
    private data class OpenMeteoResponse(
        val daily: Daily
    )

    @Serializable
    private data class Daily(
        val time: List<String>,
        val temperature_2m_max: List<Double>,
        val temperature_2m_min: List<Double>,
        val weather_code: List<Int>
    )
}
