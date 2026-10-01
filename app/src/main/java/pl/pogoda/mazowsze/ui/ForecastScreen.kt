package pl.pogoda.mazowsze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import pl.pogoda.mazowsze.WeatherViewModel
import pl.pogoda.mazowsze.data.*
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun ForecastScreen(model:WeatherViewModel,locationMessage:String?,onLocate:()->Unit,modifier:Modifier) {
    var mode by rememberSaveable {mutableIntStateOf(0)}
    var selected by rememberSaveable(model.place?.label) {mutableIntStateOf(0)}
    val chartScroll=rememberScrollState()
    val data=model.weather
    val hours=data?.let{upcoming(it).take(24)}.orEmpty()
    val index=selected.coerceIn(0,hours.lastIndex.coerceAtLeast(0))
    val current=hours.getOrNull(index)
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {SegmentedSwitch(listOf("24 godziny","10 dni"),mode,{mode=it},Modifier.padding(horizontal=22.dp))}
        if(model.place==null) {item{WeatherRequired(locationMessage,onLocate)};return@LazyColumn}
        if(model.weatherError!=null || data==null) item {DataNotice(model)}
        if(data!=null) {
            if(mode==0 && current!=null) {
                item {GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal=16.dp,vertical=16.dp)) {
                        ChartHeading(Glyph.TEMP,"Temperatura · ${hour(current.time)}")
                        Row(Modifier.fillMaxWidth().padding(top=7.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
                            Column {
                                Text(temperature(current.temperature,model),fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.SemiBold)
                                Text("Temperatura",fontSize=10.sp,color=nightPalette.muted)
                            }
                            Column(horizontalAlignment=Alignment.End) {
                                Text(current.apparentTemperature?.let { temperature(it,model) } ?: "—",fontSize=22.sp,lineHeight=28.sp,color=Color(0xFFA4D9D0),fontWeight=FontWeight.SemiBold)
                                Text("Odczuwalna",fontSize=10.sp,color=nightPalette.muted)
                            }
                        }
                        ForecastChart(hours,current.time,model,chartScroll) {chosen->selected=hours.indexOfFirst{it.time==chosen.time}.coerceAtLeast(0)}
                        if(hours.size>1) {
                            Text("Przesuwaj wykres w lewo i prawo · 24 godziny",fontSize=10.sp,
                                color=nightPalette.muted,modifier=Modifier.padding(top=9.dp))
                        }
                    }
                }}
            } else if(mode==1) {
                items(data.days) {day->DailyForecast(day,data.hours,data.timezone,model)}
            }
            item {SourceNote(data.fetchedAt,data.timezone,model::refreshWeather)}
        }
    }
}

@Composable
private fun ChartHeading(icon:Glyph,title:String) {
    Row(verticalAlignment=Alignment.CenterVertically) {LineIcon(icon,Modifier.size(22.dp));Spacer(Modifier.width(10.dp));Text(title,fontSize=14.sp,fontWeight=FontWeight.SemiBold)}
}

