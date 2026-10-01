package pl.pogoda.mazowsze.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import pl.pogoda.mazowsze.R
import kotlin.math.*

internal val displayFont = FontFamily(Font(R.font.poppins_regular), Font(R.font.poppins_semibold, FontWeight.SemiBold))
internal val auraTypography = Typography().let { t -> Typography(
    displayLarge=t.displayLarge.copy(fontFamily=displayFont), displayMedium=t.displayMedium.copy(fontFamily=displayFont),
    displaySmall=t.displaySmall.copy(fontFamily=displayFont), headlineLarge=t.headlineLarge.copy(fontFamily=displayFont),
    headlineMedium=t.headlineMedium.copy(fontFamily=displayFont), headlineSmall=t.headlineSmall.copy(fontFamily=displayFont),
    titleLarge=t.titleLarge.copy(fontFamily=displayFont), titleMedium=t.titleMedium.copy(fontFamily=displayFont),
    titleSmall=t.titleSmall.copy(fontFamily=displayFont), bodyLarge=t.bodyLarge.copy(fontFamily=displayFont),
    bodyMedium=t.bodyMedium.copy(fontFamily=displayFont), bodySmall=t.bodySmall.copy(fontFamily=displayFont),
    labelLarge=t.labelLarge.copy(fontFamily=displayFont), labelMedium=t.labelMedium.copy(fontFamily=displayFont),
    labelSmall=t.labelSmall.copy(fontFamily=displayFont)) }

internal data class AuraPalette(
    val background: Color = Color(0xFF102A30), val surface: Color = Color(0xB32B454B),
    val raised: Color = Color(0x554F7076), val ink: Color = Color(0xFFFAFCFC),
    val muted: Color = Color(0xFFD2DEDF), val accent: Color = Color(0xFFC0E8F3),
    val aqua: Color = Color(0xFFACDCD1), val line: Color = Color(0x55FFFFFF)
)
internal val nightPalette = AuraPalette()
internal val LocalAuraPalette = staticCompositionLocalOf { nightPalette }
internal data class SkyBackdrop(val size: IntSize, val dim: Float, val imageRes: Int)
internal val LocalSkyBackdrop = staticCompositionLocalOf<SkyBackdrop?> { null }

@Composable
internal fun SkyBackground(imageRes: Int, dim: Float) {
    Crossfade(targetState = imageRes, animationSpec = tween(900), label = "Zmiana tła nieba") { resource ->
        Image(painterResource(resource), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF071D25).copy(alpha=dim)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x160A2630), Color.Transparent, Color(0x33051E25)))))
}

/** Only the backdrop is blurred; text and controls stay crisp. API 26–30 use the tinted fallback. */
@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier, radius: Dp = 28.dp, dark: Boolean = false, nav: Boolean = false,
    frosted: Boolean = true, content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalSkyBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(radius)
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }.clip(shape)) {
        if (frosted && backdrop != null && backdrop.size.width > 0) {
            val bitmap = ImageBitmap.imageResource(backdrop.imageRes)
            Canvas(Modifier.matchParentSize().blur(12.dp)) {
                val scale = max(backdrop.size.width.toFloat()/bitmap.width, backdrop.size.height.toFloat()/bitmap.height)
                val width = (bitmap.width*scale).roundToInt()
                val height = (bitmap.height*scale).roundToInt()
                drawImage(bitmap, dstOffset=IntOffset(((backdrop.size.width-width)/2f-origin.x).roundToInt(), ((backdrop.size.height-height)/2f-origin.y).roundToInt()), dstSize=IntSize(width,height))
                drawRect(Color(0xFF071D25).copy(alpha=backdrop.dim))
            }
        }
        val colors = when {
            dark -> listOf(Color(0xED0C292D), Color(0xE6123033))
            nav -> listOf(Color(0xB522464A), Color(0xA9173940))
            backdrop == null -> listOf(Color(0xEE35545B), Color(0xEC17353D))
            else -> listOf(Color(0x5C939C9E), Color(0x445E6F72))
        }
        Box(Modifier.matchParentSize().background(Brush.linearGradient(colors)).border(.6.dp, Brush.linearGradient(listOf(Color(0x70FFFFFF),Color(0x18FFFFFF),Color(0x48FFFFFF))),shape))
        content()
    }
}

internal enum class Glyph { HOME, CHART, MAP, PIN, MENU, BACK, NEXT, DOWN, RISE, SET, WIND, DROP, TEMP, PLAY, PAUSE, LOCATE, REFRESH, WALK, SUN, CLOCK, SEARCH }

