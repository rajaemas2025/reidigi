package com.reidig.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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

private val BarTop = Color(0xFFF0E3B8)
private val BarMiddle = Color(0xFFE1C162)
private val BarBottom = Color(0xFFD4AF37)
private val BarBase = Color(0xFF966E0E)

/** Latar gelap dengan cahaya ungu di tengah, untuk layar pembuka. */
val GlowGradient = Brush.radialGradient(
    listOf(Color(0xFF5436AA), Color.Transparent),
    radius = 900f,
)

/** Tumpukan emas batangan seperti pada desain prototipe. */
@Composable
fun GoldBarArt(width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, width * 0.62f)) {
        val w = size.width
        val h = size.height
        fun trapezoid(topY: Float, bottomY: Float, topInset: Float, bottomInset: Float, color: Color) {
            val path = Path().apply {
                moveTo(w * topInset, topY)
                lineTo(w * (1 - topInset), topY)
                lineTo(w * (1 - bottomInset), bottomY)
                lineTo(w * bottomInset, bottomY)
                close()
            }
            drawPath(path, color)
        }
        drawOval(Color.Black.copy(alpha = 0.14f), Offset(0f, h * 0.84f), Size(w, h * 0.16f))
        trapezoid(0f, h * 0.2f, 0.38f, 0.3f, BarTop)
        trapezoid(h * 0.2f, h * 0.42f, 0.3f, 0.2f, BarMiddle)
        trapezoid(h * 0.42f, h * 0.78f, 0.2f, 0.06f, BarBottom)
        drawRect(BarBase, Offset(w * 0.06f, h * 0.78f), Size(w * 0.88f, h * 0.11f))
        val stroke = h * 0.025f
        drawLine(BarTop, Offset(w * 0.4f, h * 0.31f), Offset(w * 0.58f, h * 0.31f), stroke)
        drawLine(BarTop, Offset(w * 0.26f, h * 0.6f), Offset(w * 0.46f, h * 0.6f), stroke)
    }
}

/** Ikon 3 batang emas bertumpuk di dalam kotak ungu sesuai header Beranda prototipe. */
@Composable
fun GoldBarsBadge(size: Dp = 40.dp, cornerRadius: Dp = 12.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(cornerRadius)).background(Palette.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            val barH = h * 0.22f
            val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            // Top bar
            drawRoundRect(
                color = Palette.Gold,
                topLeft = Offset(w * 0.2f, h * 0.12f),
                size = Size(w * 0.6f, barH),
                cornerRadius = r,
            )
            // Bottom left bar
            drawRoundRect(
                color = Palette.Gold,
                topLeft = Offset(0f, h * 0.56f),
                size = Size(w * 0.46f, barH),
                cornerRadius = r,
            )
            // Bottom right bar
            drawRoundRect(
                color = Palette.Gold,
                topLeft = Offset(w * 0.54f, h * 0.56f),
                size = Size(w * 0.46f, barH),
                cornerRadius = r,
            )
        }
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

/** Tombol masuk dengan akun Google sesuai gaya outlined button prototipe. */
@Composable
fun GoogleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, Palette.Primary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Palette.Primary),
    ) {
        Image(painterResource(R.drawable.ic_google), null, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Palette.Primary)
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
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Palette.Good700, fontSize = 13.5.sp)
    }
}

@ScreenPreview
@Composable
private fun GoldBarPreview() = ReidigTheme {
    Box(Modifier.background(Palette.Primary).padding(24.dp)) { GoldBarArt(180.dp) }
}
