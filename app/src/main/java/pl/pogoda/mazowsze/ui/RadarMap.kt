package pl.pogoda.mazowsze.ui

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import pl.pogoda.mazowsze.data.Place
import pl.pogoda.mazowsze.data.RadarData
import pl.pogoda.mazowsze.data.RadarFrame

private const val radarSourcePrefix = "aura-radar-source-"
private const val radarLayerPrefix = "aura-radar-layer-"
private const val locationSourceId = "aura-location-source"
private const val locationLayerId = "aura-location-layer"
private const val radarOpacity = 0.83f
private const val preloadOpacity = 0.01f
private const val fadeMillis = 300L

@Composable
fun RadarMap(
    radar: RadarData?, frames: List<RadarFrame>, selected: Int, place: Place?, recenterKey: Int,
    modifier: Modifier = Modifier, onFrameShown: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var shownTime by remember { mutableStateOf<Long?>(null) }
    var loadedHost by remember { mutableStateOf<String?>(null) }
    var preparedTime by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(mapView, lifecycleOwner) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    destroyed = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                when (lifecycleOwner.lifecycle.currentState) {
                    Lifecycle.State.RESUMED -> {
                        mapView.onPause()
                        mapView.onStop()
                    }
                    Lifecycle.State.STARTED -> mapView.onStop()
                    else -> Unit
                }
                mapView.onDestroy()
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxSize())

    LaunchedEffect(mapView) {
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setMaxZoomPreference(9.0)
            readyMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(place?.latitude ?: 52.2297, place?.longitude ?: 21.0122))
                .zoom(7.2)
                .build()
            readyMap.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                style.layers.forEach { layer ->
                    when (layer) {
                        is BackgroundLayer -> layer.setProperties(backgroundColor("#071525"))
                        is FillLayer -> layer.setProperties(fillColor(if (layer.id.contains("water")) "#102D4C" else "#0B2038"))
                        is LineLayer -> layer.setProperties(lineColor(if (layer.id.contains("water")) "#275778" else "#344D69"))
                        is SymbolLayer -> layer.setProperties(textColor("#D5E6F1"), textHaloColor("#071525"))
                    }
                }
                readyMap.uiSettings.isLogoEnabled = false
                // Attribution stays visible in the custom bottom panel.
                readyMap.uiSettings.isAttributionEnabled = false
                styleReady = true
            }
        }
    }

    LaunchedEffect(map, styleReady, place, recenterKey) {
        if (!styleReady) return@LaunchedEffect
        val target = place ?: Place(52.2297, 21.0122, "Warszawa")
        map?.getStyle { style ->
            style.getLayer(locationLayerId)?.let { style.removeLayer(it) }
            style.getSource(locationSourceId)?.let { style.removeSource(it) }
            if (place != null) {
                val point = Feature.fromGeometry(Point.fromLngLat(target.longitude, target.latitude))
                style.addSource(GeoJsonSource(locationSourceId, point))
                style.addLayer(
                    CircleLayer(locationLayerId, locationSourceId).withProperties(
                        circleColor(android.graphics.Color.parseColor("#329BFF")),
                        circleRadius(8f),
                        circleStrokeColor(android.graphics.Color.WHITE),
                        circleStrokeWidth(3f)
                    )
                )
            }
        }
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), 7.2))
    }

    LaunchedEffect(map, styleReady, radar?.host, frames, selected) {
        val readyMap = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val style = readyMap.style ?: return@LaunchedEffect
        val frame = frames.getOrNull(selected)
        if (radar == null || frame == null) {
            clearRadarLayers(style)
            shownTime = null
            loadedHost = null
            preparedTime = null
            return@LaunchedEffect
        }
        if (loadedHost != radar.host) {
            clearRadarLayers(style)
            shownTime = null
            preparedTime = null
            loadedHost = radar.host
        }

        val activeTime = shownTime?.takeIf { style.getLayer(radarLayerId(it)) != null }
        val targetLayerId = radarLayerId(frame.timeSeconds)
        // Keep the visible image while the requested frame loads. Remove only stale preload layers.
        style.layers.filter { it.id.startsWith(radarLayerPrefix) &&
            it.id != targetLayerId && it.id != activeTime?.let(::radarLayerId) }
            .forEach { removeRadarFrame(style, it.id.removePrefix(radarLayerPrefix).toLong()) }
        activeTime?.let { time ->
            (style.getLayer(radarLayerId(time)) as? RasterLayer)?.apply {
                setRasterOpacityTransition(TransitionOptions(0L, 0L))
                setProperties(rasterOpacity(radarOpacity))
            }
        }

        if (activeTime == frame.timeSeconds) {
            onFrameShown(selected)
        } else {
            val alreadyPrepared = preparedTime == frame.timeSeconds && style.getLayer(targetLayerId) != null
            ensureRadarFrame(style, radar.host, frame, preloadOpacity)
            // Keep playback moving even when MapLibre never reports a fully rendered map.
            if (!alreadyPrepared) delay(if (activeTime == null) 450L else 180L)
            val incoming = style.getLayer(targetLayerId) as? RasterLayer ?: return@LaunchedEffect
            incoming.setRasterOpacityTransition(TransitionOptions(fadeMillis, 0L))
            incoming.setProperties(rasterOpacity(radarOpacity))
            activeTime?.let { time ->
                (style.getLayer(radarLayerId(time)) as? RasterLayer)?.apply {
                    setRasterOpacityTransition(TransitionOptions(fadeMillis, 0L))
                    setProperties(rasterOpacity(0f))
                }
            }
            delay(fadeMillis)
            activeTime?.let { removeRadarFrame(style, it) }
            shownTime = frame.timeSeconds
            onFrameShown(selected)
        }

        frames.getOrNull((selected + 1) % frames.size)?.let { next ->
            if (next.timeSeconds != frame.timeSeconds) {
                ensureRadarFrame(style, radar.host, next, preloadOpacity)
                preparedTime = next.timeSeconds
            }
        }
    }
}

private fun radarLayerId(time: Long) = "$radarLayerPrefix$time"
private fun radarSourceId(time: Long) = "$radarSourcePrefix$time"

private fun ensureRadarFrame(style: Style, host: String, frame: RadarFrame, opacity: Float) {
    if (style.getLayer(radarLayerId(frame.timeSeconds)) != null) return
    // RainViewer's free API serves historical frames through zoom level 7.
    val template = host.trimEnd('/') + frame.path + "/256/{z}/{x}/{y}/2/1_1.png"
    val tileSet = TileSet("2.2.0", template).apply {
        setMaxZoom(7f)
        attribution = "RainViewer"
    }
    style.addSource(RasterSource(radarSourceId(frame.timeSeconds), tileSet, 256))
    val layer = RasterLayer(radarLayerId(frame.timeSeconds), radarSourceId(frame.timeSeconds))
        .withProperties(rasterOpacity(opacity))
    if (style.getLayer(locationLayerId) != null) style.addLayerBelow(layer, locationLayerId)
    else style.addLayer(layer)
}

private fun removeRadarFrame(style: Style, time: Long) {
    style.getLayer(radarLayerId(time))?.let { style.removeLayer(it) }
    style.getSource(radarSourceId(time))?.let { style.removeSource(it) }
}

private fun clearRadarLayers(style: Style) {
    style.layers.filter { it.id.startsWith(radarLayerPrefix) }
        .forEach { removeRadarFrame(style, it.id.removePrefix(radarLayerPrefix).toLong()) }
}

