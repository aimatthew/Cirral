package pl.pogoda.mazowsze

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.pogoda.mazowsze.data.Place
import pl.pogoda.mazowsze.data.PlaceSuggestion
import pl.pogoda.mazowsze.data.RadarData
import pl.pogoda.mazowsze.data.SkyBackgroundMode
import pl.pogoda.mazowsze.data.WeatherData
import pl.pogoda.mazowsze.data.WeatherRepository

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WeatherRepository(application)
    private val displaySettings = application.getSharedPreferences("aura_display", 0)
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

    init {
        place?.let { refreshWeather(it) }
        refreshRadar()
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
