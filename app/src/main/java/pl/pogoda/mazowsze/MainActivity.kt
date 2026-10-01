package pl.pogoda.mazowsze

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import pl.pogoda.mazowsze.data.Place
import pl.pogoda.mazowsze.ui.AuraApp
import pl.pogoda.mazowsze.ui.CirralLaunchScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { CirralEntry() }
    }

    @Composable
    private fun CirralEntry() {
        var showWeather by rememberSaveable { mutableStateOf(false) }
        var showLaunch by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            if (showLaunch) {
                // Draw the light launch scene before composing the weather dashboard.
                delay(200)
                showWeather = true
                delay(850)
                showLaunch = false
            } else {
                showWeather = true
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (showWeather) WeatherEntry()
            AnimatedVisibility(visible = showLaunch, exit = fadeOut(tween(380))) {
                CirralLaunchScreen()
            }
        }
    }

    @Composable
    private fun WeatherEntry() {
        val model: WeatherViewModel = viewModel()
        var locationMessage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            val allowed = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (allowed && model.place?.label == "Moja lokalizacja") {
                locationMessage = "Ustalam lokalizację…"
                getPhoneLocation(
                    onSuccess = { model.selectPlace(it); locationMessage = "Lokalizacja telefonu została ustawiona." },
                    onFailure = { locationMessage = it }
                )
            }
        }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (grants.values.any { it }) {
                getPhoneLocation(
                    onSuccess = { model.selectPlace(it); locationMessage = "Lokalizacja telefonu została ustawiona." },
                    onFailure = { locationMessage = it }
                )
            } else {
                locationMessage = "Brak dostępu do lokalizacji. Nadaj uprawnienie albo wpisz miejscowość."
            }
        }
        AuraApp(
            model = model,
            locationMessage = locationMessage,
            onLocate = {
                locationMessage = "Ustalam lokalizację…"
                val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    getPhoneLocation(
                        onSuccess = { model.selectPlace(it); locationMessage = "Lokalizacja telefonu została ustawiona." },
                        onFailure = { locationMessage = it }
                    )
                } else {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            onLocationSettings = {
                val allowed = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (allowed) startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        )
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
        else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun getPhoneLocation(onSuccess: (Place) -> Unit, onFailure: (String) -> Unit) {
        if (!isLocationEnabled()) {
            onFailure("Lokalizacja telefonu jest wyłączona. Włącz ją w ustawieniach lub wpisz miejscowość.")
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var fallbackStarted = false
        lateinit var timeout: Runnable
        fun finish(place: Place? = null) {
            if (finished) return
            finished = true
            handler.removeCallbacks(timeout)
            if (place != null) onSuccess(place)
            else onFailure("Telefon nie ustalił położenia. Spróbuj ponownie albo wpisz miejscowość.")
        }
        fun useLocation(location: Location) = finish(Place(location.latitude, location.longitude, "Moja lokalizacja"))
        fun fallback() {
            if (finished || fallbackStarted) return
            fallbackStarted = true
            client.lastLocation
                .addOnSuccessListener { previous ->
                    if (previous != null && System.currentTimeMillis() - previous.time < 2 * 60 * 60 * 1000L) useLocation(previous)
                    else finish()
                }
                .addOnFailureListener { finish() }
        }
        timeout = Runnable { token.cancel(); fallback() }
        handler.postDelayed(timeout, 12_000)
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        client.getCurrentLocation(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location != null) useLocation(location) else fallback()
            }
            .addOnFailureListener { fallback() }
    }
}
