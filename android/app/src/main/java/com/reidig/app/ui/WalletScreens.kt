package com.reidig.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reidig.app.R
import com.reidig.shared.InstallmentContract
import com.reidig.shared.Rules
import com.reidig.shared.TokenStatus
import com.reidig.shared.Tx
import com.reidig.shared.gram
import com.reidig.shared.rupiah
import java.util.Locale

@Composable
fun WalletScreen(
    env: AppEnv,
    header: @Composable () -> Unit = {},
    onPortfolio: () -> Unit = {},
    onWithdraw: () -> Unit,
) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val sku1g = state.sku("LMRE-1G")
    var hideGold by rememberSaveable { mutableStateOf(false) }
    var hideCash by rememberSaveable { mutableStateOf(false) }
    val weightGrams = state.totalWeightMg / 1000.0
    val weightFormatted = String.format(Locale.forLanguageTag("id-ID"), "%.4f", weightGrams)

    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Dompet", null)
        header()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Dompet Emas Card (matching 20 Dompet prototype)
            item {
                SurfaceCard(onClick = onPortfolio) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GoldBarsBadge(40.dp, 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Dompet Emas", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                                Text(
                                    "${state.ownedTokens.size} keping fisik tersimpan",
                                    color = Palette.InkSecondary,
                                    fontFamily = BodyFontFamily,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Palette.Paper)
                                .clickable { hideGold = !hideGold },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (hideGold) PrototypeIcons.Eye else PrototypeIcons.EyeOff,
                                contentDescription = "Sembunyikan emas",
                                tint = Palette.InkSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (hideGold) "••••••" else weightFormatted,
                            color = Palette.Ink,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 28.sp,
                        )
                        if (!hideGold) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "gr",
                                color = Palette.InkSecondary,
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hideGold) "≈ Rp •••••••" else "≈ ${rupiah(state.portfolioValue)}",
                            color = Palette.InkSecondary,
                            fontFamily = MonoFontFamily,
                            fontSize = 13.sp,
                        )
                        if (!hideGold) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Palette.GoodSoft).padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("▲ Aktif", color = Palette.Good700, fontFamily = BodyFontFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Palette.Line)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Harga buyback 1 gr", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${rupiah(sku1g.buybackPrice)}/gr",
                                color = Palette.Ink,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(PrototypeIcons.ChevronRight, null, tint = Palette.InkSoft, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 2. Dompet Rupiah Card (matching 20 Dompet prototype)
            item {
                SurfaceCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Palette.Primary50),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(PrototypeIcons.Wallet, null, tint = Palette.Primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Dompet Rupiah", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                                Text("Saldo kas hasil penjualan & transaksi", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 11.5.sp)
                            }
                        }
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Palette.Paper)
                                .clickable { hideCash = !hideCash },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (hideCash) PrototypeIcons.Eye else PrototypeIcons.EyeOff,
                                contentDescription = "Sembunyikan kas",
                                tint = Palette.InkSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (hideCash) "Rp •••••••" else rupiah(state.walletBalance),
                        color = Palette.Ink,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Tarik dana ke rekening", onWithdraw, enabled = state.walletBalance > 0)
                    if (!Rules.TOPUP_ENABLED) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip("Isi saldo belum tersedia", ChipTone.NEUTRAL, PrototypeIcons.Lock)
                        }
                    }
                }
            }

            // 3. Rekening Bank Section
            state.bankAccounts.firstOrNull()?.let { account ->
                item {
                    Spacer(Modifier.height(2.dp))
                    Text("Rekening bank", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Ink)
                }
                item {
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BankLogoBadge(account.bank)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(account.bank, fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(50)).background(Palette.GoodSoft).padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text("Utama", color = Palette.Good700, fontFamily = BodyFontFamily, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${account.number} · a.n. ${account.holder}",
                                    color = Palette.InkSecondary,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 4. Mutasi Terbaru Section
            item {
                Spacer(Modifier.height(2.dp))
                Text("Mutasi terbaru", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Ink)
            }
            if (state.transactions.isEmpty()) {
                item {
                    SurfaceCard {
                        Text("Belum ada riwayat transaksi.", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 13.5.sp)
                    }
                }
            }
            items(state.transactions, key = { it.id }) { TxRow(it) }
        }
    }
}

