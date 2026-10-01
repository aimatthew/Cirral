package pl.pogoda.mazowsze

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import pl.pogoda.mazowsze.data.Place
import pl.pogoda.mazowsze.data.AppUpdates
import pl.pogoda.mazowsze.data.UpdateSnapshot
import pl.pogoda.mazowsze.data.PlaceSuggestion
import pl.pogoda.mazowsze.data.RadarData
import pl.pogoda.mazowsze.data.SkyBackgroundMode
import pl.pogoda.mazowsze.data.WeatherData
import pl.pogoda.mazowsze.data.WeatherRepository

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WeatherRepository(application)
    private val displaySettings = application.getSharedPreferences("aura_display", 0)
    private val appUpdates = AppUpdates(application)
    private val weatherNotifications = WeatherNotificationSettings(application)
    private var updateStateRequestId = 0
    private val updateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        viewModelScope.launch {
            if (key == "notifications_enabled") updateNotificationsEnabled = appUpdates.notificationsEnabled()
            if (key == "checked_at" || key == "downloaded_version") {
                val requestId = ++updateStateRequestId
                val latest = appUpdates.snapshot()
                updateSnapshot = latest
                val ready = withContext(Dispatchers.IO) { appUpdates.downloadedApk(latest) != null }
                if (requestId == updateStateRequestId) updateReady = ready
            }
        }
    }
    private var weatherRequestId = 0
    private var searchRequestId = 0

    var useFahrenheit by mutableStateOf(displaySettings.getBoolean("fahrenheit", false))
        private set
    var useMiles by mutableStateOf(displaySettings.getBoolean("miles", false))
        private set
    var duskMode by mutableStateOf(displaySettings.getBoolean("dusk", false))
        private set
    var skyBackgroundMode by mutableStateOf(
        SkyBackgroundMode.entries.firstOrNull { it.name == displaySettings.getString("sky_background_mode", null) }
            ?: SkyBackgroundMode.AUTOMATIC
    )
        private set
    var place by mutableStateOf(repository.lastPlace())
        private set
    var recentPlaces by mutableStateOf(repository.recentPlaces())
        private set
    var weather by mutableStateOf(repository.cachedWeather())
        private set
    var radar by mutableStateOf<RadarData?>(null)
        private set
    var weatherLoading by mutableStateOf(false)
        private set
    var radarLoading by mutableStateOf(false)
        private set
    var weatherError by mutableStateOf<String?>(null)
        private set
    var radarError by mutableStateOf<String?>(null)
        private set
    var searchResults by mutableStateOf<List<PlaceSuggestion>>(emptyList())
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var updateSnapshot by mutableStateOf(appUpdates.snapshot())
        private set
    var updateNotificationsEnabled by mutableStateOf(appUpdates.notificationsEnabled())
        private set
    var weatherNotificationsEnabled by mutableStateOf(weatherNotifications.enabled())
        private set
    var temperatureNotificationHours by mutableStateOf(weatherNotifications.hours())
        private set
    var updateLoading by mutableStateOf(false)
        private set
    var updateError by mutableStateOf<String?>(null)
        private set
    var updateReady by mutableStateOf(false)
        private set
    var downloadLoading by mutableStateOf(false)
        private set
    var downloadProgress by mutableIntStateOf(0)
        private set

    init {
        appUpdates.observe(updateListener)
        viewModelScope.launch {
            updateReady = withContext(Dispatchers.IO) { appUpdates.downloadedApk(updateSnapshot) != null }
        }
        place?.let { refreshWeather(it) }
        refreshRadar()
    }

    override fun onCleared() {
        appUpdates.stopObserving(updateListener)
        super.onCleared()
    }

    fun changeUpdateNotifications(enabled: Boolean) {
        appUpdates.setNotificationsEnabled(enabled)
        updateNotificationsEnabled = enabled
    }

    fun changeWeatherNotifications(enabled: Boolean) {
        weatherNotifications.setEnabled(enabled)
        weatherNotificationsEnabled = enabled
        WeatherNotificationScheduler.sync(getApplication())
    }

    fun changeTemperatureNotificationHours(hours: Set<Int>) {
        weatherNotifications.setHours(hours)
        temperatureNotificationHours = weatherNotifications.hours()
        WeatherNotificationScheduler.sync(getApplication())
    }

    fun checkForUpdates() {
        if (updateLoading) return
        viewModelScope.launch {
            updateLoading = true
            updateError = null
            try {
                updateSnapshot = appUpdates.check()
                updateReady = false
                if (updateSnapshot.isAvailable && updateSnapshot.apkUrl != null) {
                    downloadUpdate()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateError = "Nie udało się sprawdzić aktualizacji. Sprawdź połączenie i spróbuj ponownie."
            } finally {
                updateLoading = false
            }
        }
    }

    fun downloadUpdate(onReady: (File) -> Unit = {}) {
        if (downloadLoading || !updateSnapshot.isAvailable) return
        val update = updateSnapshot
        viewModelScope.launch {
            downloadLoading = true
            downloadProgress = 0
            updateError = null
            try {
                var lastProgress = -1
                val file = appUpdates.download(update) { progress ->
                    if (progress != lastProgress) {
                        lastProgress = progress
                        viewModelScope.launch { downloadProgress = progress }
                    }
                }
                updateReady = true
                onReady(file)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateError = "Nie udało się pobrać poprawnego pliku APK. Spróbuj ponownie."
            } finally {
                downloadLoading = false
            }
        }
    }

    fun selectPlace(value: Place) {
        clearSearch()
        place = value
        weather = null
        recentPlaces = repository.savePlace(value)
        refreshWeather(value)
    }

    fun clearSearch() {
        searchRequestId++
        searchResults = emptyList()
        searchLoading = false
        searchError = null
    }

    fun prepareSearch() {
        searchRequestId++
        searchResults = emptyList()
        searchLoading = true
        searchError = null
    }

    fun searchPlaces(query: String) {
        if (query.trim().length < 2) { clearSearch(); return }
        val requestId = ++searchRequestId
        searchLoading = true
        searchError = null
        viewModelScope.launch {
            runCatching { repository.searchPlaces(query) }
                .onSuccess { if (requestId == searchRequestId) searchResults = it }
                .onFailure { if (requestId == searchRequestId) searchError = "Nie udało się wyszukać miejscowości. Sprawdź połączenie." }
            if (requestId == searchRequestId) searchLoading = false
        }
    }

    fun setFahrenheit(value: Boolean) {
        useFahrenheit = value
        displaySettings.edit().putBoolean("fahrenheit", value).apply()
    }

    fun setMiles(value: Boolean) {
        useMiles = value
        displaySettings.edit().putBoolean("miles", value).apply()
    }

    fun changeDuskMode(value: Boolean) {
        duskMode = value
        displaySettings.edit().putBoolean("dusk", value).apply()
    }

    fun changeSkyBackgroundMode(value: SkyBackgroundMode) {
        skyBackgroundMode = value
        displaySettings.edit().putString("sky_background_mode", value.name).apply()
    }

    fun refreshAll() {
        place?.let(::refreshWeather)
        refreshRadar()
    }

    fun refreshWeather(value: Place? = place) {
        val target = value ?: return
        val requestId = ++weatherRequestId
        weatherLoading = true
        weatherError = null
        viewModelScope.launch {
            runCatching { repository.fetchWeather(target) }
                .onSuccess { result -> if (requestId == weatherRequestId && place == target) weather = result }
                .onFailure { if (requestId == weatherRequestId) weatherError = it.message ?: "Nie udało się pobrać prognozy" }
            if (requestId == weatherRequestId) weatherLoading = false
        }
    }

    fun refreshRadar() {
        if (radarLoading) return
        viewModelScope.launch {
            radarLoading = true
            radarError = null
            runCatching { repository.fetchRadar() }
                .onSuccess { radar = it }
                .onFailure { radarError = it.message ?: "Nie udało się pobrać radaru" }
            radarLoading = false
        }
    }
}
