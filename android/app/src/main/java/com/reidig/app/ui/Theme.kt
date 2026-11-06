package com.reidig.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reidig.app.R

/** Font keluarga dari prototipe Goldin v1: Lato (--font-title), Geist (--font-body), Geist Mono (--font-mono). */
val TitleFontFamily = FontFamily(
    Font(R.font.lato_bold, FontWeight.SemiBold),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.ExtraBold),
    Font(R.font.lato_black, FontWeight.Black),
)

val BodyFontFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_semibold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.ExtraBold),
)

val MonoFontFamily = FontFamily(
    Font(R.font.geist_mono_medium, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_semibold, FontWeight.SemiBold),
    Font(R.font.geist_mono_semibold, FontWeight.Bold),
    Font(R.font.geist_mono_semibold, FontWeight.ExtraBold),
)

/** Token warna dari EmDig / Goldin Design System (tokens.json). */
object Palette {
    // Purple / Primary scale
    val Primary50 = Color(0xFFF6F6FF)
    val Primary100 = Color(0xFFECEAFE)
    val Primary200 = Color(0xFFD7D4FC)
    val Primary300 = Color(0xFFB3ABF2)
    val Primary400 = Color(0xFF9280EE)
    val Primary500 = Color(0xFF7255D7)
    val Primary600 = Color(0xFF5436AA)
    val Primary = Color(0xFF371F76)       // primary-700 (background-dark-purple)
    val Primary800 = Color(0xFF271358)
    val Primary900 = Color(0xFF17083B)
    val Primary950 = Color(0xFF0F052C)

    // Gold / Secondary scale
    val Gold50 = Color(0xFFFBF7EC)
    val Gold100 = Color(0xFFF6EFD9)
    val Gold200 = Color(0xFFF0E3B8)
    val Gold300 = Color(0xFFEAD48E)
    val Gold = Color(0xFFE1C162)          // secondary-400 (gold accent on dark purple)
    val Gold500 = Color(0xFFD4AF37)
    val Gold600 = Color(0xFFB78E1A)
    val GoldDeep = Color(0xFF966E0E)      // secondary-700
    val GoldSoft = Color(0xFFFBF7EC)      // secondary-50

    // Neutral & Text scale
    val Ink = Color(0xFF0F052C)           // text-primary (purple-950)
    val InkSecondary = Color(0xFF525252)  // text-secondary (neutral-600)
    val InkSoft = Color(0xFF737373)       // text-tertiary (neutral-500)
    val Neutral50 = Color(0xFFFAFAFA)
    val Paper = Color(0xFFF5F5F5)         // background-default (neutral-100)
    val Neutral200 = Color(0xFFE5E5E5)
    val Line = Color(0xFFD4D4D4)          // border-subtle / border-input (neutral-300)
    val FieldBorder = Color(0xFFD4D4D4)
    val Disabled = Color(0xFFA3A3A3)      // neutral-400
    val DisabledText = Color(0xFFA3A3A3)

    // Semantic Status colors
    val Good = Color(0xFF16A34A)          // success-600
    val Good700 = Color(0xFF15803D)       // success-700
    val GoodSoft = Color(0xFFF0FDF4)      // success-50
    val Mint = Color(0xFF22C55E)          // success-500
    val Critical = Color(0xFFDC2626)      // error-600
    val CriticalBorder = Color(0xFFDC2626)
    val CriticalSoft = Color(0xFFFEF2F2)  // error-50
    val Warn = Color(0xFFA16207)          // warning-700
    val Warn600 = Color(0xFFCA8A04)       // warning-600
    val WarnSoft = Color(0xFFFEFCE8)      // warning-50
    val WarnBorder = Color(0xFFFEF08A)    // warning-200
    val Info50 = Color(0xFFEFF6FF)
    val Info200 = Color(0xFFBFDBFE)
    val Info500 = Color(0xFF3B82F6)
    val Link = Color(0xFF371F76)

    // Aliases for compatibility
    val PrimaryPressed = Primary800
    val Lavender = Primary50
    val Ring = Primary100
    val Focus = Primary600
    val Night = Primary950
    val NightSoft = Primary
}

val NightGradient = Brush.verticalGradient(listOf(Palette.Primary, Palette.Primary800))

private val ReidigTypography = Typography(
    displayLarge = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)

@Composable
fun ReidigTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Primary,
            onPrimary = Color.White,
            secondary = Palette.Gold,
            background = Palette.Paper,
            surface = Color.White,
            onSurface = Palette.Ink,
            onBackground = Palette.Ink,
            error = Palette.Critical,
        ),
        typography = ReidigTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