@Composable
internal fun BankLogoBadge(bankName: String) {
    val logoRes = when {
        bankName.contains("BCA", ignoreCase = true) -> R.drawable.logo_bca
        bankName.contains("BRI", ignoreCase = true) -> R.drawable.logo_bri
        bankName.contains("Mandiri", ignoreCase = true) -> R.drawable.logo_mandiri
        bankName.contains("BNI", ignoreCase = true) -> R.drawable.logo_bni
        else -> null
    }
    if (logoRes != null) {
        Box(
            Modifier.width(48.dp).height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(logoRes),
                contentDescription = bankName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                bankName.take(4).uppercase(),
                color = Palette.Gold,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
    }
}

@ScreenPreview
@Composable
private fun WalletPreview() = ReidigTheme { WalletScreen(rememberPreviewEnv()) {} }

@Composable
private fun TxRow(tx: Tx) {
    val isPositive = tx.amount > 0
    val isBuy = tx.title.contains("Beli", ignoreCase = true)
    val (bg, tint, icon) = when {
        isPositive -> Triple(Palette.GoodSoft, Palette.Good, PrototypeIcons.ArrowDownLeft)
        isBuy -> Triple(Palette.GoldSoft, Palette.GoldDeep, PrototypeIcons.Cart)
        else -> Triple(Palette.Primary50, Palette.Primary, PrototypeIcons.ArrowUpRight)
    }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.title, fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Palette.Ink)
                Spacer(Modifier.height(2.dp))
                Text(dateTime(tx.atMs), color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 11.5.sp)
            }
            Spacer(Modifier.width(10.dp))
            if (tx.amount != 0L) {
                Text(
                    (if (tx.amount > 0) "+" else "") + rupiah(tx.amount),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = if (tx.amount > 0) Palette.Good else Palette.Ink,
                )
            }
        }
    }
}

@Composable
fun WithdrawScreen(env: AppEnv, onBack: () -> Unit, onDone: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var askPin by remember { mutableStateOf(false) }
    val amount = input.toLongOrNull() ?: 0L
    val account = state.bankAccounts.firstOrNull()
    val tooMuch = amount > state.walletBalance

    FormScreen(
        "Tarik Dana",
        onBack,
        bottom = { PrimaryButton("Tarik ${if (amount > 0) rupiah(amount) else "dana"}", { askPin = true }, enabled = amount > 0 && !tooMuch && account != null) },
    ) {
        Headline("Tarik ke rekening bank", "Dana cair ke rekening terverifikasi atas nama Anda.", titleColor = Palette.Primary)
        Spacer(Modifier.height(16.dp))
        SurfaceCard(tint = Palette.Primary50, outline = Palette.Primary100) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Saldo Kas tersedia", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        rupiah(state.walletBalance),
                        color = Palette.Primary,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                    )
                }
                TextLink("Tarik semua", { input = state.walletBalance.toString() })
            }
        }
        Spacer(Modifier.height(16.dp))
        FormField(
            "Nominal penarikan (Rp)",
            input,
            { input = it.filter { c -> c.isDigit() }.take(12).trimStart('0') },
            keyboardType = KeyboardType.Number,
            placeholder = "0",
            error = if (tooMuch) "Nominal melebihi Saldo Kas tersedia." else null,
        )
        Spacer(Modifier.height(14.dp))
        // Quick amount chips matching 23 Tarik Dana prototype
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100_000L to "100rb", 250_000L to "250rb", 500_000L to "500rb").forEach { (preset, label) ->
                val active = amount == preset
                Box(
                    Modifier.weight(1f).height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Palette.Primary else Color.White)
                        .border(1.dp, if (active) Palette.Primary else Palette.Line, RoundedCornerShape(50))
                        .clickable { input = preset.coerceAtMost(state.walletBalance).toString() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (active) Color.White else Palette.InkSecondary,
                        fontFamily = BodyFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Rekening tujuan", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        if (account != null) {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BankLogoBadge(account.bank)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${account.bank} · ${account.number}", fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                        Text("a.n. ${account.holder}", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.5.sp)
                    }
                }
            }
        } else {
            Text("Belum ada rekening terverifikasi.", color = Palette.InkSoft, fontFamily = BodyFontFamily)
        }
    }

    if (askPin && account != null) {
        PinDialog(env, "Tarik ${rupiah(amount)} ke ${account.bank} ${account.number}", { askPin = false }) {
            askPin = false
            if (env.attempt { env.repo.withdraw(amount, account.id) } != null) {
                env.notify("Penarikan ${rupiah(amount)} diproses.")
                onDone()
            }
        }
    }
}

@ScreenPreview
@Composable
private fun WithdrawPreview() = ReidigTheme { WithdrawScreen(rememberPreviewEnv(), {}, {}) }

private val TENORS = listOf(3, 6, 12)
private val DOWN_PAYMENT_PERCENTS = listOf(10, 20, 30)

