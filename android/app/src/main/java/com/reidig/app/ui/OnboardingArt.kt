package com.reidig.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reidig.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Kumpulan ikon SVG Tabler & kustom yang sama persis dengan Goldin Prototype v1.html. */
object PrototypeIcons {
    private fun tablerVector(name: String, paths: List<String>, strokeWidth: Float = 1.75f): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val GoldIngots: ImageVector by lazy {
        tablerVector(
            "GoldIngots",
            listOf(
                "M9.2 4.5h5.6l1.7 5.5h-9z",
                "M4.2 13.5h5.6l1.7 5.5h-9z",
                "M14.2 13.5h5.6l1.7 5.5h-9z",
            ),
        )
    }

    val Cart: ImageVector by lazy {
        tablerVector(
            "Cart",
            listOf(
                "M4 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
                "M15 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
                "M17 17h-11v-14h-2",
                "M6 5l14 1l-1 7h-13",
            ),
        )
    }

    val PriceTag: ImageVector by lazy {
        tablerVector(
            "PriceTag",
            listOf(
                "M5.5 7.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
                "M3 6v5.172a2 2 0 0 0 .586 1.414l7.71 7.71a2.41 2.41 0 0 0 3.408 0l5.592 -5.592a2.41 2.41 0 0 0 0 -3.408l-7.71 -7.71a2 2 0 0 0 -1.414 -.586h-5.172a3 3 0 0 0 -3 3z",
            ),
        )
    }

    val CashBanknote: ImageVector by lazy {
        tablerVector(
            "CashBanknote",
            listOf(
                "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
                "M3 8a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2z",
                "M18 12h.01",
                "M6 12h.01",
            ),
        )
    }

    val Home: ImageVector by lazy {
        tablerVector(
            "Home",
            listOf(
                "M5 12h-2l9 -9l9 9h-2",
                "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7",
                "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6",
            ),
        )
    }

    val Wallet: ImageVector by lazy {
        tablerVector(
            "Wallet",
            listOf(
                "M17 8v-3a1 1 0 0 0 -1 -1h-10a2 2 0 0 0 0 4h12a1 1 0 0 1 1 1v3m0 4v3a1 1 0 0 1 -1 1h-12a2 2 0 0 1 -2 -2v-12",
                "M20 12v4h-4a2 2 0 0 1 0 -4h4",
            ),
        )
    }

    val ArrowsExchange: ImageVector by lazy {
        tablerVector(
            "ArrowsExchange",
            listOf(
                "M7 10h14l-4 -4",
                "M17 14h-14l4 4",
            ),
        )
    }

    val User: ImageVector by lazy {
        tablerVector(
            "User",
            listOf(
                "M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0",
                "M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2",
            ),
        )
    }

    val Bell: ImageVector by lazy {
        tablerVector(
            "Bell",
            listOf(
                "M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6",
                "M9 17v1a3 3 0 0 0 6 0v-1",
            ),
        )
    }

    val IdBadge: ImageVector by lazy {
        tablerVector(
            "IdBadge",
            listOf(
                "M3 7a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3z",
                "M7 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
                "M15 8h2",
                "M15 12h2",
                "M7 16h10",
            ),
        )
    }

    val FaceId: ImageVector by lazy {
        tablerVector(
            "FaceId",
            listOf(
                "M4 8v-2a2 2 0 0 1 2 -2h2",
                "M4 16v2a2 2 0 0 0 2 2h2",
                "M16 4h2a2 2 0 0 1 2 2v2",
                "M16 20h2a2 2 0 0 0 2 -2v-2",
                "M9 10h.01",
                "M15 10h.01",
                "M9.5 15a3.5 3.5 0 0 0 5 0",
            ),
        )
    }

    val HistoryClock: ImageVector by lazy {
        tablerVector(
            "HistoryClock",
            listOf(
                "M12 8l0 4l2 2",
                "M3.05 11a9 9 0 1 1 .5 4m-.5 5v-5h5",
            ),
        )
    }

    val ArrowDownLeft: ImageVector by lazy {
        tablerVector(
            "ArrowDownLeft",
            listOf(
                "M17 7l-10 10",
                "M16 17l-9 0l0 -9",
            ),
        )
    }

    val ArrowUpRight: ImageVector by lazy {
        tablerVector(
            "ArrowUpRight",
            listOf(
                "M17 7l-10 10",
                "M8 7l9 0l0 9",
            ),
        )
    }

    val ShieldCheck: ImageVector by lazy {
        tablerVector(
            "ShieldCheck",
            listOf(
                "M11.46 20.846a12 12 0 0 1 -7.96 -14.846a12 12 0 0 0 8.5 -3a12 12 0 0 0 8.5 3a12 12 0 0 1 -.09 7.06",
                "M15 19l2 2l4 -4",
            ),
        )
    }

    val Mail: ImageVector by lazy {
        tablerVector(
            "Mail",
            listOf(
                "M3 7a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10z",
                "M3 7l9 6l9 -6",
            ),
        )
    }

    val Lock: ImageVector by lazy {
        tablerVector(
            "Lock",
            listOf(
                "M5 13a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6z",
                "M11 16a1 1 0 1 0 2 0a1 1 0 0 0 -2 0",
                "M8 11v-4a4 4 0 1 1 8 0v4",
            ),
        )
    }

