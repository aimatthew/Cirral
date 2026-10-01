package pl.pogoda.mazowsze.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class WeatherRepository(context: Context) {
    private val preferences = context.getSharedPreferences("aura_local", Context.MODE_PRIVATE)
    private val recentPlacesKey = "recent_places"

    fun lastPlace(): Place? {
        if (!preferences.contains("latitude")) return null
        return Place(
            preferences.getFloat("latitude", 52.2297f).toDouble(),
            preferences.getFloat("longitude", 21.0122f).toDouble(),
            preferences.getString("label", "Moja lokalizacja") ?: "Moja lokalizacja"
        )
    }

    fun recentPlaces(): List<Place> {
        val stored = preferences.getString(recentPlacesKey, null)
            ?: return lastPlace()?.let { listOf(it) }.orEmpty()
        return runCatching {
            val entries = JSONArray(stored)
            (0 until entries.length()).mapNotNull { index ->
                val item = entries.optJSONObject(index) ?: return@mapNotNull null
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                val label = item.optString("label")
                if (latitude.isFinite() && longitude.isFinite() && label.isNotBlank())
                    Place(latitude, longitude, label) else null
            }.take(3)
        }.getOrElse { lastPlace()?.let { listOf(it) }.orEmpty() }
    }

    fun savePlace(place: Place): List<Place> {
        val recent = (listOf(place) + recentPlaces().filterNot { previous ->
            place.label.equals(previous.label, ignoreCase = true) &&
                kotlin.math.abs(place.latitude - previous.latitude) < .01 &&
                kotlin.math.abs(place.longitude - previous.longitude) < .01
        }).take(3)
        val recentJson = JSONArray().apply {
            recent.forEach { item ->
                put(JSONObject().put("latitude", item.latitude).put("longitude", item.longitude).put("label", item.label))
            }
        }
        preferences.edit()
            .putFloat("latitude", place.latitude.toFloat())
            .putFloat("longitude", place.longitude.toFloat())
            .putString("label", place.label)
            .putString(recentPlacesKey, recentJson.toString())
            .apply()
        return recent
    }

    fun cachedWeather(): WeatherData? {
        val place = lastPlace() ?: return null
        if (preferences.getFloat("weather_latitude", Float.NaN).toDouble() != place.latitude ||
            preferences.getFloat("weather_longitude", Float.NaN).toDouble() != place.longitude
        ) return null
        val json = preferences.getString("weather_json", null) ?: return null
        return runCatching {
            parseWeather(JSONObject(json), preferences.getLong("weather_fetched", 0))
        }.getOrNull()
    }

    suspend fun fetchWeather(place: Place): WeatherData = withContext(Dispatchers.IO) {
        val latitude = String.format(Locale.US, "%.4f", place.latitude)
        val longitude = String.format(Locale.US, "%.4f", place.longitude)
        val parameters = linkedMapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "current" to "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m",
            "hourly" to "temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_gusts_10m,is_day,uv_index",
            "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,sunrise,sunset,uv_index_max",
            "timezone" to "auto",
            "forecast_days" to "10",
            "models" to "best_match"
        )
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}" 
        }
        val json = requestJson("https://api.open-meteo.com/v1/forecast?$query")
        val now = System.currentTimeMillis()
        val result = parseWeather(JSONObject(json), now)
        preferences.edit()
            .putString("weather_json", json)
            .putLong("weather_fetched", now)
            .putFloat("weather_latitude", place.latitude.toFloat())
            .putFloat("weather_longitude", place.longitude.toFloat())
            .apply()
        result
    }

    suspend fun fetchRadar(): RadarData = withContext(Dispatchers.IO) {
        val root = JSONObject(requestJson("https://api.rainviewer.com/public/weather-maps.json"))
        val host = root.getString("host")
        require(host.startsWith("https://")) { "Nieprawidłowy adres radaru" }
        val past = root.getJSONObject("radar").getJSONArray("past")
        val frames = (0 until past.length()).map { index ->
            val item = past.getJSONObject(index)
            RadarFrame(item.getLong("time"), item.getString("path"))
        }.sortedBy { it.timeSeconds }
        RadarData(host, frames, System.currentTimeMillis())
    }

    suspend fun searchPlaces(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val root = JSONObject(requestJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=pl"))
        val results = root.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).map { index ->
            val item = results.getJSONObject(index)
            PlaceSuggestion(
                Place(item.getDouble("latitude"), item.getDouble("longitude"), item.getString("name")),
                listOf(item.optString("admin1"), item.optString("country"))
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
            )
        }.distinctBy { Triple(it.place.label, it.place.latitude, it.place.longitude) }
    }

    private fun parseWeather(root: JSONObject, fetchedAt: Long): WeatherData {
        val current = root.getJSONObject("current")
        val currentWeather = CurrentWeather(
            time = current.getString("time"),
            temperature = current.getDouble("temperature_2m"),
            apparentTemperature = current.getDouble("apparent_temperature"),
            humidity = current.getInt("relative_humidity_2m"),
            precipitation = current.getDouble("precipitation"),
            windSpeed = current.getDouble("wind_speed_10m"),
            code = current.getInt("weather_code"),
            isDay = current.getInt("is_day") == 1
        )
        val hourly = root.getJSONObject("hourly")
        val hourTimes = hourly.getJSONArray("time")
        val hours = (0 until hourTimes.length()).map { index ->
            HourWeather(
                time = hourTimes.getString(index),
                temperature = hourly.getJSONArray("temperature_2m").getDouble(index),
                apparentTemperature = hourly.optJSONArray("apparent_temperature")?.optDouble(index)?.takeIf { it.isFinite() },
                precipitationProbability = hourly.getJSONArray("precipitation_probability").optInt(index),
                precipitation = hourly.getJSONArray("precipitation").optDouble(index),
                code = hourly.getJSONArray("weather_code").optInt(index),
                windSpeed = hourly.getJSONArray("wind_speed_10m").optDouble(index),
                isDay = hourly.optJSONArray("is_day")?.optInt(index, 1) != 0,
                windGust = hourly.optJSONArray("wind_gusts_10m")?.optDouble(index)?.takeIf { it.isFinite() },
                uvIndex = hourly.optJSONArray("uv_index")?.optDouble(index)?.takeIf { it.isFinite() }
            )
        }
        val daily = root.getJSONObject("daily")
        val dayTimes = daily.getJSONArray("time")
        val days = (0 until dayTimes.length()).map { index ->
            DayWeather(
                date = dayTimes.getString(index),
                minimum = daily.getJSONArray("temperature_2m_min").getDouble(index),
                maximum = daily.getJSONArray("temperature_2m_max").getDouble(index),
                precipitation = daily.getJSONArray("precipitation_sum").optDouble(index),
                precipitationProbability = daily.getJSONArray("precipitation_probability_max").optInt(index),
                code = daily.getJSONArray("weather_code").optInt(index),
                sunrise = daily.getJSONArray("sunrise").getString(index),
                sunset = daily.getJSONArray("sunset").getString(index),
                uvIndex = daily.optJSONArray("uv_index_max")?.optDouble(index)?.takeIf { it.isFinite() }
            )
        }
        return WeatherData(currentWeather, hours, days, fetchedAt, root.optString("timezone", "Europe/Warsaw"))
    }

    private fun requestJson(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Cirral/1.0 personal Android app")
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Serwer zwrócił błąd ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
