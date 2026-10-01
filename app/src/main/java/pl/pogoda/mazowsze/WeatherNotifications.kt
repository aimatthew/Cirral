package pl.pogoda.mazowsze

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.pogoda.mazowsze.data.Place
import pl.pogoda.mazowsze.data.WeatherRepository
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** User-selected hours are local device time; rain alerts run independently of those hours. */
class WeatherNotificationSettings(context: Context) {
    private val preferences = context.getSharedPreferences("weather_notifications", Context.MODE_PRIVATE)

    fun enabled() = preferences.getBoolean("enabled", false)
    fun hours(): Set<Int> = preferences.getStringSet("hours", emptySet()).orEmpty()
        .mapNotNull { it.toIntOrNull()?.takeIf { hour -> hour in 0..23 } }.toSet()

    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("enabled", value).apply() }
    fun setHours(value: Set<Int>) {
        preferences.edit().putStringSet("hours", value.filter { it in 0..23 }.map(Int::toString).toSet()).apply()
    }

    fun shouldSendRain(place: Place, onset: Instant, now: Long): Boolean {
        val location = "${(place.latitude * 100).roundToInt()},${(place.longitude * 100).roundToInt()}"
        val previousLocation = preferences.getString("rain_location", null)
        val previousOnset = preferences.getLong("rain_onset", 0L)
        val previousSent = preferences.getLong("rain_sent", 0L)
        return previousLocation != location ||
            (kotlin.math.abs(onset.toEpochMilli() - previousOnset) > TimeUnit.HOURS.toMillis(2) &&
                now - previousSent > TimeUnit.HOURS.toMillis(3))
    }

    fun markRain(place: Place, onset: Instant, now: Long) {
        preferences.edit().putString("rain_location", "${(place.latitude * 100).roundToInt()},${(place.longitude * 100).roundToInt()}")
            .putLong("rain_onset", onset.toEpochMilli()).putLong("rain_sent", now).apply()
    }

    fun wasTemperatureSent(place: Place, hour: Int): Boolean {
        val key = "${java.time.LocalDate.now()}-$hour-${(place.latitude * 100).roundToInt()}-${(place.longitude * 100).roundToInt()}"
        return preferences.getString("temperature_sent", null) == key
    }

    fun markTemperatureSent(place: Place, hour: Int) {
        val key = "${java.time.LocalDate.now()}-$hour-${(place.latitude * 100).roundToInt()}-${(place.longitude * 100).roundToInt()}"
        preferences.edit().putString("temperature_sent", key).apply()
    }
}

object WeatherNotificationScheduler {
    private const val RAIN_WORK = "cirral_rain_alerts"
    private const val DAILY_WORK = "cirral_daily_temperature"

    fun sync(context: Context) {
        val manager = WorkManager.getInstance(context)
        val settings = WeatherNotificationSettings(context)
        if (!settings.enabled()) {
            manager.cancelUniqueWork(RAIN_WORK)
            manager.cancelUniqueWork(DAILY_WORK)
            return
        }
        val rain = PeriodicWorkRequestBuilder<RainAlertWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(RAIN_WORK, ExistingPeriodicWorkPolicy.KEEP, rain)
        scheduleNextTemperature(context)
    }

