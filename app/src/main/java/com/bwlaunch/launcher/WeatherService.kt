package com.bwlaunch.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple weather service using Open-Meteo API.
 * Free API, no key required.
 *
 * API docs: https://open-meteo.com/en/docs
 */
class WeatherService {

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        // Cache weather for 30 minutes (e-ink friendly - reduces updates)
        const val CACHE_DURATION_MS = 30 * 60 * 1000L
    }

    data class WeatherData(
        val temperature: Int,
        val weatherCode: Int,
        val isDay: Boolean,
        val useCelsius: Boolean = false
    ) {
        /**
         * Returns a simple weather description based on WMO weather code.
         * https://open-meteo.com/en/docs (WMO Weather interpretation codes)
         */
        fun getConditionText(): String {
            return when (weatherCode) {
                0 -> "Clear"
                1, 2, 3 -> "Cloudy"
                45, 48 -> "Foggy"
                51, 53, 55 -> "Drizzle"
                56, 57 -> "Freezing Drizzle"
                61, 63, 65 -> "Rain"
                66, 67 -> "Freezing Rain"
                71, 73, 75 -> "Snow"
                77 -> "Snow Grains"
                80, 81, 82 -> "Showers"
                85, 86 -> "Snow Showers"
                95 -> "Thunderstorm"
                96, 99 -> "Thunderstorm"
                else -> "Unknown"
            }
        }

        /**
         * Returns a simple text icon for e-ink display.
         */
        fun getIcon(): String {
            return when (weatherCode) {
                0 -> if (isDay) "\u2600" else "\u263D" // Sun or Moon
                1, 2, 3 -> "\u26C5" // Partly cloudy
                45, 48 -> "\u2601" // Cloud (fog)
                51, 53, 55, 61, 63, 65, 80, 81, 82 -> "\u2614" // Rain
                56, 57, 66, 67 -> "\u2744" // Freezing
                71, 73, 75, 77, 85, 86 -> "\u2744" // Snow
                95, 96, 99 -> "\u26A1" // Thunderstorm
                else -> "\u2601" // Default: cloud
            }
        }

        /**
         * Format for display: "72° Clear"
         */
        fun toDisplayString(): String {
            return "$temperature° ${getConditionText()}"
        }

        /**
         * Serialize to cache string
         */
        fun toCache(): String {
            return "$temperature|$weatherCode|$isDay|$useCelsius"
        }

        companion object {
            /**
             * Deserialize from cache string
             */
            fun fromCache(cache: String): WeatherData? {
                return try {
                    val parts = cache.split("|")
                    if (parts.size >= 3) {
                        WeatherData(
                            temperature = parts[0].toInt(),
                            weatherCode = parts[1].toInt(),
                            isDay = parts[2].toBoolean(),
                            useCelsius = parts.getOrNull(3)?.toBoolean() ?: false
                        )
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Fetch current weather for the given coordinates.
     * @param useCelsius If true, returns temperature in Celsius; otherwise Fahrenheit.
     * Returns null on failure.
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double, useCelsius: Boolean = false): WeatherData? {
        return withContext(Dispatchers.IO) {
            try {
                val tempUnit = if (useCelsius) "celsius" else "fahrenheit"
                val url = URL(
                    "$BASE_URL?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&temperature_unit=$tempUnit" +
                    "&timezone=auto"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseWeatherResponse(response, useCelsius)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseWeatherResponse(json: String, useCelsius: Boolean): WeatherData? {
        return try {
            val root = JSONObject(json)
            val current = root.getJSONObject("current")

            WeatherData(
                temperature = current.getDouble("temperature_2m").toInt(),
                weatherCode = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
                useCelsius = useCelsius
            )
        } catch (e: Exception) {
            null
        }
    }
}
