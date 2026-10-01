package pl.pogoda.mazowsze.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import pl.pogoda.mazowsze.WeatherViewModel
import pl.pogoda.mazowsze.data.*
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AuraApp(model: WeatherViewModel, locationMessage: String?, onLocate: () -> Unit, onLocationSettings: () -> Unit,
    openUpdates: Boolean = false) {
    var selected by rememberSaveable { mutableIntStateOf(if (openUpdates) 3 else 0) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var clock by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(model.skyBackgroundMode) {
        if (model.skyBackgroundMode == SkyBackgroundMode.AUTOMATIC) {
            while (true) {
                clock = Instant.now()
                delay(60_000L)
            }
        }
    }
    val scene = skyScene(model.skyBackgroundMode, model.weather, clock)
    val imageRes = skySceneResource(scene)
    val dim by animateFloatAsState(
        targetValue = (skySceneDim(scene) + if (model.duskMode) .15f else 0f).coerceAtMost(.55f),
        animationSpec = tween(900), label = "Przyciemnienie nieba"
    )
    val p = nightPalette
    BackHandler(selected != 0) { selected=0 }
    LaunchedEffect(selected) {
        if(selected<2 && model.place!=null && !model.weatherLoading && System.currentTimeMillis()-(model.weather?.fetchedAt?:0)>30*60*1000L) model.refreshWeather()
    }
    CompositionLocalProvider(LocalAuraPalette provides p) {
        MaterialTheme(colorScheme=darkColorScheme(primary=p.accent,onPrimary=p.background,background=p.background,onBackground=p.ink,surface=p.background,onSurface=p.ink,outline=p.line),typography=auraTypography) {
            Box(Modifier.fillMaxSize().onSizeChanged { rootSize=it }) {
                SkyBackground(imageRes, dim)
                CompositionLocalProvider(LocalContentColor provides p.ink, LocalSkyBackdrop provides if(selected==2)null else SkyBackdrop(rootSize,dim,imageRes)) {
                    if(selected==2) RadarScreen(model,onBack={selected=0})
                    else Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        AppHeader(selected, model.place?.label?:"Wybierz miejsce", {selected=3}, {selected=0})
                        val contentModifier=Modifier.weight(1f).navigationBarsPadding().padding(bottom=if(selected==3)0.dp else 92.dp)
                        when(selected) {
                            0 -> TodayScreen(model,locationMessage,onLocate,{selected=1},contentModifier)
                            1 -> ForecastScreen(model,locationMessage,onLocate,contentModifier)
                            else -> SettingsScreen(model,locationMessage,onLocate,onLocationSettings,{selected=0},scene,contentModifier,
                                focusUpdates=openUpdates)
                        }
                    }
                    if (selected != 3) BottomNavigation(selected,{selected=it},Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
internal fun IconControl(icon: Glyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick=onClick,modifier=modifier.semantics { contentDescription=label }) { LineIcon(icon) }
}

@Composable
private fun AppHeader(selected: Int, location: String, onSettings: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=8.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
        if(selected==0) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick=onSettings).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                LineIcon(Glyph.PIN,Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text(location,fontSize=19.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
                Spacer(Modifier.width(8.dp));LineIcon(Glyph.DOWN,Modifier.size(17.dp))
            }
            IconControl(Glyph.MENU,"Miejsca i ustawienia",onSettings)
        } else {
            IconControl(Glyph.BACK,"Wróć do pogody",onBack)
            Column(Modifier.weight(1f).padding(start=4.dp)) {
                Text(if(selected==1)"Prognoza" else "Twoja pogoda",fontWeight=FontWeight.SemiBold,fontSize=20.sp)
                Text(if(selected==1)location else "Miejsca i ustawienia",color=nightPalette.muted,fontSize=12.sp)
            }
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun BottomNavigation(selected: Int, onSelect: (Int)->Unit, modifier: Modifier) {
    GlassPanel(modifier.navigationBarsPadding().padding(horizontal=20.dp,vertical=10.dp).fillMaxWidth(),radius=38.dp,nav=true) {
        Row(Modifier.padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("Teraz" to Glyph.HOME,"Prognoza" to Glyph.CHART,"Radar" to Glyph.MAP).forEachIndexed { index,(label,icon) ->
                val active=selected==index
                Column(Modifier.weight(1f).clip(RoundedCornerShape(32.dp)).background(if(active)Color(0x557E9DA6) else Color.Transparent)
                    .clickable { onSelect(index) }.semantics { this.selected=active; role=Role.Tab }
                    .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    LineIcon(icon,Modifier.size(22.dp),filled=active);Spacer(Modifier.height(3.dp))
                    Text(label,fontSize=11.sp,lineHeight=16.sp,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

internal fun temperature(value: Double, model: WeatherViewModel, withUnit: Boolean=false): String =
    if(model.useFahrenheit) ((value*9/5)+32).roundToInt().toString()+"°"+if(withUnit)"F" else ""
    else value.roundToInt().toString()+"°"+if(withUnit)"C" else ""
internal fun wind(value: Double, model: WeatherViewModel): String =
    if(model.useMiles)(value*.621371).roundToInt().toString()+" mph" else value.roundToInt().toString()+" km/h"
internal fun upcoming(data: WeatherData)=data.hours.filter {it.time.take(13)>=data.current.time.take(13)}
internal fun shortDay(date:String, timezone:String):String = if(date==LocalDate.now(forecastZone(timezone)).toString())"Dziś" else runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE",Locale.forLanguageTag("pl"))).replaceFirstChar { it.uppercase() }
}.getOrDefault(date)

@Composable
internal fun WeatherRequired(locationMessage: String?, onLocate: ()->Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            WeatherGlyph(2,size=76.dp)
            Text("Pogoda wokół Ciebie",fontSize=24.sp,fontWeight=FontWeight.SemiBold)
            Text("Wybierz miejscowość w menu lub użyj lokalizacji telefonu.",fontSize=14.sp)
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xD414343A))
                .clickable(onClick=onLocate).padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                LineIcon(Glyph.LOCATE,Modifier.size(19.dp));Spacer(Modifier.width(10.dp))
                Text("Użyj lokalizacji telefonu",fontSize=13.sp)
            }
            locationMessage?.let {Text(it,fontSize=12.sp)}
        }
    }
}

@Composable
internal fun DataNotice(model: WeatherViewModel) {
    if(model.weatherError!=null) GlassPanel(Modifier.fillMaxWidth(),radius=18.dp,dark=true) {
        Column(Modifier.padding(16.dp)) {
            Text(model.weatherError!!,fontSize=12.sp)
            TextButton(onClick=model::refreshWeather){Text("Spróbuj ponownie")}
        }
    }
    if(model.weather==null && model.weatherLoading) Box(Modifier.fillMaxWidth().height(200.dp),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
}

@Composable
private fun TodayScreen(model: WeatherViewModel, locationMessage: String?, onLocate: ()->Unit, onDetails: ()->Unit, modifier: Modifier) {
    var days by rememberSaveable {mutableStateOf(true)}
    val data=model.weather
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=4.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text(LocalDate.now(data?.let { forecastZone(it.timezone) } ?: ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.forLanguageTag("pl"))).replaceFirstChar{it.uppercase()},
                modifier=Modifier.fillMaxWidth().padding(bottom=1.dp),textAlign=TextAlign.Center,fontSize=14.sp,color=nightPalette.muted)
        }
        if(model.place==null) {item {WeatherRequired(locationMessage,onLocate)};return@LazyColumn}
        if(model.weatherError!=null || data==null) item {DataNotice(model)}
        if(data!=null) {
            item {CurrentHero(data,model)}
            item {SegmentedSwitch(listOf("Godziny","Dni"),if(days)1 else 0,{days=it==1},Modifier.padding(horizontal=52.dp))}
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    if(days) data.days.take(5).forEach {day ->
                        ForecastCapsule(shortDay(day.date,data.timezone),day.code,temperature(day.maximum,model),temperature(day.minimum,model),day.date==data.days.first().date,modifier=Modifier.weight(1f))
                    } else upcoming(data).take(5).forEach {h ->
                        ForecastCapsule(hour(h.time),h.code,temperature(h.temperature,model),"${h.precipitationProbability}%",false,h.isDay,Modifier.weight(1f))
                    }
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick=onDetails),radius=24.dp,nav=true) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("Szczegóły pogody",fontSize=14.sp,modifier=Modifier.weight(1f));LineIcon(Glyph.NEXT,Modifier.size(20.dp))
                    }
                }
            }
            item {SourceNote(data.fetchedAt,data.timezone,model::refreshWeather)}
        }
    }
}