    fun scheduleNextTemperature(context: Context) {
        val manager = WorkManager.getInstance(context)
        val hours = WeatherNotificationSettings(context).hours()
        if (!WeatherNotificationSettings(context).enabled() || hours.isEmpty()) {
            manager.cancelUniqueWork(DAILY_WORK)
            return
        }
        val now = java.time.ZonedDateTime.now()
        val next = hours.map { hour ->
            var candidate = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
            if (!candidate.isAfter(now.plusMinutes(1))) candidate = candidate.plusDays(1)
            candidate
        }.minOrNull() ?: return
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TemperatureAlertWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniqueWork(DAILY_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

private object WeatherNotificationDisplay {
    private const val RAIN_CHANNEL = "cirral_rain"
    private const val TEMP_CHANNEL = "cirral_temperature"

    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, rain: Boolean, title: String, body: String, id: Int): Boolean {
        if (!allowed(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = if (rain) RAIN_CHANNEL else TEMP_CHANNEL
        manager.createNotificationChannel(NotificationChannel(channel,
            if (rain) "Alerty opadowe" else "Temperatura lokalna", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent).setAutoCancel(true).build())
        return true
    }

    fun preview(context: Context) {
        val repository = WeatherRepository(context)
        val zone = repository.cachedWeather()?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val onset = Instant.now().plus(Duration.ofHours(2))
        show(context, true, "Możliwy deszcz ${approximateRainTime(onset, zone)}",
            "Przykład · za około 2 godz. · ${repository.lastPlace()?.label ?: "Twoja okolica"}. Sprawdź prognozę przed wyjściem.", 2101)
        show(context, false, "Temperatura w Twojej okolicy: 18°C",
            "Przykładowe powiadomienie o temperaturze · Cirral", 2102)
    }
}

class RainAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = WeatherNotificationSettings(applicationContext)
        val place = WeatherRepository(applicationContext).lastPlace() ?: return Result.success()
        if (!settings.enabled() || !WeatherNotificationDisplay.allowed(applicationContext)) return Result.success()
        return try {
            val candidate = RainForecast.find(place)
            if (candidate != null && settings.shouldSendRain(place, candidate.onset, System.currentTimeMillis())) {
                val minutes = Duration.between(Instant.now(), candidate.onset).toMinutes().coerceAtLeast(0)
                val whenText = when {
                    minutes < 45 -> "w ciągu godziny"
                    minutes < 90 -> "za około godzinę"
                    else -> "za około 2 godz."
                }
                if (WeatherNotificationDisplay.show(applicationContext, true,
                        "Możliw${if (candidate.kind == "deszcz") "y" else "a"} ${candidate.kind} ${approximateRainTime(candidate.onset, candidate.zone)}",
                        "${whenText.replaceFirstChar { it.uppercase() }} · ${place.label}. Sprawdź radar i prognozę.", 2001)) {
                    settings.markRain(place, candidate.onset, System.currentTimeMillis())
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.success() // A stale or incomplete forecast must not produce an alert.
        }
    }
}

class TemperatureAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        try {
            val settings = WeatherNotificationSettings(applicationContext)
            val place = WeatherRepository(applicationContext).lastPlace()
            val hour = java.time.LocalTime.now().hour
            if (settings.enabled() && place != null && hour in settings.hours() &&
                !settings.wasTemperatureSent(place, hour) && WeatherNotificationDisplay.allowed(applicationContext)) {
                val weather = WeatherRepository(applicationContext).fetchWeather(place)
                val fahrenheit = applicationContext.getSharedPreferences("aura_display", 0).getBoolean("fahrenheit", false)
                val value = if (fahrenheit) weather.current.temperature * 9 / 5 + 32 else weather.current.temperature
                val unit = if (fahrenheit) "°F" else "°C"
                if (WeatherNotificationDisplay.show(applicationContext, false,
                    "Temperatura w okolicy: ${value.roundToInt()}$unit",
                    "Teraz ${value.roundToInt()}$unit · ${place.label}. Otwórz Cirral, aby zobaczyć prognozę.", 2002)) {
                    settings.markTemperatureSent(place, hour)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next selected hour will try again.
        } finally {
            WeatherNotificationScheduler.scheduleNextTemperature(applicationContext)
        }
        return Result.success()
    }
}

private data class RainCandidate(val onset: Instant, val kind: String, val zone: ZoneId)

private fun approximateRainTime(onset: Instant, zone: ZoneId): String {
    val local = onset.atZone(zone)
    val day = if (local.toLocalDate().isAfter(Instant.now().atZone(zone).toLocalDate())) "jutro około " else "około "
    return day + local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pl-PL")))
}

/** Two forecast feeds must agree; radar history is not a two-hour forecast. */
private object RainForecast {
    private val format = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    suspend fun find(place: Place): RainCandidate? = withContext(Dispatchers.IO) {
        val latitude = String.format(Locale.US, "%.4f", place.latitude)
        val longitude = String.format(Locale.US, "%.4f", place.longitude)
        val base = "latitude=$latitude&longitude=$longitude&timezone=auto"
        val primary = JSONObject(get("https://api.open-meteo.com/v1/forecast?$base" +
            "&minutely_15=precipitation,weather_code&forecast_minutely_15=16" +
            "&hourly=precipitation_probability&forecast_hours=5&models=best_match"))
        val zone = ZoneId.of(primary.getString("timezone"))
        val minutely = primary.getJSONObject("minutely_15")
        val times = minutely.getJSONArray("time")
        val amounts = minutely.getJSONArray("precipitation")
        val codes = minutely.getJSONArray("weather_code")
        val probabilities = primary.getJSONObject("hourly")
        val hourlyTimes = probabilities.getJSONArray("time")
        val hourlyValues = probabilities.getJSONArray("precipitation_probability")
        val now = Instant.now()
        if (amounts.optDouble(0, 0.0) >= 0.15) return@withContext null
        val candidates = (0 until (times.length() - 1)).mapNotNull { i ->
            val onset = LocalDateTime.parse(times.getString(i), format).atZone(zone).toInstant()
            val minutes = Duration.between(now, onset).toMinutes()
            if (minutes !in 25..150 || amounts.optDouble(i, 0.0) + amounts.optDouble(i + 1, 0.0) < 0.35) return@mapNotNull null
            if (codes.optInt(i, 0) !in 51..67 && codes.optInt(i, 0) !in 80..82 && codes.optInt(i, 0) !in 95..99) return@mapNotNull null
            if (codes.optInt(i, 0) in 56..57 || codes.optInt(i, 0) in 66..67) return@mapNotNull null
            val closest = (0 until hourlyTimes.length()).minByOrNull { h ->
                val hour = LocalDateTime.parse(hourlyTimes.getString(h), format).atZone(zone).toInstant()
                kotlin.math.abs(Duration.between(hour, onset).toMinutes())
            }
            val probability = closest?.let { hourlyValues.optInt(it, 0) } ?: 0
            if (probability < 65) return@mapNotNull null
            i to onset
        }
        val first = candidates.firstOrNull() ?: return@withContext null
        // ECMWF is a separate forecast model. Outside the native 15-minute
        // coverage, Best Match is interpolated and needs a stronger signal.
        val inEurope = place.latitude in 35.0..70.0 && place.longitude in -12.0..40.0
        if (!inEurope && amounts.optDouble(first.first, 0.0) + amounts.optDouble(first.first + 1, 0.0) < 0.7) {
            return@withContext null
        }
        val second = JSONObject(get("https://api.open-meteo.com/v1/ecmwf?$base" +
            "&hourly=precipitation&forecast_hours=5"))
        val hours = second.getJSONObject("hourly")
        val secondaryTimes = hours.getJSONArray("time")
        val secondaryAmounts = hours.getJSONArray("precipitation")
        val confirmed = (0 until secondaryTimes.length()).any { i ->
            val hour = LocalDateTime.parse(secondaryTimes.getString(i), format).atZone(zone).toInstant()
            kotlin.math.abs(Duration.between(hour, first.second).toMinutes()) <= 75 &&
                secondaryAmounts.optDouble(i, 0.0) >= 0.25
        }
        if (!confirmed) return@withContext null
        val code = codes.optInt(first.first, 0)
        val kind = when (code) {
            in 95..99 -> "burza"
            in 51..55 -> "mżawka"
            else -> "deszcz"
        }
        RainCandidate(first.second, kind, zone)
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        try {
            check(connection.responseCode in 200..299)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}

fun showWeatherNotificationPreview(context: Context): Boolean {
    if (!WeatherNotificationDisplay.allowed(context)) return false
    WeatherNotificationDisplay.preview(context)
    return true
}

class WeatherTimeChangedReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
            WeatherNotificationScheduler.sync(context)
        }
    }
}
