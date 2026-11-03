package com.reidig.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.reidig.shared.Quote
import com.reidig.shared.Sku
import com.reidig.shared.gram
import com.reidig.shared.percent
import com.reidig.shared.rupiah
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    env: AppEnv,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onPrint: () -> Unit,
    onInstallment: () -> Unit,
    onWithdraw: () -> Unit,
    onVerify: () -> Unit,
    onPrices: () -> Unit,
) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Palette.Paper).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(NightGradient).statusBarsPadding().padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(38)
                Spacer(Modifier.width(10.dp))
                Text("REIDIG", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text("Halo, ${state.user?.name?.substringBefore(' ') ?: ""}", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text("Nilai emas Anda", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Text(rupiah(state.portfolioValue), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            Text("${gram(state.totalWeightMg)} · ${state.ownedTokens.size} token", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(14.dp)) {
                HomePriceRow("1 gr", state.sku("LMRE-1G"))
                Spacer(Modifier.height(8.dp))
                Text("Diperbarui ${state.priceUpdatedAtLabel}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        Column(Modifier.padding(20.dp)) {
            if (state.user?.verified == false) {
                SurfaceCard(tint = Palette.GoldSoft, onClick = onVerify) {
                    Text("Verifikasi identitas sebelum membeli", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction("Beli", Icons.Outlined.ShoppingBag, onBuy)
                QuickAction("Jual", Icons.Outlined.Sell, onSell)
                QuickAction("Harga", Icons.Outlined.TableChart, onPrices)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction("Cetak", Icons.Outlined.Inventory2, onPrint)
                QuickAction("Cicil", Icons.Outlined.CalendarMonth, onInstallment)
                QuickAction("Tarik", Icons.Outlined.AccountBalanceWallet, onWithdraw)
            }
            Spacer(Modifier.height(20.dp))
            SurfaceCard(onClick = onWithdraw) {
                Text("Saldo REIDIG", color = Palette.InkSoft, fontSize = 13.sp)
                Text(rupiah(state.walletBalance), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.height(16.dp))
            Image(
                painterResource(R.drawable.banner_jual_emas),
                contentDescription = "Jual emas paling transparan dengan harga tinggi di Raja Emas Indonesia",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}

@ScreenPreview
@Composable
private fun HomePreview() = ReidigTheme { HomeScreen(rememberPreviewEnv(), {}, {}, {}, {}, {}, {}, {}) }

@ScreenPreview
@Composable
private fun HomeUnverifiedPreview() = ReidigTheme { HomeScreen(rememberPreviewEnv(false), {}, {}, {}, {}, {}, {}, {}) }

@Composable
private fun HomePriceRow(weightLabel: String, sku: Sku) {
    Text(weightLabel, color = Palette.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(Modifier.height(2.dp))
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Harga beli", color = Color.White.copy(alpha = 0.75f), fontSize = 12.5.sp)
            Text(rupiah(sku.buyPrice), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("Harga jual", color = Color.White.copy(alpha = 0.75f), fontSize = 12.5.sp)
            Text(rupiah(sku.buybackPrice), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Harga beli dan harga jual (buyback) semua pecahan. */
@Composable
fun PriceTableScreen(env: AppEnv, onBack: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Tabel Harga Emas", onBack)
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp)) {
            item {
                Text("Diperbarui ${state.priceUpdatedAtLabel}", color = Palette.InkSoft, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Spacer(Modifier.height(12.dp))
                PriceTableRow("Berat", "Harga beli", "Harga jual", header = true)
                HorizontalDivider(color = Palette.Line)
            }
            items(state.skus, key = { it.code }) { sku ->
                PriceTableRow(gram(sku.weightMg), rupiah(sku.buyPrice), rupiah(sku.buybackPrice))
                HorizontalDivider(color = Palette.Ring)
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text(state.skus.firstOrNull()?.product ?: "", color = Palette.InkSoft, fontSize = 13.sp)
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
    val fontSize = if (header) 12.5.sp else 13.5.sp
    val style = TextStyle(fontFeatureSettings = "tnum")
    Row(Modifier.fillMaxWidth().padding(vertical = if (header) 8.dp else 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(weight, color = color, fontSize = fontSize, fontWeight = if (header) FontWeight.Medium else FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        Text(buy, color = color, fontSize = fontSize, style = style, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
        Text(sell, color = color, fontSize = fontSize, style = style, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Palette.Ring), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CatalogScreen(env: AppEnv, onSelect: (String) -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Beli Emas", null)
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(2.dp))
            }
            items(state.skus, key = { it.code }) { sku ->
                SurfaceCard(onClick = { onSelect(sku.code) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Palette.GoldSoft), contentAlignment = Alignment.Center) {
                            Text(gram(sku.weightMg).removeSuffix(" gr"), color = Palette.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${sku.product} ${gram(sku.weightMg)}", fontWeight = FontWeight.SemiBold)
                            Text(rupiah(sku.buyPrice), fontWeight = FontWeight.Bold, color = Palette.Primary)
                        }
                        if (sku.stock == 0) {
                            StatusChip("Stok habis", ChipTone.WARN, Icons.Outlined.WarningAmber)
                        } else {
                            Text("Stok ${sku.stock}", color = Palette.InkSoft, fontSize = 12.5.sp)
                        }
                    }
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
    // LaunchedEffect tidak berjalan di preview statis, jadi harga diisi di sini.
    val inPreview = LocalInspectionMode.current
    var quote by remember { mutableStateOf(if (inPreview) env.repo.quoteBuy(skuCode, 1) else null) }
    var askPin by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableLongStateOf(0L) }
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
        "Beli ${gram(sku.weightMg)}",
        onBack,
        bottom = {
            if (current != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Harga dikunci", color = Palette.InkSoft, fontSize = 13.sp)
                    Text("0:${secondsLeft.coerceAtLeast(0).toString().padStart(2, '0')}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            PrimaryButton(
                "Bayar ${current?.let { rupiah(it.total) } ?: ""}",
                { askPin = true },
                enabled = current != null && !insufficient && sku.stock > 0,
            )
        },
    ) {
        Text("${sku.product} ${gram(sku.weightMg)}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text("Stok ${sku.stock} keping", color = Palette.InkSoft, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Jumlah keping", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (quantity > 1) quantity-- }, enabled = quantity > 1) { Icon(Icons.Filled.Remove, "Kurangi") }
            Text("$quantity", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { if (quantity < sku.stock) quantity++ }, enabled = quantity < sku.stock) { Icon(Icons.Filled.Add, "Tambah") }
        }
        Spacer(Modifier.height(10.dp))
        if (current != null) {
            SurfaceCard {
                SummaryRow("Harga per keping", rupiah(current.unitPrice))
                SummaryRow("Jumlah", "$quantity keping")
                SummaryRow("Total", rupiah(current.total), strong = true)
            }
            Spacer(Modifier.height(12.dp))
            SurfaceCard(tint = Palette.WarnSoft) {
                Text("Kalau dijual lagi sekarang", fontWeight = FontWeight.Bold, color = Palette.Warn)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Anda menerima ${rupiah(current.buybackValueNow)}, ${percent(sku.spreadPercent)} di bawah harga beli. Token bisa dijual 24 jam setelah dibeli.",
                    color = Palette.Ink,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Metode pembayaran", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        PaymentMethod.entries.forEach { option ->
            val detail = if (option == PaymentMethod.WALLET) "Tersedia ${rupiah(state.walletBalance)}" else ""
            PaymentOption(option.label, detail, option == method) { method = option }
            Spacer(Modifier.height(8.dp))
        }
        if (insufficient) ErrorLine("Saldo tidak mencukupi. Pilih metode lain.")
        Spacer(Modifier.height(6.dp))
    }

    if (askPin && current != null) {
        PinDialog(env, "Bayar ${rupiah(current.total)}", { askPin = false }) {
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

/** Menampilkan QR atau nomor VA dari iFortepay sampai callback pembayaran masuk. */
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
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding()) {
            TopTitleBar("Menunggu Pembayaran", onCancel)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rupiah(amount), fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bayar dalam ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                    color = Palette.InkSoft,
                )
                Spacer(Modifier.height(20.dp))
                if (method == PaymentMethod.QRIS) {
                    Box(
                        Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Lavender).border(1.dp, Palette.Ring, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.QrCode2, "Kode QRIS", tint = Palette.Primary, modifier = Modifier.size(170.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Pindai dengan aplikasi bank atau e-wallet.", color = Palette.InkSoft, textAlign = TextAlign.Center)
                } else {
                    SurfaceCard(tint = Palette.Lavender) {
                        Text("Nomor Virtual Account BCA", color = Palette.InkSoft, fontSize = 13.sp)
                        Text(paymentCode, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Transfer sesuai nominal dari ATM atau aplikasi bank.", color = Palette.InkSoft, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Text("Harga dan keping ditahan sampai batas waktu habis.", color = Palette.InkSoft, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Column(Modifier.padding(20.dp)) {
                PrimaryButton("Saya sudah bayar", onConfirm)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(cancelLabel, onCancel)
            }
        }
    }
}

@Composable
internal fun PaymentOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) Palette.Lavender else Color.White)
            .border(1.dp, if (selected) Palette.Primary else Palette.Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = Palette.Primary))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (detail.isNotEmpty()) Text(detail, color = Palette.InkSoft, fontSize = 13.sp)
        }
    }
}

/** Layar hasil bersama untuk transaksi yang berhasil. */
@Composable
fun SuccessScreen(title: String, message: String, buttonLabel: String, onDone: () -> Unit, details: @Composable () -> Unit = {}) {
    FormScreen(title, null, bottom = { PrimaryButton(buttonLabel, onDone) }) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Palette.GoodSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Palette.Good, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Headline(title, message, centered = true)
        Spacer(Modifier.height(20.dp))
        details()
    }
}
