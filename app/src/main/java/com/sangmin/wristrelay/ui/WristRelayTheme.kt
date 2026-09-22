package com.sangmin.wristrelay.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sangmin.wristrelay.R

val DeepNavy = Color(0xFF262721)
val NavySurface = Color(0xFFFFFFFF)
val NavyRaised = Color(0xFFE8E4DC)
val Mint = Color(0xFF97543C)
val MintDark = Color(0xFF73402F)
val Mist = Color(0xFFF7F5F0)
val Muted = Color(0xFF5E605B)
val Danger = Color(0xFFA72D3D)

val WantedSans = FontFamily(
    Font(R.font.wanted_sans_regular, FontWeight.Normal),
    Font(R.font.wanted_sans_medium, FontWeight.Medium),
    Font(R.font.wanted_sans_semibold, FontWeight.SemiBold),
    Font(R.font.wanted_sans_bold, FontWeight.Bold),
)

@Composable
fun WristRelayTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFE6A990),
            onPrimary = Color(0xFF321C15),
            primaryContainer = Color(0xFF57382D),
            onPrimaryContainer = Color(0xFFFFDDD0),
            background = Color(0xFF1D1E1B),
            onBackground = Color(0xFFF5F2EB),
            surface = Color(0xFF292A26),
            onSurface = Color(0xFFF5F2EB),
            surfaceVariant = Color(0xFF383934),
            onSurfaceVariant = Color(0xFFCBC9C1),
            error = Color(0xFFFFB4AB),
        )
    } else {
        lightColorScheme(
            primary = Mint,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF4DED3),
            onPrimaryContainer = Color(0xFF5F3223),
            background = Mist,
            onBackground = DeepNavy,
            surface = NavySurface,
            onSurface = DeepNavy,
            surfaceVariant = NavyRaised,
            onSurfaceVariant = Muted,
            error = Danger,
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = WantedSans, fontWeight = FontWeight.Bold),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = WantedSans, fontWeight = FontWeight.Bold),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = WantedSans, fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = WantedSans, fontWeight = FontWeight.SemiBold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = WantedSans, fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = WantedSans),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = WantedSans),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = WantedSans, fontWeight = FontWeight.SemiBold),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = WantedSans, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
