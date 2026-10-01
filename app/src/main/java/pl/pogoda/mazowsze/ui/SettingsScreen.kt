package pl.pogoda.mazowsze.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import java.text.DateFormat
import java.util.Date
import java.io.File
import kotlin.math.abs
import pl.pogoda.mazowsze.WeatherViewModel
import pl.pogoda.mazowsze.BuildConfig
import pl.pogoda.mazowsze.data.SkyBackgroundMode

@Composable
internal fun SettingsScreen(model: WeatherViewModel, locationMessage: String?, onLocate: () -> Unit,
    onLocationSettings: () -> Unit, onPlaceSelected: () -> Unit,
    currentScene: SkyBackgroundMode, modifier: Modifier, focusUpdates: Boolean = false) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(focusUpdates) { if (focusUpdates) listState.scrollToItem(6) }
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pendingInstallFile
        if (file != null && context.packageManager.canRequestPackageInstalls()) {
            try {
                launchApkInstaller(context, file)
                installError = null
            } catch (_: Exception) {
                installError = "Nie można uruchomić instalatora APK."
            }
        } else installError = "Zezwól Cirral na instalowanie aplikacji w ustawieniach telefonu."
        pendingInstallFile = null
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.changeUpdateNotifications(granted)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var sourcesExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            delay(350)
            model.searchPlaces(query)
        } else model.clearSearch()
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(Glyph.PIN, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Aktualne miejsce", fontSize = 11.sp, color = nightPalette.muted)
                            Text(model.place?.label ?: "Wybierz miejsce", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color(0xA314343A))
                        .clickable(onClick = onLocate).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(Glyph.LOCATE, Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Użyj lokalizacji telefonu", fontSize = 12.sp)
                    }
                    locationMessage?.let {
                        Text(it, fontSize = 11.sp, color = nightPalette.muted, modifier = Modifier.padding(top = 9.dp))
                        if (it != "Ustalam lokalizację…" && it != "Lokalizacja telefonu została ustawiona.")
                            Text("Otwórz ustawienia lokalizacji ›", fontSize = 11.sp,
                                modifier = Modifier.clickable(onClick = onLocationSettings).padding(top = 8.dp, bottom = 2.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color(0xA314343A))
                        .padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(Glyph.SEARCH, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = query, onValueChange = { query = it; if (it.trim().length >= 2) model.prepareSearch() else model.clearSearch() }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = displayFont),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) Text("Wpisz miejscowość na świecie", fontSize = 12.sp, color = nightPalette.muted)
                                    inner()
                                }
                            }
                        )
                    }
                    if (query.trim().length >= 2) {
                        when {
                            model.searchLoading -> Row(Modifier.padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = nightPalette.accent, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Szukam miejscowości…", fontSize = 11.sp, color = nightPalette.muted)
                            }
                            model.searchError != null -> Text(model.searchError!!, fontSize = 11.sp, color = nightPalette.muted, modifier = Modifier.padding(top = 12.dp))
                            model.searchResults.isEmpty() -> Text("Nie znaleziono miejscowości. Sprawdź pisownię.", fontSize = 11.sp,
                                color = nightPalette.muted, modifier = Modifier.padding(top = 12.dp))
                            else -> model.searchResults.forEach { suggestion ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    keyboard?.hide()
                                    model.selectPlace(suggestion.place)
                                    query = ""
                                    onPlaceSelected()
                                }.padding(horizontal = 8.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(suggestion.place.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(suggestion.region, fontSize = 10.sp, color = nightPalette.muted)
                                    }
                                    LineIcon(Glyph.NEXT, Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (query.isBlank()) {
        item { Text("Ostatnie lokalizacje", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    val recent = model.recentPlaces.take(3)
                    if (recent.isEmpty()) Text("Wyszukaj miejscowość lub użyj lokalizacji telefonu.",
                        fontSize = 12.sp, color = nightPalette.muted,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp))
                    recent.forEachIndexed { index, place ->
                        val active = model.place?.let { current ->
                            current.label == place.label && abs(current.latitude - place.latitude) < .01 &&
                                abs(current.longitude - place.longitude) < .01
                        } == true
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (active) Color(0x5568898B) else Color.Transparent)
                                .clickable { model.selectPlace(place); onPlaceSelected() }
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(place.label, fontSize = 13.sp, fontWeight = if(active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.label == "Moja lokalizacja")
                                    Text(String.format(Locale.US, "Z telefonu · %.3f, %.3f", place.latitude, place.longitude),
                                        fontSize = 10.sp, color = nightPalette.muted)
                            }
                            if (active) Text("✓", fontSize = 15.sp)
                        }
                        if (index < recent.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(.5.dp).background(Color(0x2DFFFFFF)))
                    }
                }
            }
        }
        }
        item { Text("Wygląd i jednostki", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
        item { BackgroundModeSelector(model, currentScene) }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Przyciemnij niebo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Łagodniejsze światło ekranu", fontSize = 10.sp, color = nightPalette.muted)
                        }
                        Switch(model.duskMode, model::changeDuskMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF63858B),
                                uncheckedThumbColor = Color(0xFFCFDADC), uncheckedTrackColor = Color(0xFF2B484F)
                            ))
                    }
                    SettingsDivider()
                    SettingsChoice("Temperatura", "°C", "°F", if(model.useFahrenheit)1 else 0) {model.setFahrenheit(it == 1)}
                    SettingsDivider()
                    SettingsChoice("Prędkość wiatru", "km/h", "mph", if(model.useMiles)1 else 0) {model.setMiles(it == 1)}
                }
            }
        }
        item { Text("Aktualizacje", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.padding(17.dp)) {
                    Text("Cirral ${BuildConfig.VERSION_NAME}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Mniej więcej co 6 godzin sprawdzamy GitHub i pobieramy nowszy APK.", fontSize = 11.sp,
                        color = nightPalette.muted, modifier = Modifier.padding(top = 3.dp))
                    Spacer(Modifier.height(14.dp))
                    val update = model.updateSnapshot
                    val status = when {
                        model.updateLoading -> "Sprawdzam aktualizacje…"
                        model.updateError != null -> model.updateError!!
                        update.checkedAt == 0L -> "Jeszcze nie sprawdzono aktualizacji."
                        update.isAvailable && model.downloadLoading -> "Pobieram wersję ${update.latestVersion}: ${model.downloadProgress}%"
                        update.isAvailable && model.updateReady -> "Wersja ${update.latestVersion} jest gotowa do instalacji."
                        update.isAvailable -> "Dostępna wersja ${update.latestVersion}"
                        else -> "Masz najnowszą wersję."
                    }
                    Text(status, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (update.checkedAt > 0L) {
                        Text("Ostatnio sprawdzono: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(update.checkedAt))}",
                            fontSize = 10.sp, color = nightPalette.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xA314343A))
                        .clickable(enabled = !model.updateLoading && !model.downloadLoading, role = Role.Button) { model.checkForUpdates() }
                        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (model.updateLoading) CircularProgressIndicator(Modifier.size(18.dp), color = nightPalette.accent, strokeWidth = 2.dp)
                        else LineIcon(Glyph.REFRESH, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Sprawdź teraz", fontSize = 12.sp)
                    }
                    if (update.isAvailable) {
                        if (update.apkUrl == null) {
                            Text("To wydanie nie zawiera pliku APK do instalacji.", fontSize = 11.sp,
                                color = nightPalette.muted, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
                        } else {
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)
                                .heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color(0x66577F85))
                                .clickable(enabled = !model.downloadLoading && !model.updateLoading, role = Role.Button) {
                                    installError = null
                                    model.downloadUpdate { file ->
                                        if (context.packageManager.canRequestPackageInstalls()) {
                                            try {
                                                launchApkInstaller(context, file)
                                                installError = null
                                            } catch (_: Exception) {
                                                installError = "Nie można uruchomić instalatora APK."
                                            }
                                        } else {
                                            pendingInstallFile = file
                                            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                Uri.parse("package:${context.packageName}")))
                                        }
                                    }
                                }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (model.downloadLoading) "Pobieranie ${model.downloadProgress}%"
                                    else if (model.updateReady) "Zainstaluj pobraną wersję"
                                    else "Pobierz APK i zainstaluj", fontSize = 12.sp)
                            }
                            Text("Instalację trzeba zatwierdzić w systemie Android.", fontSize = 10.sp,
                                color = nightPalette.muted, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                    installError?.let { Text(it, fontSize = 11.sp, color = nightPalette.muted,
                        modifier = Modifier.padding(bottom = 10.dp)) }
                    SettingsDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Powiadamiaj o aktualizacjach", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Gdy pojawi się nowe wydanie", fontSize = 10.sp, color = nightPalette.muted)
                        }
                        Switch(model.updateNotificationsEnabled, onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else model.changeUpdateNotifications(enabled)
                        }, modifier = Modifier.semantics { contentDescription = "Powiadamiaj o aktualizacjach" },
                            colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF63858B),
                            uncheckedThumbColor = Color(0xFFCFDADC), uncheckedTrackColor = Color(0xFF2B484F)
                        ))
                    }
                }
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth(), radius = 22.dp, nav = true) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { sourcesExpanded = !sourcesExpanded }
                        .padding(vertical=4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Źródła danych", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        LineIcon(if (sourcesExpanded) Glyph.DOWN else Glyph.NEXT, Modifier.size(18.dp))
                    }
                    if (sourcesExpanded) {
                        Text("Prognoza i wyszukiwanie miast: Open-Meteo / GeoNames. Radar: historyczne obserwacje RainViewer. Mapa: OpenFreeMap i OpenStreetMap.",
                            fontSize = 11.sp, lineHeight = 17.sp, color = nightPalette.muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                        Text("Open-Meteo ↗", fontSize = 11.sp, modifier = Modifier.clickable { uri.openUri("https://open-meteo.com/") }.padding(vertical = 8.dp))
                        Text("RainViewer ↗", fontSize = 11.sp, modifier = Modifier.clickable { uri.openUri("https://www.rainviewer.com/") }.padding(vertical = 8.dp))
                        Text("OpenFreeMap ↗", fontSize = 11.sp, modifier = Modifier.clickable { uri.openUri("https://openfreemap.org/") }.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

private fun launchApkInstaller(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}

@Composable
private fun BackgroundModeSelector(model: WeatherViewModel, previewScene: SkyBackgroundMode) {
    val selected = model.skyBackgroundMode
    val choices = listOf(
        SkyBackgroundMode.AUTOMATIC,
        SkyBackgroundMode.MORNING,
        SkyBackgroundMode.NOON,
        SkyBackgroundMode.SUNSET,
        SkyBackgroundMode.EVENING,
        SkyBackgroundMode.NIGHT
    )
    GlassPanel(Modifier.fillMaxWidth(), radius = 26.dp) {
        Column(Modifier.padding(16.dp)) {
            Text("Tło nieba", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Automatycznie według wschodu i zachodu słońca albo jedno wybrane tło.",
                fontSize = 10.sp, lineHeight = 15.sp, color = nightPalette.muted,
                modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(18.dp))) {
                Image(painterResource(skySceneResource(previewScene)), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    listOf(Color(0x16071525), Color(0xC3071525)))))
                Text(if (selected == SkyBackgroundMode.AUTOMATIC)
                        "Teraz: ${skySceneName(previewScene)}" else "Wybrane: ${skySceneName(previewScene)}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            Spacer(Modifier.height(9.dp))
            Column(Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    val active = choice == selected
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (active) Color(0x664C707A) else Color.Transparent)
                        .selectable(selected = active, role = Role.RadioButton) { model.changeSkyBackgroundMode(choice) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(skySceneName(choice), fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f))
                        if (active) Text("✓", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(.5.dp).background(Color(0x36FFFFFF)))
}

@Composable
private fun SettingsChoice(title: String, left: String, right: String, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x6614333A)).padding(3.dp)) {
            listOf(left, right).forEachIndexed { index, label ->
                Text(label, color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(15.dp))
                        .background(if(index == selected) Color(0x99769096) else Color.Transparent)
                        .clickable { onSelect(index) }.padding(horizontal = 11.dp, vertical = 9.dp))
            }
        }
    }
}
