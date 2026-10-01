package pl.pogoda.mazowsze.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.pogoda.mazowsze.R

@Composable
fun CirralLaunchScreen() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.launch_atmosphere),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x33091E2A),
                    .38f to Color(0x520A2330),
                    .67f to Color(0x700A2130),
                    1f to Color(0x79071420)
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(144.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "CIRRAL",
                color = Color(0xFFF4F3EF),
                fontFamily = displayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = 5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