    val Eye: ImageVector by lazy {
        tablerVector(
            "Eye",
            listOf(
                "M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0",
                "M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6",
            ),
        )
    }

    val EyeOff: ImageVector by lazy {
        tablerVector(
            "EyeOff",
            listOf(
                "M10.585 10.587a2 2 0 0 0 2.829 2.828",
                "M16.681 16.673a8.717 8.717 0 0 1 -4.681 1.327c-3.6 0 -6.6 -2 -9 -6c1.272 -2.12 2.712 -3.678 4.32 -4.674m2.86 -1.146a9.055 9.055 0 0 1 1.82 -.18c3.6 0 6.6 2 9 6c-.666 1.11 -1.379 2.067 -2.138 2.87",
                "M3 3l18 18",
            ),
        )
    }

    val Calendar: ImageVector by lazy {
        tablerVector(
            "Calendar",
            listOf(
                "M4 7a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12z",
                "M16 3v4",
                "M8 3v4",
                "M4 11h16",
            ),
        )
    }

    val Refresh: ImageVector by lazy {
        tablerVector(
            "Refresh",
            listOf(
                "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4",
                "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4",
            ),
        )
    }

    val ChevronLeft: ImageVector by lazy {
        tablerVector("ChevronLeft", listOf("M15 6l-6 6l6 6"), strokeWidth = 2f)
    }

    val ChevronRight: ImageVector by lazy {
        tablerVector("ChevronRight", listOf("M9 6l6 6l-6 6"), strokeWidth = 1.75f)
    }

    val Check: ImageVector by lazy {
        tablerVector("Check", listOf("M5 12l5 5l10 -10"), strokeWidth = 2.25f)
    }

    val ArrowsUpDown: ImageVector by lazy {
        tablerVector(
            "ArrowsUpDown",
            listOf(
                "M7 3l0 18",
                "M10 6l-3 -3l-3 3",
                "M20 18l-3 3l-3 -3",
                "M17 21l0 -18",
            ),
        )
    }

    val SmsMessage: ImageVector by lazy {
        tablerVector(
            "SmsMessage",
            listOf(
                "M8 9h8",
                "M8 13h6",
                "M18 4a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-5l-5 3v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12z",
            ),
        )
    }
}

/** Latar gelap dengan cahaya ungu di tengah. */
val GlowGradient = Brush.radialGradient(
    listOf(Color(0xFF5436AA), Color.Transparent),
    radius = 900f,
)

/** Ilustrasi 3D emas batangan dari aset PNG prototipe Goldin v1 dengan animasi melayang halus. */
@Composable
fun GoldBarArt(height: Dp = 280.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "float")
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "floatY",
    )
    Image(
        painter = painterResource(R.drawable.img_gold_bars_hero),
        contentDescription = "Ilustrasi emas batangan",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height).offset(y = offsetY.dp),
    )
}

/** Lencana 3 trapesium emas di dalam kotak ungu sesuai header 07 Beranda & 20 Dompet prototipe. */
@Composable
fun GoldBarsBadge(size: Dp = 40.dp, cornerRadius: Dp = 12.dp, iconSize: Dp = 24.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(cornerRadius)).background(Palette.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PrototypeIcons.GoldIngots,
            contentDescription = null,
            tint = Palette.Gold,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Taburan konfeti statis untuk layar selesai verifikasi. */
fun DrawScope.drawConfetti() {
    val colors = listOf(Palette.Gold, Palette.Primary300)
    repeat(26) { index ->
        val angle = index * 2.4f
        val distance = 0.28f + (index % 7) * 0.1f
        val x = size.width * (0.5f + distance * cos(angle) * 0.75f)
        val y = size.height * (0.32f + distance * sin(angle) * 0.42f)
        val piece = 7.dp.toPx()
        rotateAround(angle * 180f / PI.toFloat(), Offset(x, y)) {
            drawRoundRect(
                colors[index % colors.size].copy(alpha = 0.75f),
                Offset(x, y),
                Size(piece * 1.5f, piece),
                CornerRadius(2.dp.toPx()),
            )
        }
    }
}

private inline fun DrawScope.rotateAround(degrees: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.transform.rotate(degrees, pivot)
    block()
    drawContext.canvas.restore()
}

/** Tombol masuk/daftar dengan akun Google sesuai gaya outlined button prototipe. */
@Composable
fun GoogleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, Palette.Primary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Palette.Primary),
    ) {
        Image(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.Primary)
    }
}

/** Bar hijau penanda email sudah terverifikasi. */
@Composable
fun VerifiedBanner(text: AnnotatedString) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.GoodSoft)
            .border(1.dp, Palette.Good.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Palette.Good), contentAlignment = Alignment.Center) {
            Icon(PrototypeIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontFamily = BodyFontFamily, color = Palette.Good700, fontSize = 13.5.sp)
    }
}

@ScreenPreview
@Composable
private fun GoldBarPreview() = ReidigTheme {
    Box(Modifier.background(Palette.Paper).padding(24.dp)) { GoldBarArt(260.dp) }
}
