package pl.pogoda.mazowsze.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val polish = Locale.forLanguageTag("pl-PL")
private val localZone = ZoneId.systemDefault()

fun forecastZone(timezone: String): ZoneId = runCatching { ZoneId.of(timezone) }.getOrDefault(localZone)

fun degrees(value: Double): String = "${value.roundToInt()}°"
fun millimeters(value: Double): String = String.format(polish, "%.1f mm", value)
fun hour(value: String): String = runCatching {
    LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm", polish))
}.getOrElse { value.takeLast(5) }

fun dayName(value: String, timezone: String): String = runCatching {
    val date = LocalDate.parse(value)
    val today = LocalDate.now(forecastZone(timezone))
    if (date == today) "Dziś"
    else if (date == today.plusDays(1)) "Jutro"
    else date.format(DateTimeFormatter.ofPattern("EEEE", polish)).replaceFirstChar { it.uppercase(polish) }
}.getOrElse { value }

fun dayDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM", polish))
}.getOrElse { value }

fun radarTime(seconds: Long): String = Instant.ofEpochSecond(seconds)
    .atZone(localZone).format(DateTimeFormatter.ofPattern("HH:mm", polish))

fun radarDateTime(seconds: Long): String = Instant.ofEpochSecond(seconds)
    .atZone(localZone).format(DateTimeFormatter.ofPattern("dd.MM, HH:mm", polish))

fun updateTime(millis: Long, timezone: String): String = Instant.ofEpochMilli(millis)
    .atZone(forecastZone(timezone)).format(DateTimeFormatter.ofPattern("HH:mm", polish))

fun condition(code: Int): String = when (code) {
    0 -> "Bezchmurnie"
    1 -> "Przeważnie pogodnie"
    2 -> "Częściowe zachmurzenie"
    3 -> "Pochmurno"
    45, 48 -> "Mgła"
    51, 53, 55, 56, 57 -> "Mżawka"
    61, 63, 65, 66, 67 -> "Deszcz"
    71, 73, 75, 77 -> "Śnieg"
    80, 81, 82 -> "Przelotny deszcz"
    85, 86 -> "Przelotny śnieg"
    95, 96, 99 -> "Burza"
    else -> "Zmienna pogoda"
}

fun weatherSymbol(code: Int, isDay: Boolean = true): String = when (code) {
    0, 1 -> if (isDay) "☀" else "☾"
    2, 3 -> "☁"
    45, 48 -> "≋"
    71, 73, 75, 77, 85, 86 -> "❄"
    95, 96, 99 -> "ϟ"
    else -> "☂"
}
