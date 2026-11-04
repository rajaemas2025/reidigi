package com.reidig.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Warna dari REI Design System. */
object Palette {
    val Primary = Color(0xFF371F76)
    val PrimaryPressed = Color(0xFF5436AA)
    val Lavender = Color(0xFFF6F6FF)
    val Ring = Color(0xFFEBE5F7)
    val Focus = Color(0xFF5436AA)
    val Night = Color(0xFF0F052C)
    val NightSoft = Color(0xFF2A1F5C)
    val Gold = Color(0xFFE0B553)
    val GoldDeep = Color(0xFFA87A1F)
    val GoldSoft = Color(0xFFFBF3DF)
    val Ink = Color(0xFF0F052C)
    val InkSoft = Color(0xFF737373)
    val Line = Color(0xFFD4D4D4)
    val FieldBorder = Color(0xFFA3A3A3)
    val Disabled = Color(0xFFA3A3A3)
    val DisabledText = Color(0xFFA3A3A3)
    val Paper = Color(0xFFFAFAFA)
    val Link = Color(0xFF2563EB)
    val Good = Color(0xFF2E7D57)
    val GoodSoft = Color(0xFFE5F5EC)
    val Mint = Color(0xFF5FC79B)
    val Critical = Color(0xFFDC2626)
    val CriticalBorder = Color(0xFFEF4444)
    val CriticalSoft = Color(0xFFFEE2E2)
    val Warn = Color(0xFF93610A)
    val WarnSoft = Color(0xFFFCF1D9)
}

val NightGradient = Brush.verticalGradient(listOf(Palette.NightSoft, Palette.Night))

@Composable
fun ReidigTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Primary,
            onPrimary = Color.White,
            secondary = Palette.Gold,
            background = Color.White,
            surface = Color.White,
            onSurface = Palette.Ink,
            onBackground = Palette.Ink,
            error = Palette.Critical,
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
