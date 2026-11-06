package com.reidig.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reidig.app.R
import com.reidig.shared.GoldToken
import com.reidig.shared.PaymentMethod
import com.reidig.shared.PendingOrder
import com.reidig.shared.Sku
import com.reidig.shared.gram
import com.reidig.shared.percent
import com.reidig.shared.rupiah
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(
    env: AppEnv,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onPrint: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onInstallment: () -> Unit,
    onWithdraw: () -> Unit,
    onVerify: () -> Unit,
    onPrices: () -> Unit,
) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val sku1g = state.sku("LMRE-1G")
    val firstName = state.user?.name?.substringBefore(' ')?.ifBlank { "Sobat" } ?: "Sobat"
    var hideBalance by rememberSaveable { mutableStateOf(false) }

    // Simulation state
    var simMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Beli, 1 = Jual
    var simUnit by rememberSaveable { mutableStateOf("rp") } // "rp" or "gr"
    var simInput by rememberSaveable { mutableStateOf("100000") }

    // Price chart range state
    var chartRange by rememberSaveable { mutableStateOf("1B") }

    val weightGrams = state.totalWeightMg / 1000.0
    val weightFormatted = String.format(Locale.forLanguageTag("id-ID"), "%.4f", weightGrams)

    Column(
        Modifier.fillMaxSize()
            .background(Palette.Paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // 1. Top Header Bar (GoldBarsBadge + Halo, User + Notification bell, matching 07 Beranda lines 962-975)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoldBarsBadge(40.dp, 24.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Halo,",
                        color = Palette.InkSecondary,
                        fontFamily = BodyFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                    Text(
                        firstName,
                        color = Palette.Ink,
                        fontFamily = TitleFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                    )
                }
            }
            Box(
                Modifier.size(44.dp)
                    .cardDepth(Color.White, CircleShape, Palette.Line)
                    .clickable(onClick = onPrices),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PrototypeIcons.Bell, "Notifikasi & Harga", tint = Palette.Ink, modifier = Modifier.size(20.dp))
                Box(
                    Modifier.size(8.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .clip(CircleShape)
                        .background(Palette.Critical),
                )
            }
        }

        // 2. Unverified e-KYC Banner (matching 07 Beranda lines 977-989)
        if (state.user?.verified == false) {
            SurfaceCard(tint = Palette.Primary50, outline = Palette.Primary100) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Palette.Primary100),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PrototypeIcons.IdBadge, null, tint = Palette.Primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Verifikasi identitasmu dulu",
                            color = Palette.Ink,
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Cukup sekitar 2 menit. Setelah itu kamu bisa beli, jual, dan top up emas.",
                            color = Palette.InkSecondary,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onVerify,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Primary, contentColor = Palette.Primary50),
                ) {
                    Text(
                        "Verifikasi sekarang",
                        fontFamily = BodyFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(PrototypeIcons.ChevronRight, null, tint = Palette.Primary50, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 3. Dark Purple Hero Balance Card (matching 07 Beranda lines 991-1005)
        HeroPurpleCard {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Saldo emas", color = Palette.Primary200, fontFamily = BodyFontFamily, fontSize = 14.sp)
                    Box(
                        Modifier.size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { hideBalance = !hideBalance },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (hideBalance) PrototypeIcons.Eye else PrototypeIcons.EyeOff,
                            contentDescription = "Sembunyikan atau tampilkan saldo",
                            tint = Palette.Primary100,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (hideBalance) "••••••" else weightFormatted,
                        color = Palette.Gold,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                    )
                    if (!hideBalance) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "gr",
                            color = Palette.Gold,
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (hideBalance) "≈ Rp •••••••" else "≈ ${rupiah(state.portfolioValue)} (harga jual saat ini)",
                    color = Palette.Primary100,
                    fontFamily = BodyFontFamily,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF6F6FF).copy(alpha = 0.24f))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onWithdraw),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Saldo kas", color = Palette.Primary200, fontFamily = BodyFontFamily, fontSize = 14.sp)
                    Text(
                        if (hideBalance) "Rp •••••" else rupiah(state.walletBalance),
                        color = Palette.Primary50,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 4. Quick Actions Row — Exact 4 items from Goldin Prototype v1.html (lines 1007-1024):
        //    1) Beli emas (Cart)
        //    2) Jual emas (PriceTag)
        //    3) Kelola kas (CashBanknote)
        //    4) Cetak fisik (GoldIngots)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionTile("Beli emas", PrototypeIcons.Cart, Modifier.weight(1f), onBuy)
            QuickActionTile("Jual emas", PrototypeIcons.PriceTag, Modifier.weight(1f), onSell)
            QuickActionTile("Kelola kas", PrototypeIcons.CashBanknote, Modifier.weight(1f), onWithdraw)
            QuickActionTile("Cetak fisik", PrototypeIcons.GoldIngots, Modifier.weight(1f), onPrint)
        }

        Spacer(Modifier.height(18.dp))

        // 5. Simulasi Transaksi Card (matching 07 Beranda lines 1026-1053)
        SurfaceCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Simulasi transaksi",
                    fontFamily = TitleFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Palette.Ink,
                )
                Row(
                    Modifier.width(148.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Neutral200)
                        .padding(3.dp),
                ) {
                    listOf("Beli", "Jual").forEachIndexed { idx, label ->
                        val active = simMode == idx
                        Box(
                            Modifier.weight(1f)
                                .then(if (active) Modifier.shadow(1.dp, RoundedCornerShape(50)) else Modifier)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) Color.White else Color.Transparent)
                                .clickable { simMode = idx }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontFamily = BodyFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Palette.Ink else Palette.InkSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Masukkan nominal", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))

            // Input with Rp / gr toggle
            Row(
                Modifier.fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, Palette.Line, RoundedCornerShape(12.dp))
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = simInput,
                    onValueChange = { raw ->
                        simInput = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        color = Palette.Ink,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Palette.Paper)
                        .padding(3.dp),
                ) {
                    listOf("rp" to "Rp", "gr" to "gr").forEach { (u, label) ->
                        val active = simUnit == u
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (active) Palette.Primary else Color.Transparent)
                                .clickable {
                                    if (simUnit != u) {
                                        simUnit = u
                                        simInput = if (u == "rp") "100000" else "1"
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (active) Color.White else Palette.InkSecondary,
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val rate = if (simMode == 0) sku1g.buyPrice else sku1g.buybackPrice
            val numeric = simInput.replace(',', '.').toDoubleOrNull() ?: 0.0
            val resultLabel = when {
                simUnit == "rp" && simMode == 0 -> "Emas yang didapat"
                simUnit == "rp" && simMode == 1 -> "Emas yang dijual"
                simMode == 0 -> "Estimasi harga beli"
                else -> "Estimasi dana diterima"
            }
            val resultValue = if (simUnit == "rp") {
                val gr = if (rate > 0) numeric / rate.toDouble() else 0.0
                String.format(Locale.forLanguageTag("id-ID"), "%.4f gr", gr)
            } else {
                val rp = (numeric * rate).toLong()
                rupiah(rp)
            }

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Primary50)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(resultLabel, color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.sp)
                Text(
                    resultValue,
                    color = Palette.Primary,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = if (simMode == 0) onBuy else onSell,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, Palette.Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Primary),
            ) {
                Text(
                    if (simMode == 0) "Beli emas" else "Jual emas",
                    fontFamily = BodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 6. Harga Emas Per Gram + Grafik Card (matching 07 Beranda lines 1055-1102)
        SurfaceCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Harga emas per gram",
                        fontFamily = TitleFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Palette.Ink,
                    )
                    Text(
                        "Diperbarui ${state.priceUpdatedAtLabel}",
                        color = Palette.InkSoft,
                        fontFamily = BodyFontFamily,
                        fontSize = 12.sp,
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Palette.Primary50)
                        .clickable(onClick = onPrices)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PrototypeIcons.Refresh, null, tint = Palette.Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Semua pecahan",
                        color = Palette.Primary,
                        fontFamily = BodyFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PriceStatBox(
                    label = "Harga beli",
                    price = rupiah(sku1g.buyPrice),
                    change = "▲ 0,42% hari ini",
                    modifier = Modifier.weight(1f),
                )
                PriceStatBox(
                    label = "Harga jual",
                    price = rupiah(sku1g.buybackPrice),
                    change = "▲ 0,38% hari ini",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            GoldPriceChart(range = chartRange)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1H", "1M", "1B", "6B", "1T").forEach { r ->
                    val active = chartRange == r
                    Box(
                        Modifier.weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (active) Palette.Primary else Palette.Paper)
                            .clickable { chartRange = r },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            r,
                            color = if (active) Color.White else Palette.InkSecondary,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 7. Bottom Security Badge Card (matching 07 Beranda lines 1104-1112)
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Palette.GoodSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PrototypeIcons.ShieldCheck, null, tint = Palette.Good, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Gold Encryption AES-256 & terdaftar resmi Bappebti",
                    color = Palette.InkSecondary,
                    fontFamily = BodyFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuickActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).cardDepth(Color.White, RoundedCornerShape(16.dp), Palette.Line),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Palette.Primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontFamily = BodyFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun PriceStatBox(label: String, price: String, change: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(Palette.Paper)
            .padding(12.dp),
    ) {
        Text(label, color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            price,
            color = Palette.Ink,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(change, color = Palette.Good, fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
private fun GoldPriceChart(range: String) {
    val points = remember(range) {
        when (range) {
            "1H" -> listOf(0.45f, 0.48f, 0.46f, 0.52f, 0.50f, 0.56f, 0.62f, 0.60f, 0.68f, 0.72f)
            "1M" -> listOf(0.35f, 0.40f, 0.38f, 0.45f, 0.52f, 0.49f, 0.58f, 0.64f, 0.69f, 0.78f)
            "1B" -> listOf(0.25f, 0.32f, 0.30f, 0.42f, 0.48f, 0.55f, 0.52f, 0.65f, 0.74f, 0.85f)
            "6B" -> listOf(0.18f, 0.24f, 0.31f, 0.28f, 0.40f, 0.50f, 0.61f, 0.68f, 0.79f, 0.90f)
            else -> listOf(0.12f, 0.20f, 0.28f, 0.36f, 0.44f, 0.52f, 0.63f, 0.74f, 0.84f, 0.94f)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width
        val h = size.height
        val padTop = 10.dp.toPx()
        val padBottom = 10.dp.toPx()
        val usableH = h - padTop - padBottom
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val areaPath = Path()
        points.forEachIndexed { idx, norm ->
            val x = idx * stepX
            val y = padTop + (1f - norm) * usableH
            if (idx == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, h)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(w, h)
        areaPath.close()

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(Palette.Primary.copy(alpha = 0.22f), Palette.Primary.copy(alpha = 0.0f)),
                startY = 0f,
                endY = h,
            ),
        )
        drawPath(
            path = linePath,
            color = Palette.Primary,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val lastY = padTop + (1f - points.last()) * usableH
        drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(w, lastY))
        drawCircle(Palette.Primary, radius = 3.5.dp.toPx(), center = Offset(w, lastY))
    }
}

@ScreenPreview
@Composable
private fun HomePreview() = ReidigTheme { HomeScreen(rememberPreviewEnv(), {}, {}, {}, {}, {}, {}, {}) }

@ScreenPreview
@Composable
private fun HomeUnverifiedPreview() = ReidigTheme { HomeScreen(rememberPreviewEnv(false), {}, {}, {}, {}, {}, {}, {}) }

/** Harga beli dan harga jual (buyback) semua pecahan. */
@Composable
fun PriceTableScreen(env: AppEnv, onBack: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Tabel Harga Emas", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp)) {
            item {
                SurfaceCard {
                    Text("Diperbarui ${state.priceUpdatedAtLabel}", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    PriceTableRow("Berat", "Harga beli", "Harga jual", header = true)
                    HorizontalDivider(color = Palette.Line)
                    state.skus.forEachIndexed { idx, sku ->
                        PriceTableRow(gram(sku.weightMg), rupiah(sku.buyPrice), rupiah(sku.buybackPrice))
                        if (idx < state.skus.lastIndex) {
                            HorizontalDivider(color = Palette.Line.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(state.skus.firstOrNull()?.product ?: "", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.5.sp)
            }
        }
    }
}

@ScreenPreview
@Composable
private fun PriceTablePreview() = ReidigTheme { PriceTableScreen(rememberPreviewEnv()) {} }

@Composable
private fun PriceTableRow(weight: String, buy: String, sell: String, header: Boolean = false) {
    val color = if (header) Palette.InkSoft else Palette.Ink
    val fontSize = if (header) 12.sp else 13.sp
    Row(Modifier.fillMaxWidth().padding(vertical = if (header) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            weight,
            color = color,
            fontFamily = BodyFontFamily,
            fontSize = fontSize,
            fontWeight = if (header) FontWeight.Medium else FontWeight.SemiBold,
            modifier = Modifier.width(64.dp),
        )
        Text(
            buy,
            color = color,
            fontFamily = if (header) BodyFontFamily else MonoFontFamily,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            sell,
            color = color,
            fontFamily = if (header) BodyFontFamily else MonoFontFamily,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun CatalogScreen(env: AppEnv, header: @Composable () -> Unit = {}, onSelect: (String) -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val sku1g = state.sku("LMRE-1G")
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Transaksi", null)
        header()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Summary card matching 13 Transaksi prototype
                SurfaceCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Saldo kas tersedia", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                rupiah(state.walletBalance),
                                color = Palette.Ink,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Harga beli / gr", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                rupiah(sku1g.buyPrice),
                                color = Palette.Primary,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Pilih pecahan emas fisik 24K",
                    fontFamily = TitleFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Palette.Ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(state.skus, key = { it.code }) { sku ->
                CatalogSkuCard(sku) { onSelect(sku.code) }
            }
        }
    }
}

@Composable
private fun CatalogSkuCard(sku: Sku, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.GoldSoft)
                    .border(1.dp, Palette.Gold200, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    gram(sku.weightMg).removeSuffix(" gr"),
                    color = Palette.GoldDeep,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${sku.product} ${gram(sku.weightMg)}",
                    fontFamily = BodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    rupiah(sku.buyPrice),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Palette.Primary,
                )
            }
            if (sku.stock == 0) {
                StatusChip("Stok habis", ChipTone.WARN, Icons.Outlined.WarningAmber)
            } else {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Palette.Primary50).padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "Stok ${sku.stock}",
                        color = Palette.Primary,
                        fontFamily = BodyFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@ScreenPreview
@Composable
private fun CatalogPreview() = ReidigTheme { CatalogScreen(rememberPreviewEnv()) {} }

@Composable
fun BuyScreen(env: AppEnv, skuCode: String, onBack: () -> Unit, onDone: (List<GoldToken>) -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val sku = state.sku(skuCode)
    var quantity by remember { mutableIntStateOf(1) }
    var method by remember { mutableStateOf(PaymentMethod.QRIS) }
    val inPreview = LocalInspectionMode.current
    var quote by remember { mutableStateOf(if (inPreview) env.repo.quoteBuy(skuCode, 1) else null) }
    var askPin by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableLongStateOf(60L) }
    var pendingOrder by remember { mutableStateOf<PendingOrder?>(null) }

    // Harga dikunci 60 detik, lalu diperbarui otomatis.
    LaunchedEffect(quantity, pendingOrder) {
        while (pendingOrder == null) {
            val current = env.attempt { env.repo.quoteBuy(skuCode, quantity) }
            quote = current
            if (current == null) break
            while (true) {
                secondsLeft = (current.expiresAtMs - System.currentTimeMillis()) / 1000
                if (secondsLeft <= 0) break
                delay(1000)
            }
        }
    }

    val current = quote
    val insufficient = method == PaymentMethod.WALLET && current != null && state.walletBalance < current.total

    FormScreen(
        "Konfirmasi transaksi",
        onBack,
        bottom = {
            PrimaryButton(
                "Konfirmasi & bayar ${current?.let { rupiah(it.total) } ?: ""}",
                { askPin = true },
                enabled = current != null && !insufficient && sku.stock > 0,
            )
        },
    ) {
        // Price Lock Timer Banner (matching 14 Konfirmasi prototype)
        if (current != null) {
            val sec = secondsLeft.coerceIn(0, 60)
            SurfaceCard(tint = Palette.Primary50, outline = Palette.Primary100) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { sec / 60f },
                            modifier = Modifier.size(44.dp),
                            color = if (sec <= 15) Palette.Critical else Palette.Primary,
                            trackColor = Palette.Primary200,
                            strokeWidth = 3.5.dp,
                        )
                        Text(
                            "${sec}d",
                            fontSize = 11.sp,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = if (sec <= 15) Palette.Critical else Palette.Primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Harga dikunci ${sec} detik",
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Palette.Ink,
                        )
                        Text(
                            "Harga akan diperbarui otomatis jika waktu habis.",
                            color = Palette.InkSecondary,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Detail Emas & Rincian Order Card
        SurfaceCard {
            Text(
                "DETAIL EMAS",
                color = Palette.InkSoft,
                fontFamily = BodyFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(8.dp))
            SummaryRow("Jenis transaksi", "Beli Emas")
            SummaryRow("Produk", "${sku.product} ${gram(sku.weightMg)}", strong = true, valueColor = Palette.Primary)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Jumlah keping (Stok ${sku.stock})",
                    color = Palette.InkSecondary,
                    fontFamily = BodyFontFamily,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (quantity > 1) quantity-- }, enabled = quantity > 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Remove, "Kurangi", modifier = Modifier.size(18.dp))
                }
                Text(
                    "$quantity",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                IconButton(onClick = { if (quantity < sku.stock) quantity++ }, enabled = quantity < sku.stock, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, "Tambah", modifier = Modifier.size(18.dp))
                }
            }

            if (current != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Palette.Line)
                Spacer(Modifier.height(10.dp))
                Text(
                    "RINCIAN ORDER (RP)",
                    color = Palette.InkSoft,
                    fontFamily = BodyFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
                Spacer(Modifier.height(8.dp))
                SummaryRow("Harga per keping", rupiah(current.unitPrice))
                SummaryRow("Biaya admin", "Gratis", valueColor = Palette.Good)
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Palette.Line)
                Spacer(Modifier.height(6.dp))
                SummaryRow("Total pembayaran", rupiah(current.total), strong = true)
            }
        }

        if (current != null) {
            Spacer(Modifier.height(12.dp))
            SurfaceCard(tint = Palette.WarnSoft, outline = Palette.WarnBorder) {
                Text(
                    "Estimasi buyback saat ini",
                    fontFamily = BodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Palette.Warn,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Jika dijual kembali sekarang senilai ${rupiah(current.buybackValueNow)} (selisih ${percent(sku.spreadPercent)}). Token dapat dijual 24 jam setelah pembelian.",
                    color = Palette.InkSecondary,
                    fontFamily = BodyFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Metode pembayaran",
            fontFamily = TitleFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Palette.Ink,
        )
        Spacer(Modifier.height(8.dp))
        PaymentMethod.entries.forEach { option ->
            val detail = if (option == PaymentMethod.WALLET) "Saldo tersedia ${rupiah(state.walletBalance)}" else "Konfirmasi otomatis"
            val logoRes = when (option) {
                PaymentMethod.QRIS -> R.drawable.logo_qris
                PaymentMethod.VIRTUAL_ACCOUNT -> R.drawable.logo_bca
                PaymentMethod.WALLET -> null
            }
            PaymentOption(option.label, detail, option == method, logoRes = logoRes) { method = option }
            Spacer(Modifier.height(8.dp))
        }
        if (insufficient) ErrorLine("Saldo tidak mencukupi. Pilih metode lain.")
        Spacer(Modifier.height(6.dp))
    }

    if (askPin && current != null) {
        PinDialog(env, "Konfirmasi Beli Emas senilai ${rupiah(current.total)}", { askPin = false }) {
            askPin = false
            env.attempt { env.repo.createOrder(current.id, method) }?.let { order ->
                if (method == PaymentMethod.WALLET) {
                    env.attempt { env.repo.confirmPayment(order.id) }?.let(onDone)
                } else {
                    pendingOrder = order
                }
            }
        }
    }
    pendingOrder?.let { order ->
        PaymentWaitDialog(
            env,
            order.quote.total,
            order.method,
            order.paymentCode,
            order.payBeforeMs,
            "Batalkan pesanan",
            onCancel = {
                env.repo.cancelOrder(order.id)
                pendingOrder = null
            },
            onConfirm = {
                env.attempt { env.repo.confirmPayment(order.id) }?.let { minted ->
                    pendingOrder = null
                    onDone(minted)
                }
            },
        )
    }
}

@ScreenPreview
@Composable
private fun BuyPreview() = ReidigTheme { BuyScreen(rememberPreviewEnv(), "LMRE-1G", {}, {}) }

/** Menampilkan QR atau nomor VA sesuai layar 15 Menunggu Pembayaran prototipe. */
@Composable
internal fun PaymentWaitDialog(
    env: AppEnv,
    amount: Long,
    method: PaymentMethod,
    paymentCode: String,
    payBeforeMs: Long,
    cancelLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var secondsLeft by remember { mutableLongStateOf(0L) }
    LaunchedEffect(payBeforeMs) {
        while (true) {
            secondsLeft = ((payBeforeMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (secondsLeft == 0L) {
                env.notify("Batas waktu pembayaran habis. Keping dilepas kembali.")
                onCancel()
                break
            }
            delay(1000)
        }
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding().navigationBarsPadding()) {
            TopTitleBar("Menunggu Pembayaran", onCancel)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SurfaceCard(tint = Palette.WarnSoft, outline = Palette.WarnBorder) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Selesaikan pembayaran dalam", color = Palette.Warn, fontFamily = BodyFontFamily, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                            color = Palette.Warn,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SurfaceCard {
                    Text("Total pembayaran", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        rupiah(amount),
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp,
                        color = Palette.Primary,
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Palette.Line)
                    Spacer(Modifier.height(14.dp))
                    if (method == PaymentMethod.QRIS) {
                        Image(
                            painterResource(R.drawable.logo_qris),
                            contentDescription = "QRIS",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(22.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(210.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Palette.Primary50)
                                    .border(1.dp, Palette.Primary100, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.QrCode2, "Kode QRIS", tint = Palette.Primary, modifier = Modifier.size(164.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Pindai QRIS dengan aplikasi mobile banking atau e-wallet.",
                            color = Palette.InkSecondary,
                            fontFamily = BodyFontFamily,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(R.drawable.logo_bca),
                                contentDescription = "BCA",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.height(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Nomor Virtual Account BCA", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            paymentCode,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            color = Palette.Ink,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Transfer sesuai nominal tepat hingga digit terakhir.",
                            color = Palette.InkSecondary,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.5.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Harga dan keping emas ditahan untukmu sampai batas waktu pembayaran berakhir.",
                    color = Palette.InkSoft,
                    fontFamily = BodyFontFamily,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                PrimaryButton("Saya sudah bayar", onConfirm)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(cancelLabel, onCancel)
            }
        }
    }
}

@Composable
internal fun PaymentOption(
    title: String,
    detail: String,
    selected: Boolean,
    logoRes: Int? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) Palette.Primary50 else Color.White)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) Palette.Primary else Palette.Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoRes != null) {
            Box(
                Modifier.width(46.dp).height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(logoRes),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                Modifier.width(46.dp).height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Primary50),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PrototypeIcons.Wallet, null, tint = Palette.Primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(detail, color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.sp)
            }
        }
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Palette.Primary, unselectedColor = Palette.Line),
        )
    }
}

/** Layar hasil bersama untuk transaksi yang berhasil (menggunakan DoubleRingSuccessBadge). */
@Composable
fun SuccessScreen(title: String, message: String, buttonLabel: String, onDone: () -> Unit, details: @Composable () -> Unit = {}) {
    FormScreen("", null, bottom = { PrimaryButton(buttonLabel, onDone) }) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DoubleRingSuccessBadge(PrototypeIcons.Check)
        }
        Spacer(Modifier.height(22.dp))
        Headline(title, message, centered = true, titleColor = Palette.Primary)
        Spacer(Modifier.height(20.dp))
        details()
    }
}
