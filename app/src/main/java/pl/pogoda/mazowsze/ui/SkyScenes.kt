package pl.pogoda.mazowsze.ui

import java.time.Instant
import java.time.LocalDateTime
import pl.pogoda.mazowsze.R
import pl.pogoda.mazowsze.data.SkyBackgroundMode
import pl.pogoda.mazowsze.data.WeatherData

internal fun skyScene(mode: SkyBackgroundMode, weather: WeatherData?, instant: Instant): SkyBackgroundMode {
    if (mode != SkyBackgroundMode.AUTOMATIC) return mode

    val now = instant.atZone(forecastZone(weather?.timezone.orEmpty())).toLocalDateTime()
    val today = weather?.days?.firstOrNull { it.date == now.toLocalDate().toString() }
    val daylight = today?.let {
        runCatching { LocalDateTime.parse(it.sunrise) to LocalDateTime.parse(it.sunset) }
            .getOrNull()?.takeIf { (sunrise, sunset) -> sunrise.isBefore(sunset) }
    }
    // Use local clock times until the forecast for this place is available.
    val sunrise = daylight?.first ?: now.toLocalDate().atTime(6, 30)
    val sunset = daylight?.second ?: now.toLocalDate().atTime(18, 30)

    return when {
        now.isBefore(sunrise.minusMinutes(30)) -> SkyBackgroundMode.NIGHT
        now.isBefore(sunrise.plusHours(2)) -> SkyBackgroundMode.MORNING
        now.isBefore(sunset.minusMinutes(75)) -> SkyBackgroundMode.NOON
        now.isBefore(sunset.plusMinutes(20)) -> SkyBackgroundMode.SUNSET
        now.isBefore(sunset.plusMinutes(90)) -> SkyBackgroundMode.EVENING
        else -> SkyBackgroundMode.NIGHT
    }
}

internal fun skySceneName(scene: SkyBackgroundMode): String = when (scene) {
    SkyBackgroundMode.AUTOMATIC -> "Automatycznie"
    SkyBackgroundMode.MORNING -> "Ranek"
    SkyBackgroundMode.NOON -> "Południe"
    SkyBackgroundMode.SUNSET -> "Zachód"
    SkyBackgroundMode.EVENING -> "Wieczór"
    SkyBackgroundMode.NIGHT -> "Noc"
}

internal fun skySceneResource(scene: SkyBackgroundMode): Int = when (scene) {
    SkyBackgroundMode.MORNING -> R.drawable.sky_morning
    SkyBackgroundMode.NOON -> R.drawable.sky_noon
    SkyBackgroundMode.EVENING -> R.drawable.sky_evening
    SkyBackgroundMode.NIGHT -> R.drawable.sky_night
    SkyBackgroundMode.AUTOMATIC, SkyBackgroundMode.SUNSET -> R.drawable.sky_reference_v2
}

internal fun skySceneDim(scene: SkyBackgroundMode): Float = when (scene) {
    SkyBackgroundMode.MORNING -> .27f
    SkyBackgroundMode.NOON -> .34f
    SkyBackgroundMode.SUNSET -> .12f
    SkyBackgroundMode.EVENING -> .10f
    SkyBackgroundMode.NIGHT -> .06f
    SkyBackgroundMode.AUTOMATIC -> .12f
}