@Composable
internal fun LineIcon(icon: Glyph, modifier: Modifier = Modifier.size(24.dp), color: Color = Color.White, filled: Boolean = false) {
    Canvas(modifier) {
        scale(size.width/24f, size.height/24f, Offset.Zero) {
            fun line(x:Float,y:Float,a:Float,b:Float) = drawLine(color,Offset(x,y),Offset(a,b),1.55f,StrokeCap.Round)
            fun path(vararg p:Float) { val path=Path().apply { moveTo(p[0],p[1]); for(i in 2 until p.size step 2) lineTo(p[i],p[i+1]) }; drawPath(path,color,style=Stroke(1.55f,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
            when(icon) {
                Glyph.HOME -> { if(filled) { val h=Path().apply {moveTo(3f,11f);lineTo(12f,3f);lineTo(21f,11f);lineTo(18f,11f);lineTo(18f,21f);lineTo(14f,21f);lineTo(14f,15f);lineTo(10f,15f);lineTo(10f,21f);lineTo(6f,21f);lineTo(6f,11f);close()};drawPath(h,color) } else { path(3f,11f,12f,3f,21f,11f); path(6f,9f,6f,21f,10f,21f,10f,15f,14f,15f,14f,21f,18f,21f,18f,9f) } }
                Glyph.CHART -> { if(filled) { drawRoundRect(color,Offset(4f,13f),Size(3f,8f));drawRoundRect(color,Offset(11f,4f),Size(3f,17f));drawRoundRect(color,Offset(18f,9f),Size(3f,12f)) } else { path(4f,21f,4f,13f,7f,13f,7f,21f,4f,21f); path(11f,21f,11f,4f,14f,4f,14f,21f,11f,21f); path(18f,21f,18f,9f,21f,9f,21f,21f,18f,21f) } }
                Glyph.MAP -> { if(filled) { val m=Path().apply {moveTo(3f,5f);lineTo(9f,2f);lineTo(15f,5f);lineTo(21f,2f);lineTo(21f,19f);lineTo(15f,22f);lineTo(9f,19f);lineTo(3f,22f);close()};drawPath(m,color);drawLine(Color(0xFF44656B),Offset(9f,3f),Offset(9f,18f),1.2f);drawLine(Color(0xFF44656B),Offset(15f,6f),Offset(15f,21f),1.2f) } else { path(3f,5f,9f,2f,15f,5f,21f,2f,21f,19f,15f,22f,9f,19f,3f,22f,3f,5f); line(9f,2f,9f,19f);line(15f,5f,15f,22f) } }
                Glyph.CLOCK -> { drawCircle(color,9f,Offset(12f,12f),style=Stroke(1.5f));path(12f,6f,12f,12f,16f,15f) }
                Glyph.SEARCH -> { drawCircle(color,7f,Offset(10f,10f),style=Stroke(1.6f));line(15f,15f,21f,21f) }
                Glyph.PIN -> { val p=Path().apply { moveTo(12f,22f); cubicTo(9f,17f,4f,12f,4f,8f); cubicTo(4f,-1f,20f,-1f,20f,8f); cubicTo(20f,12f,15f,17f,12f,22f) };drawPath(p,color);drawCircle(Color(0xFF5C797E),2.5f,Offset(12f,8f)) }
                Glyph.MENU -> { line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f) }
                Glyph.BACK -> { path(12f,5f,5f,12f,12f,19f);line(5f,12f,21f,12f) }
                Glyph.NEXT -> path(9f,5f,16f,12f,9f,19f)
                Glyph.DOWN -> path(6f,9f,12f,15f,18f,9f)
                Glyph.DROP -> { val p=Path().apply { moveTo(12f,2f); cubicTo(9f,7f,5f,11f,5f,15f);cubicTo(5f,24f,19f,24f,19f,15f);cubicTo(19f,11f,15f,7f,12f,2f) };drawPath(p,color,style=Stroke(1.5f)) }
                Glyph.WIND -> { line(2f,8f,14f,8f);drawArc(color,180f,275f,false,Offset(11f,2f),Size(6f,6f),style=Stroke(1.5f));line(2f,12f,19f,12f);drawArc(color,180f,270f,false,Offset(16f,6f),Size(6f,6f),style=Stroke(1.5f));line(2f,16f,13f,16f);drawArc(color,270f,270f,false,Offset(10f,16f),Size(6f,6f),style=Stroke(1.5f)) }
                Glyph.TEMP -> { drawRoundRect(color,Offset(9f,2f),Size(6f,15f),androidx.compose.ui.geometry.CornerRadius(3f),style=Stroke(1.5f));drawCircle(color,4.5f,Offset(12f,18f),style=Stroke(1.5f));line(12f,7f,12f,18f) }
                Glyph.RISE, Glyph.SET, Glyph.SUN -> {
                    val cy=if(icon==Glyph.SUN)12f else 14f
                    if(icon==Glyph.SUN) drawCircle(color,4.5f,Offset(12f,cy),style=Stroke(1.4f))
                    else { drawArc(color,180f,180f,false,Offset(7f,9f),Size(10f,10f),style=Stroke(1.4f));line(2f,15f,22f,15f) }
                    for(i in 0..7) { val a=i*PI/4; if(icon==Glyph.SUN || sin(a)<=.1) {line(12f+cos(a).toFloat()*8,cy+sin(a).toFloat()*8,12f+cos(a).toFloat()*10.5f,cy+sin(a).toFloat()*10.5f)} }
                    if(icon!=Glyph.SUN) {line(12f,18f,12f,23f); if(icon==Glyph.RISE)path(9f,21f,12f,18f,15f,21f) else path(9f,20f,12f,23f,15f,20f)}
                }
                Glyph.PLAY -> { val p=Path().apply {moveTo(7f,3f);lineTo(21f,12f);lineTo(7f,21f);close()};drawPath(p,color) }
                Glyph.PAUSE -> {line(8f,4f,8f,20f);line(16f,4f,16f,20f)}
                Glyph.LOCATE -> {drawCircle(color,7f,Offset(12f,12f),style=Stroke(1.5f));drawCircle(color,2f,Offset(12f,12f));line(12f,1f,12f,5f);line(12f,19f,12f,23f);line(1f,12f,5f,12f);line(19f,12f,23f,12f)}
                Glyph.REFRESH -> { drawArc(color,35f,300f,false,Offset(4f,4f),Size(16f,16f),style=Stroke(1.5f));path(16f,3f,21f,6f,17f,9f) }
                Glyph.WALK -> {drawCircle(color,2.3f,Offset(14f,3f));path(5f,12f,9f,8f,14f,9f,18f,13f,22f,13f);path(14f,9f,11f,15f,15f,18f,17f,23f);path(11f,15f,8f,22f)}
            }
        }
    }
}

@Composable
internal fun WeatherGlyph(code: Int, isDay: Boolean = true, size: Dp = 42.dp, color: Color = Color.White) {
    val rain = code in 51..67 || code in 80..82 || code in 95..99
    val snow = code in 71..77 || code in 85..86
    val cloud = code >= 2
    Canvas(Modifier.size(size).semantics { contentDescription=condition(code) }) {
        val w=this.size.width
        if(code<=2) {
            val center=if(cloud) Offset(w*.60f,w*.34f) else Offset(w*.5f,w*.47f)
            val r=if(cloud)w*.24f else w*.29f
            drawCircle(Brush.radialGradient(listOf(Color(0x55FFAC22),Color.Transparent),center,r*1.6f),r*1.6f,center)
            if(isDay) drawCircle(Brush.linearGradient(listOf(Color(0xFFFFC64E),Color(0xFFFF9200)),Offset(center.x,center.y-r),Offset(center.x,center.y+r)),r,center)
            else { val moon=Path().apply{moveTo(center.x+r*.3f,center.y-r);cubicTo(center.x-r*1.4f,center.y-r*.8f,center.x-r*1.1f,center.y+r*1.4f,center.x+r*.8f,center.y+r*.6f);cubicTo(center.x-r*.1f,center.y+r*.5f,center.x-r*.4f,center.y-r*.1f,center.x+r*.3f,center.y-r)};drawPath(moon,Color(0xFFDBEAF5)) }
        }
        if(cloud) {
            val path=Path().apply {
                moveTo(w*.22f,w*.76f);cubicTo(w*.02f,w*.76f,w*.02f,w*.48f,w*.2f,w*.47f)
                cubicTo(w*.17f,w*.19f,w*.51f,w*.16f,w*.56f,w*.4f)
                cubicTo(w*.65f,w*.32f,w*.78f,w*.4f,w*.78f,w*.5f)
                cubicTo(w*1.02f,w*.49f,w*1.01f,w*.78f,w*.77f,w*.78f);close()
            }
            val top=if(rain)Color(0xFFBFCACB) else Color.White
            val bottom=if(rain)Color(0xFF657F84) else Color(0xFFABB9BC)
            drawPath(path,Brush.linearGradient(listOf(top,top,bottom),Offset(w*.2f,w*.2f),Offset(w*.7f,w*.82f)))
            val front=Path().apply {
                moveTo(w*.48f,w*.78f)
                cubicTo(w*.29f,w*.78f,w*.28f,w*.55f,w*.45f,w*.53f)
                cubicTo(w*.50f,w*.38f,w*.72f,w*.38f,w*.76f,w*.54f)
                cubicTo(w*.95f,w*.51f,w*.98f,w*.77f,w*.77f,w*.78f)
                close()
            }
            drawPath(front,Brush.linearGradient(listOf(top.copy(alpha=.94f),bottom),Offset(w*.55f,w*.42f),Offset(w*.68f,w*.80f)))
            if(rain||snow) for(x in listOf(.36f,.53f,.7f)) {
                if(snow) drawCircle(Color(0xFFE5F6FA),w*.026f,Offset(w*x,w*.88f))
                else drawLine(Color(0xFF9ADDE9),Offset(w*x,w*.82f),Offset(w*(x-.05f),w*.94f),w*.023f,StrokeCap.Round)
            }
            if(code>=95) { val bolt=Path().apply{moveTo(w*.53f,w*.62f);lineTo(w*.43f,w*.85f);lineTo(w*.54f,w*.84f);lineTo(w*.48f,w);lineTo(w*.7f,w*.73f);lineTo(w*.56f,w*.75f);close()};drawPath(bolt,Color(0xFFFFB72D)) }
            if(code in 45..48) for(y in listOf(.85f,.94f)) drawLine(Color(0xFFD4DDDF),Offset(w*.2f,w*y),Offset(w*.8f,w*y),w*.023f,StrokeCap.Round)
        }
    }
}