@Composable
private fun CurrentHero(data:WeatherData,model:WeatherViewModel) {
    val current=data.current
    val today=LocalDate.now(forecastZone(data.timezone))
    val day=data.days.firstOrNull{it.date==today.toString()}
        ?:data.days.firstOrNull{it.date==current.time.take(10)}?:data.days.firstOrNull()
    val nextDay=day?.let { selected -> data.days.firstOrNull { it.date==LocalDate.parse(selected.date).plusDays(1).toString() } }
    GlassPanel(Modifier.fillMaxWidth(),radius=32.dp) {
        Column(Modifier.padding(horizontal=18.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                WeatherGlyph(current.code,current.isDay,100.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(temperature(current.temperature,model,true),fontSize=51.sp,lineHeight=59.sp,letterSpacing=(-2).sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Text(condition(current.code),fontSize=12.sp,lineHeight=18.sp)
                    Text("Odczuwalna ${temperature(current.apparentTemperature,model)}",fontSize=11.sp,color=nightPalette.muted)
                    day?.let { Text("Dziś: maks. ${temperature(it.maximum,model)} · min. ${temperature(it.minimum,model)}",
                        fontSize=11.sp,color=nightPalette.ink,maxLines=1,overflow=TextOverflow.Ellipsis) }
                }
            }
            day?.let { DaylightSummary(it,nextDay,data.timezone) }
            PrecipitationSummary(upcoming(data).take(6))
            Row(Modifier.fillMaxWidth().padding(top=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Metric(Glyph.DROP,"Wilgotność","${current.humidity}%")
                Metric(Glyph.WIND,"Wiatr",wind(current.windSpeed,model))
            }
        }
    }
}

@Composable
private fun DaylightSummary(day:DayWeather,nextDay:DayWeather?,timezone:String) {
    val zone=remember(timezone) { forecastZone(timezone) }
    val sunrise=remember(day.sunrise) { runCatching { LocalDateTime.parse(day.sunrise) }.getOrNull() }
    val sunset=remember(day.sunset) { runCatching { LocalDateTime.parse(day.sunset) }.getOrNull() }
    var now by remember { mutableStateOf(LocalDateTime.now(zone)) }
    LaunchedEffect(day.date, timezone) {
        while(true) {
            now=LocalDateTime.now(zone)
            kotlinx.coroutines.delay(60_000)
        }
    }
    val minutes=if(sunrise!=null && sunset!=null) Duration.between(sunrise,sunset).toMinutes().coerceAtLeast(0) else 0
    val duration="${minutes/60} h ${minutes%60} min"
    val headline=when {
        sunset!=null && now>=sunset && nextDay!=null -> "Jutro wschód ${hour(nextDay.sunrise)}"
        else -> "$duration światła"
    }
    val progress=if(sunrise!=null && sunset!=null && minutes>0)
        (Duration.between(sunrise,now).toMinutes().toFloat()/minutes).coerceIn(0f,1f) else 0f
    val sunVisible=sunrise!=null && sunset!=null && now>=sunrise && now<sunset
    val description="Światło dnia. Wschód ${hour(day.sunrise)}, zachód ${hour(day.sunset)}. Długość dnia $duration."+
        if(sunset!=null && now>=sunset && nextDay!=null) " Jutro wschód ${hour(nextDay.sunrise)}." else ""

    Column(
        Modifier.fillMaxWidth()
            .clearAndSetSemantics { contentDescription=description }
            .padding(horizontal=6.dp,vertical=5.dp)
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("ŚWIATŁO DNIA",fontSize=10.sp,letterSpacing=1.sp,color=nightPalette.muted)
            Text(headline,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val inset=15.dp.toPx()
            val top=10.dp.toPx()
            val radiusY=34.dp.toPx()
            val ovalSize=Size(size.width-2*inset,radiusY*2)
            val ovalStart=Offset(inset,top)
            drawArc(Color(0x779FC4CA),180f,180f,false,ovalStart,ovalSize,
                style=Stroke(width=1.2.dp.toPx(),cap=StrokeCap.Round))
            if(progress>0f) drawArc(Color(0xFFDDEFF1),180f,180f*progress,false,ovalStart,ovalSize,
                style=Stroke(width=1.6.dp.toPx(),cap=StrokeCap.Round))
            if(sunrise!=null && sunset!=null) {
                val angle=PI*(1.0+progress)
                val center=Offset(size.width/2+(size.width/2-inset)*cos(angle).toFloat(),top+radiusY+radiusY*sin(angle).toFloat())
                val sunColor=if(sunVisible) Color(0xFFF5FCFC) else Color(0xAFC5DFE2)
                drawCircle(sunColor,3.6.dp.toPx(),center,style=Stroke(width=1.3.dp.toPx()))
                for(ray in 0 until 8) {
                    val direction=ray*PI/4
                    val inner=6.2.dp.toPx()
                    val outer=9.dp.toPx()
                    drawLine(sunColor,
                        Offset(center.x+inner*cos(direction).toFloat(),center.y+inner*sin(direction).toFloat()),
                        Offset(center.x+outer*cos(direction).toFloat(),center.y+outer*sin(direction).toFloat()),
                        strokeWidth=1.2.dp.toPx(),cap=StrokeCap.Round)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Column {
                Text("WSCHÓD",fontSize=9.sp,letterSpacing=.6.sp,color=nightPalette.muted)
                Text(hour(day.sunrise),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            }
            Column(horizontalAlignment=Alignment.End) {
                Text("ZACHÓD",fontSize=9.sp,letterSpacing=.6.sp,color=nightPalette.muted)
                Text(hour(day.sunset),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PrecipitationSummary(hours:List<HourWeather>) {
    val peak=hours.maxWithOrNull(compareBy<HourWeather>{it.precipitationProbability}.thenBy{it.precipitation})
    val firstPotential=hours.firstOrNull{it.precipitationProbability>=30 || it.precipitation>=.1}?:peak
    val headline=when {
        peak==null -> "Prognoza godzinowa niedostępna"
        peak.precipitationProbability==0 -> "0% szansy opadów w najbliższych 6 h"
        peak.precipitationProbability<=20 && hours.all{it.precipitation<.1} -> "Mała szansa opadów przez 6 h"
        else -> "Możliwy opad około ${hour((firstPotential?:peak).time)}"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=3.dp)) {
        HorizontalDivider(color=nightPalette.line)
        Spacer(Modifier.height(9.dp))
        Text("OPADY",fontSize=10.sp,letterSpacing=.6.sp,color=nightPalette.muted)
        Text(headline,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
        peak?.takeIf { it.precipitationProbability>0 }?.let {
            Text("Najwyższa szansa w ciągu 6 h: ${it.precipitationProbability}%",fontSize=11.sp,color=nightPalette.muted)
        }
    }
}

@Composable
internal fun Metric(icon:Glyph,label:String,value:String,modifier:Modifier=Modifier) {
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        LineIcon(icon,Modifier.size(28.dp));Spacer(Modifier.width(12.dp))
        Column {Text(label,fontSize=11.sp,color=nightPalette.muted);Text(value,fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
    }
}

@Composable
internal fun SegmentedSwitch(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    GlassPanel(modifier.fillMaxWidth(),radius=30.dp,nav=true) {
        Row(Modifier.padding(3.dp)) {
            labels.forEachIndexed {index,label->
                Text(label,fontSize=12.sp,fontWeight=if(index==selected)FontWeight.SemiBold else FontWeight.Normal,textAlign=TextAlign.Center,
                    modifier=Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if(index==selected)Color(0x668FA5AB) else Color.Transparent)
                        .clickable{onSelect(index)}.semantics{this.selected=index==selected;role=Role.Tab}.padding(vertical=9.dp))
            }
        }
    }
}

@Composable
private fun ForecastCapsule(label:String,code:Int,high:String,low:String,selected:Boolean,isDay:Boolean=true,modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(30.dp)).background(if(selected)Color(0x88796568) else Color(0x68435F64)).padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label,fontSize=12.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
        Spacer(Modifier.height(8.dp));WeatherGlyph(code,isDay=isDay,size=43.dp);Spacer(Modifier.height(7.dp))
        Text(high,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text(low,fontSize=12.sp,color=nightPalette.muted)
    }
}

@Composable
internal fun SourceNote(fetchedAt:Long,timezone:String,onRefresh:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("Open-Meteo · ${updateTime(fetchedAt,timezone)}",color=nightPalette.muted,fontSize=10.sp,modifier=Modifier.weight(1f))
        Text("Odśwież",color=nightPalette.ink,fontSize=11.sp,modifier=Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick=onRefresh).padding(horizontal=10.dp,vertical=9.dp))
    }
}