@Composable
fun InstallmentScreen(env: AppEnv, onBack: () -> Unit, onNext: (InstallmentDraft) -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    var skuCode by rememberSaveable { mutableStateOf("LMRE-5G") }
    var tenor by rememberSaveable { mutableStateOf(6) }
    var dpPercent by rememberSaveable { mutableStateOf(20) }
    val sku = state.sku(skuCode)
    val downPayment = sku.buyPrice * dpPercent / 100
    val monthly = (sku.buyPrice - downPayment) / tenor

    FormScreen(
        "Cicil Emas",
        onBack,
        bottom = {
            SummaryRow("Uang muka hari ini", rupiah(downPayment), strong = true)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Lanjut ke perjanjian", { onNext(InstallmentDraft(skuCode, sku.buyPrice, downPayment, tenor)) }, enabled = sku.stock > 0)
        },
    ) {
        Headline(
            "Simulasi Cicil Emas",
            "Keping fisik dikunci di brankas dan otomatis masuk portofolio aktif saat cicilan lunas.",
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(18.dp))
        Text("Pilih pecahan emas", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(state.skus.filter { it.weightMg in 500L..10_000L }.map { it.code to gram(it.weightMg) }, skuCode) { skuCode = it }
        Spacer(Modifier.height(14.dp))
        Text("Uang muka (DP)", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(DOWN_PAYMENT_PERCENTS.map { it to "$it%" }, dpPercent) { dpPercent = it }
        Spacer(Modifier.height(14.dp))
        Text("Tenor cicilan", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(TENORS.map { it to "$it bulan" }, tenor) { tenor = it }
        Spacer(Modifier.height(18.dp))
        SurfaceCard {
            Text(
                "RINGKASAN SIMULASI",
                color = Palette.InkSoft,
                fontFamily = BodyFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(8.dp))
            SummaryRow("Produk", "${sku.product} ${gram(sku.weightMg)}")
            SummaryRow("Harga emas dikunci", rupiah(sku.buyPrice))
            SummaryRow("Uang muka ($dpPercent%)", rupiah(downPayment))
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Palette.Line)
            Spacer(Modifier.height(6.dp))
            SummaryRow("Angsuran per bulan", rupiah(monthly), strong = true, valueColor = Palette.Primary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Rincian akad dan ketentuan pembiayaan akan ditampilkan pada lembar perjanjian.",
            color = Palette.InkSecondary,
            fontFamily = BodyFontFamily,
            fontSize = 12.5.sp,
        )
    }
}

@ScreenPreview
@Composable
private fun InstallmentPreview() = ReidigTheme { InstallmentScreen(rememberPreviewEnv(), {}, {}) }

@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier.weight(1f).height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Palette.Primary else Color.White)
                    .border(1.dp, if (active) Palette.Primary else Palette.Line, RoundedCornerShape(50))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontFamily = BodyFontFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else Palette.InkSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AccountScreen(env: AppEnv, onVerify: () -> Unit, onInstallments: () -> Unit, onLogout: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val user = state.user
    val initials = (user?.name ?: "P").trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase() }

    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Profil", null)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(Palette.Primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initials, color = Palette.Gold, fontFamily = TitleFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user?.name ?: "", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.Ink)
                            Text(user?.email ?: "", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            if (user?.verified == true) {
                                StatusChip("e-KYC Terverifikasi", ChipTone.GOOD)
                            } else {
                                StatusChip("Belum terverifikasi", ChipTone.WARN)
                            }
                        }
                    }
                    if (user?.verified != true) {
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Verifikasi identitas sekarang", onVerify)
                    }
                }
            }
            state.bankAccounts.firstOrNull()?.let { account ->
                item {
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BankLogoBadge(account.bank)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Rekening bank terdaftar", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                                Text(
                                    "${account.bank} · ${account.number}",
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Palette.Ink,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SurfaceCard(onClick = onInstallments) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(Palette.GoldSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(PrototypeIcons.Calendar, null, tint = Palette.GoldDeep, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ajukan cicilan emas", fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                            Text("Kunci harga emas hari ini, bayar bertahap", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.sp)
                        }
                        Icon(PrototypeIcons.ChevronRight, null, tint = Palette.InkSoft, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (state.contracts.isNotEmpty()) {
                item { Text("Kontrak cicilan aktif", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Ink) }
                items(state.contracts, key = { it.id }) { contract -> ContractCard(env, contract) }
            }
            item {
                Spacer(Modifier.height(4.dp))
                DestructiveButton("Keluar dari akun", onLogout)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Goldin · Raja Emas Indonesia · v1.0.0",
                    color = Palette.InkSoft,
                    fontFamily = BodyFontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@ScreenPreview
@Composable
private fun AccountPreview() = ReidigTheme { AccountScreen(rememberPreviewEnv(), {}, {}, {}) }

@Composable
private fun ContractCard(env: AppEnv, contract: InstallmentContract) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val token = state.tokens.firstOrNull { it.code == contract.tokenCode }
    val locked = token?.status == TokenStatus.LOCKED_INSTALLMENT
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(contract.id, fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            if (locked) StatusChip("Berjalan", ChipTone.WARN, PrototypeIcons.Lock) else StatusChip("Lunas", ChipTone.GOOD)
        }
        Spacer(Modifier.height(6.dp))
        token?.let { Text("${state.sku(it.skuCode).product} ${gram(state.sku(it.skuCode).weightMg)}", fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        Text("${rupiah(contract.monthlyBeforeFees)} / bulan · ${contract.paidMonths} dari ${contract.tenorMonths} bulan", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(contract.partner, color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
        Text("Akad ${contract.agreementVersion} · ${dateTime(contract.agreedAtMs)}", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 11.5.sp)
    }
}
