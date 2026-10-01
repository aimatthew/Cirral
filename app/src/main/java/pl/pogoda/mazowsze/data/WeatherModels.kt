package pl.pogoda.mazowsze.data

data class Place(val latitude: Double, val longitude: Double, val label: String)
data class PlaceSuggestion(val place: Place, val region: String)

data class CurrentWeather(
    val time: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val precipitation: Double,
    val windSpeed: Double,
    val code: Int,
    val isDay: Boolean
)

data class HourWeather(
    val time: String,
    val temperature: Double,
    val apparentTemperature: Double?,
    val precipitationProbability: Int,
    val precipitation: Double,
    val code: Int,
    val windSpeed: Double,
    val isDay: Boolean = true,
    val windGust: Double? = null,
    val uvIndex: Double? = null
)

data class DayWeather(
    val date: String,
    val minimum: Double,
    val maximum: Double,
    val precipitation: Double,
    val precipitationProbability: Int,
    val code: Int,
    val sunrise: String,
    val sunset: String,
    val uvIndex: Double? = null
)

data class WeatherData(
    val current: CurrentWeather,
    val hours: List<HourWeather>,
    val days: List<DayWeather>,
    val fetchedAt: Long,
    val timezone: String
)

fun gustExtremes(hours: List<HourWeather>): Pair<HourWeather, HourWeather>? {
    val available = hours.filter { it.windGust != null }
    val lowest = available.minByOrNull { it.windGust ?: Double.POSITIVE_INFINITY } ?: return null
    val highest = available.maxByOrNull { it.windGust ?: Double.NEGATIVE_INFINITY } ?: return null
    return lowest to highest
}

data class RadarFrame(val timeSeconds: Long, val path: String)
data class RadarData(val host: String, val frames: List<RadarFrame>, val fetchedAt: Long)