@Composable
private fun ForecastChart(hours:List<HourWeather>,selectedTime:String,model:WeatherViewModel,
    scrollState:androidx.compose.foundation.ScrollState,onSelect:(HourWeather)->Unit) {
    if(hours.isEmpty())return
    val selectedUv=hours.firstOrNull { it.time==selectedTime }?.uvIndex
    val values=hours.flatMap { listOfNotNull(it.temperature,it.apparentTemperature) }
    val temperatureLow=values.minOrNull()!!
    val temperatureHigh=values.maxOrNull()!!
    val temperaturePadding=maxOf(1.0,(temperatureHigh-temperatureLow)*.1)
    val min=temperatureLow-temperaturePadding
    val max=temperatureHigh+temperaturePadding
    val lineHeight=165.dp
    val gustValues=hours.mapNotNull { it.windGust }
    val gustLow=gustValues.minOrNull() ?: 0.0
    val gustHigh=gustValues.maxOrNull() ?: 1.0
    val gustPadding=maxOf(2.0,(gustHigh-gustLow)*.12)
    val gustMin=gustLow-gustPadding
    val gustMax=gustHigh+gustPadding
    val gustUnit=if(model.useMiles) "mph" else "km/h"
    fun temperatureY(value:Double)=16f+((max-value)/(max-min)).toFloat()*74f
    fun gustY(value:Double)=105f+((gustMax-value)/(gustMax-gustMin)).toFloat()*40f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemWidth=maxWidth/5
        val itemWidthPx=with(LocalDensity.current){itemWidth.toPx()}
        LaunchedEffect(scrollState,hours,itemWidthPx) {
            snapshotFlow { scrollState.value }
                .map { (it/itemWidthPx).toInt().coerceIn(hours.indices) }
                .distinctUntilChanged()
                .collect { onSelect(hours[it]) }
        }
        Column(Modifier.fillMaxWidth().padding(top=12.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(15.dp)) {
                    ChartLegend(Color.White,"Temperatura")
                    ChartLegend(Color(0xFFA4D9D0),"Odczuwalna")
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(15.dp)) {
                    ChartLegend(Color(0xFF78AEB0),"Opad %")
                    ChartLegend(Color(0xFFD4B7E8),"Porywy ($gustUnit)")
                }
                Text("Porywy wiatru mają osobną skalę",fontSize=9.sp,color=nightPalette.muted)
                Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        LineIcon(Glyph.SUN,Modifier.size(17.dp),color=uvColor(selectedUv))
                        Spacer(Modifier.width(6.dp))
                        Text("UV o ${hour(selectedTime)} · ${uvText(selectedUv)}",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=uvColor(selectedUv))
                    }
                }
            }
        Column(Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(top=8.dp)) {
        Box(Modifier.width(itemWidth*hours.size).height(lineHeight)) {
            Canvas(Modifier.fillMaxSize().pointerInput(hours) {
                detectTapGestures{point->
                    val i=((point.x/size.width)*hours.size).toInt().coerceIn(hours.indices)
                    onSelect(hours[i])
                }
            }.semantics{contentDescription="Wykres temperatury, temperatury odczuwalnej, szansy opadu i porywów wiatru. Przesuwaj w obie strony i dotknij, aby wybrać godzinę."}) {
                fun point(i:Int,value:Double)=Offset(size.width*(i+.5f)/hours.size,
                    temperatureY(value).dp.toPx())
                fun gustPoint(i:Int,value:Double)=Offset(size.width*(i+.5f)/hours.size,
                    gustY(value).dp.toPx())
                val points=hours.mapIndexed {i,h->point(i,h.temperature)}
                val bottom=size.height-1
                val fillBottom=160.dp.toPx()
                points.forEachIndexed {i,point->
                    drawLine(Color(0x22FFFFFF),Offset(point.x,0f),Offset(point.x,bottom),1f)
                    if(hours[i].time==selectedTime) drawLine(Color(0xAAFFFFFF),Offset(point.x,12f),Offset(point.x,bottom),1.5f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4f,5f)))
                }
                drawLine(Color(0x55FFFFFF),Offset(0f,bottom),Offset(size.width,bottom),1f)
                val path=smoothChartPath(points)
                val fill=Path().apply {addPath(path);lineTo(points.last().x,fillBottom);lineTo(points.first().x,fillBottom);close()}
                drawPath(fill,Brush.verticalGradient(listOf(Color(0x44FFFFFF),Color(0x12FFFFFF))))
                drawPath(path,Color.White,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                val apparentPath=smoothChartPath(hours.mapIndexed {i,h->h.apparentTemperature?.let { point(i,it) }})
                drawPath(apparentPath,Color(0xFFA4D9D0),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,
                    join=StrokeJoin.Round,pathEffect=PathEffect.dashPathEffect(floatArrayOf(10f,5f))))
                points.forEachIndexed {i,point->
                    if(hours[i].time==selectedTime) {drawCircle(Color.White,6.dp.toPx(),point);drawCircle(Color(0xFF9DDBFF),4.dp.toPx(),point)}
                    else drawCircle(Color.White,3.dp.toPx(),point)
                }
                hours.forEachIndexed {i,h->h.apparentTemperature?.let { apparent->
                    val position=point(i,apparent)
                    drawCircle(Color(0xFFA4D9D0),if(h.time==selectedTime) 4.dp.toPx() else 2.5.dp.toPx(),position)
                }}
                val gustPath=smoothChartPath(hours.mapIndexed {i,h->h.windGust?.let { gustPoint(i,it) }})
                drawPath(gustPath,Color(0xFFD4B7E8),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                hours.forEachIndexed {i,h->h.windGust?.let { gust->
                    drawCircle(Color(0xFFD4B7E8),if(h.time==selectedTime) 4.dp.toPx() else 2.5.dp.toPx(),gustPoint(i,gust))
                }}
            }
            Row(Modifier.fillMaxSize()) {
                hours.forEach {h->
                    val actualY=temperatureY(h.temperature)
                    val apparentY=h.apparentTemperature?.let(::temperatureY)
                    val actualLabelBase=(actualY-20f).coerceAtLeast(0f)
                    val apparentLabelBase=apparentY?.let { (it+5f).coerceAtMost(lineHeight.value-14f) }
                    val overlap=apparentLabelBase!=null && actualLabelBase<apparentLabelBase+14f && apparentLabelBase<actualLabelBase+14f
                    val actualLabelY=if(overlap && apparentY!=null) (minOf(actualY,apparentY)-20f).coerceAtLeast(0f) else actualLabelBase
                    val apparentLabelY=if(overlap && apparentY!=null) (maxOf(actualY,apparentY)+5f).coerceAtMost(lineHeight.value-14f) else apparentLabelBase
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Text(temperature(h.temperature,model),fontSize=10.sp,lineHeight=12.sp,
                            textAlign=TextAlign.Center,softWrap=false,
                            modifier=Modifier.fillMaxWidth().offset(y=actualLabelY.dp))
                        h.apparentTemperature?.let { apparent->
                            Text(temperature(apparent,model),fontSize=10.sp,lineHeight=12.sp,
                                color=Color(0xFFA4D9D0),textAlign=TextAlign.Center,softWrap=false,
                                modifier=Modifier.fillMaxWidth().offset(y=(apparentLabelY ?: 0f).dp))
                        }
                        h.windGust?.let { gust->
                            Text(wind(gust,model),fontSize=9.sp,lineHeight=11.sp,
                                color=Color(0xFFD4B7E8),textAlign=TextAlign.Center,softWrap=false,
                                modifier=Modifier.fillMaxWidth().offset(y=(gustY(gust)+5f).dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.width(itemWidth*hours.size).height(61.dp).padding(top=4.dp),verticalAlignment=Alignment.Bottom) {
            hours.forEach {h->
                Column(Modifier.width(itemWidth).clickable { onSelect(h) }
                    .semantics { contentDescription="${hour(h.time)}: temperatura ${temperature(h.temperature,model,true)}, odczuwalna ${h.apparentTemperature?.let { temperature(it,model,true) } ?: "brak danych"}, szansa opadu ${h.precipitationProbability}%, poryw wiatru ${h.windGust?.let { wind(it,model) } ?: "brak danych"}" },
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                    Text("${h.precipitationProbability}%",fontSize=10.sp,color=nightPalette.muted)
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.width(24.dp).height((h.precipitationProbability*.34f).coerceAtLeast(2f).dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFFA4D9D0),Color(0xFF5C9997))),RoundedCornerShape(topStart=4.dp,topEnd=4.dp)))
                }
            }
        }
        Row(Modifier.width(itemWidth*hours.size).padding(top=6.dp)) {
            hours.forEach { h ->
                Text(hour(h.time),fontSize=10.sp,textAlign=TextAlign.Center,color=nightPalette.muted,
                    modifier=Modifier.weight(1f).clickable { onSelect(h) }
                        .semantics { contentDescription="Godzina ${hour(h.time)}, indeks UV ${uvText(h.uvIndex)}" }
                        .padding(vertical=8.dp))
            }
        }
        }
        }
    }
}

