package pl.pogoda.mazowsze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.pogoda.mazowsze.WeatherViewModel
import kotlin.math.roundToInt

@Composable
fun RadarScreen(model: WeatherViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val data = model.radar
    var lastHour by remember { mutableStateOf(false) }
    val frames = if (lastHour) data?.frames.orEmpty().takeLast(7) else data?.frames.orEmpty()
    val uri = LocalUriHandler.current
    var selected by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var recenter by remember { mutableIntStateOf(0) }
    val indicated = if (playing && shown in frames.indices) shown else selected

    LaunchedEffect(data?.fetchedAt, lastHour) {
        selected = frames.lastIndex.coerceAtLeast(0)
        shown = -1
        playing = false
    }
    LaunchedEffect(Unit) { model.refreshRadar(); while (true) { delay(5 * 60 * 1000L); model.refreshRadar() } }
    LaunchedEffect(playing, shown, frames.size) {
        if (playing && shown in frames.indices && frames.size > 1) {
            delay(400)
            if (selected == shown) selected = (shown + 1) % frames.size
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        RadarMap(data, frames, selected, model.place, recenter, Modifier.fillMaxSize()) { shown = it }
        Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color(0xD0061425), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(410.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB9051222)))))
        Column(Modifier.statusBarsPadding().padding(start = 12.dp, end = 20.dp, top = 5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconControl(Glyph.BACK, "Wróć do pogody", onBack)
                Column(Modifier.weight(1f).padding(start = 3.dp)) {
                    Text("Radar opadów", fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("Obserwacje · ${model.place?.label ?: "Mazowsze"}", fontSize = 11.sp, color = nightPalette.muted)
                }
                GlassPanel(radius = 18.dp, dark = true, frosted = false) {
                    IconControl(Glyph.LOCATE, "Wyśrodkuj mapę na wybranej lokalizacji", { recenter++ })
                }
            }
            Spacer(Modifier.height(11.dp))
            GlassPanel(radius = 24.dp, dark = true, frosted = false) {
                Row(Modifier.clickable { lastHour = !lastHour }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Glyph.CLOCK, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (lastHour) "Ostatnia godzina" else "Ostatnie 2 godziny", fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    LineIcon(Glyph.DOWN, Modifier.size(15.dp))
                }
            }
        }
        if (model.radarLoading && frames.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        GlassPanel(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 99.dp)
                .fillMaxWidth().heightIn(max = maxHeight * .40f), radius = 28.dp, dark = true, frosted = false
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(frames.getOrNull(indicated)?.let { radarTime(it.timeSeconds) } ?: "Radar", fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
                        Text("Obraz radarowy", fontSize = 11.sp, color = nightPalette.muted)
                    }
                    FilledTonalIconButton(
                        onClick = { playing = !playing }, enabled = frames.size > 1 && shown in frames.indices,
                        modifier = Modifier.size(44.dp).semantics { contentDescription = if (playing) "Wstrzymaj radar" else "Odtwórz radar" },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x664C8BA8))
                    ) { LineIcon(if (playing) Glyph.PAUSE else Glyph.PLAY, Modifier.size(20.dp)) }
                }
                if (frames.isNotEmpty()) {
                    RadarTimeline(indicated, frames.lastIndex.coerceAtLeast(1)) { playing = false; selected = it.coerceIn(frames.indices) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(radarTime(frames.first().timeSeconds), fontSize = 10.sp)
                        Text(radarTime(frames[frames.size / 2].timeSeconds), fontSize = 10.sp)
                        Text(radarTime(frames.last().timeSeconds), fontSize = 10.sp)
                    }
                } else {
                    Text(model.radarError ?: "Pobieram obrazy radaru…", fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).background(Brush.horizontalGradient(listOf(
                    Color(0xFF6CD1EB), Color(0xFF00A3E0), Color(0xFF005588), Color(0xFFFFE000), Color(0xFFFF4400)
                ))))
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Słabszy opad", fontSize = 9.sp)
                    Text("Silniejszy", fontSize = 9.sp)
                }
                if (model.radarError != null && frames.isNotEmpty()) Text("Nie udało się odświeżyć klatek.", fontSize = 9.sp)
                frames.lastOrNull()?.let { if (System.currentTimeMillis() - it.timeSeconds * 1000L > 30 * 60 * 1000L) Text("Ostatni obraz ma ponad 30 minut.", fontSize = 9.sp) }
                Text("Kolorowe pola oznaczają opad. Pusta mapa może oznaczać brak opadu lub zasięgu radaru.", fontSize = 9.sp, lineHeight = 13.sp, color = nightPalette.muted, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RainViewer ↗", fontSize = 9.sp, modifier = Modifier.clickable { uri.openUri("https://www.rainviewer.com/") }.padding(vertical = 6.dp))
                    Text("© OpenStreetMap · OpenFreeMap", fontSize = 9.sp, modifier = Modifier.clickable { uri.openUri("https://www.openstreetmap.org/copyright") }.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun RadarTimeline(selected: Int, lastIndex: Int, onSelect: (Int) -> Unit) {
    Canvas(Modifier.fillMaxWidth().height(29.dp)
        .pointerInput(lastIndex) { detectTapGestures { onSelect(((it.x / size.width) * lastIndex).roundToInt()) } }
        .pointerInput(lastIndex) {
            detectDragGestures(onDragStart = { onSelect(((it.x / size.width) * lastIndex).roundToInt()) },
                onDrag = { change, _ -> onSelect(((change.position.x / size.width) * lastIndex).roundToInt()); change.consume() })
        }
        .semantics { contentDescription = "Czas obrazu radarowego"; progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..lastIndex.toFloat()) }
    ) {
        val y = size.height / 2
        val start = 6.dp.toPx()
        val end = size.width - start
        val x = start + (end - start) * selected / lastIndex
        drawLine(Color(0xFF82999C), Offset(start, y), Offset(end, y), 2.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFFB1E1FF), Offset(start, y), Offset(x, y), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White, 6.dp.toPx(), Offset(x, y))
    }
}