private fun uvText(value:Double?):String = value?.let { String.format(Locale.forLanguageTag("pl"),"%.1f",it) } ?: "—"

private fun uvColor(value:Double?):Color = when {
    value==null -> Color(0xFF61777D)
    value<1 -> Color(0xFF6D9A9A)
    value<3 -> Color(0xFF91C6A3)
    value<6 -> Color(0xFFE1C86C)
    value<8 -> Color(0xFFE4A266)
    value<11 -> Color(0xFFDB7778)
    else -> Color(0xFFC391CF)
}

private fun smoothChartPath(points:List<Offset?>):Path=Path().apply {
    var previous:Offset?=null
    points.forEach { point->
        if(point==null) {
            previous=null
        } else {
            val last=previous
            if(last==null) moveTo(point.x,point.y)
            else {
                val middle=(last.x+point.x)/2f
                cubicTo(middle,last.y,middle,point.y,point.x,point.y)
            }
            previous=point
        }
    }
}

@Composable
private fun ChartLegend(color:Color,label:String) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(13.dp).height(3.dp).background(color,RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label,fontSize=10.sp,color=nightPalette.muted)
    }
}

@Composable
private fun GustExtreme(label:String,weather:HourWeather,model:WeatherViewModel,modifier:Modifier=Modifier) {
    Column(modifier) {
        Text(label,fontSize=10.sp,color=nightPalette.muted)
        Text(weather.windGust?.let{wind(it,model)}?:"—",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        Text("o ${hour(weather.time)}",fontSize=10.sp,color=nightPalette.muted)
    }
}

@Composable
private fun DailyForecast(day:DayWeather,hours:List<HourWeather>,timezone:String,model:WeatherViewModel) {
    val gustRange=gustExtremes(hours.filter { it.time.take(10)==day.date })
    GlassPanel(Modifier.fillMaxWidth(),radius=24.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${dayName(day.date,timezone)} · ${dayDate(day.date)}",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text(condition(day.code),fontSize=10.sp,color=nightPalette.muted)
                }
                WeatherGlyph(day.code,size=44.dp)
                Column(Modifier.padding(start=12.dp),horizontalAlignment=Alignment.End) {
                    Text("Maks. ${temperature(day.maximum,model)}",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Text("Min. ${temperature(day.minimum,model)}",fontSize=11.sp,color=nightPalette.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Opady ${day.precipitationProbability}% · ${millimeters(day.precipitation)}",fontSize=10.sp,color=nightPalette.aqua)
                Text("${hour(day.sunrise)} — ${hour(day.sunset)}",fontSize=10.sp,color=nightPalette.muted)
            }
            gustRange?.let { (lowest,highest) ->
                Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    GustExtreme("Porywy min.",lowest,model,Modifier.weight(1f))
                    GustExtreme("Porywy maks.",highest,model,Modifier.weight(1f))
                }
            }
        }
    }
}
