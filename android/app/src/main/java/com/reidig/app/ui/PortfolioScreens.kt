package com.reidig.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reidig.shared.GoldToken
import com.reidig.shared.PrintMethod
import com.reidig.shared.PrintOrder
import com.reidig.shared.PrintStatus
import com.reidig.shared.Rules
import com.reidig.shared.TokenStatus
import com.reidig.shared.gram
import com.reidig.shared.rupiah

@Composable
private fun TokenStatusChip(token: GoldToken, now: Long) {
    when (token.status) {
        TokenStatus.ACTIVE ->
            if (Rules.canSellAt(token, now)) StatusChip("Aktif", ChipTone.GOOD)
            else StatusChip("Masa tahan 24 jam", ChipTone.GOLD, PrototypeIcons.Lock)
        TokenStatus.LOCKED_INSTALLMENT -> StatusChip("Terkunci cicilan", ChipTone.WARN, PrototypeIcons.Lock)
        TokenStatus.PRINT_PENDING -> StatusChip("Sedang dicetak", ChipTone.NEUTRAL, PrototypeIcons.GoldIngots)
        else -> StatusChip("Selesai", ChipTone.NEUTRAL)
    }
}

@Composable
fun PortfolioScreen(env: AppEnv, onToken: (String) -> Unit, onPrint: () -> Unit, onBuy: () -> Unit, header: @Composable () -> Unit = {}) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Dompet", null)
        header()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HeroPurpleCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Total emas lunas", color = Palette.Primary100, fontFamily = BodyFontFamily, fontSize = 12.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            gram(state.totalWeightMg),
                            color = Palette.Gold,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 28.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Nilai buyback ≈ ${rupiah(state.portfolioValue)}",
                            color = Palette.Primary100,
                            fontFamily = MonoFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            if (state.openTokens.isEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Headline("Belum ada keping emas", "Mulai dari pecahan 0,1 gr.", centered = true, titleColor = Palette.Primary)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Beli emas sekarang", onBuy)
                }
            }
            items(state.openTokens, key = { it.code }) { token ->
                val sku = state.sku(token.skuCode)
                SurfaceCard(onClick = { onToken(token.code) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Palette.GoldSoft)
                                .border(1.dp, Palette.Gold200, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                gram(sku.weightMg).removeSuffix(" gr"),
                                color = Palette.GoldDeep,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${sku.product} ${gram(sku.weightMg)}", fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                            Text("Seri ${token.serial}", color = Palette.InkSecondary, fontFamily = MonoFontFamily, fontSize = 12.sp)
                        }
                        TokenStatusChip(token, now)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Palette.Line.copy(alpha = 0.6f))
                    Spacer(Modifier.height(6.dp))
                    TokenCodeText(token.code)
                }
            }
            if (state.printOrders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Pesanan cetak fisik", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Ink)
                }
                items(state.printOrders, key = { it.id }) { order -> PrintOrderCard(order) }
            }
            if (state.openTokens.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton("Cetak fisik emas", onPrint)
                }
            }
        }
    }
}

@ScreenPreview
@Composable
private fun PortfolioPreview() = ReidigTheme { PortfolioScreen(rememberPreviewEnv(), {}, {}, {}) }

@ScreenPreview
@Composable
private fun PortfolioEmptyPreview() = ReidigTheme { PortfolioScreen(rememberPreviewEnv(false), {}, {}, {}) }

@Composable
fun PrintOrderCard(order: PrintOrder) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.id, fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            StatusChip(order.status.label, if (order.status == PrintStatus.DONE) ChipTone.GOOD else ChipTone.NEUTRAL, PrototypeIcons.GoldIngots)
        }
        Spacer(Modifier.height(4.dp))
        Text(if (order.method == PrintMethod.JNE) "JNE ke ${order.destination}" else "Ambil di ${order.destination}", fontFamily = BodyFontFamily, fontSize = 13.5.sp, color = Palette.Ink)
        Text("Estimasi ${order.estimate}", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.5.sp)
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PrintOrderCardPreview() = ReidigTheme {
    Column(Modifier.background(Palette.Paper).padding(16.dp)) {
        PrintOrderCard(
            PrintOrder("CTK-1", listOf("RET-LMRE1G-1"), PrintMethod.BRANCH, "Cabang Kelapa Gading", PrintStatus.IN_TRANSIT, "2–3 hari kerja", 0),
        )
    }
}

@Composable
fun TokenDetailScreen(env: AppEnv, code: String, onBack: () -> Unit, onSold: () -> Unit, onPrint: (String) -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val token = state.tokens.firstOrNull { it.code == code } ?: return
    val sku = state.sku(token.skuCode)
    val now = System.currentTimeMillis()
    var confirmSell by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    val canSell = Rules.canSellAt(token, now)
    val difference = sku.buybackPrice - token.acquiredPrice

    FormScreen(
        "Detail Token Emas",
        onBack,
        bottom = if (token.status == TokenStatus.ACTIVE) {
            {
                PrimaryButton("Jual ${rupiah(sku.buybackPrice)}", { confirmSell = true }, enabled = canSell)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Cetak fisik keping ini", { onPrint(token.code) })
            }
        } else {
            null
        },
    ) {
        Text(
            "${sku.product} ${gram(sku.weightMg)}",
            fontFamily = TitleFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = Palette.Primary,
        )
        Spacer(Modifier.height(8.dp))
        TokenStatusChip(token, now)
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            Text("Kode token", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
            TokenCodeText(token.code)
            Spacer(Modifier.height(10.dp))
            Text("Nomor seri keping", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
            TokenCodeText(token.serial)
            Spacer(Modifier.height(10.dp))
            Text("Waktu perolehan", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
            Text(dateTime(token.acquiredAtMs), fontFamily = BodyFontFamily, fontSize = 13.5.sp, color = Palette.Ink, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard {
            SummaryRow("Harga perolehan", rupiah(token.acquiredPrice))
            SummaryRow("Harga buyback saat ini", rupiah(sku.buybackPrice))
            HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(vertical = 6.dp))
            SummaryRow("Selisih", rupiah(difference), strong = true, valueColor = if (difference < 0) Palette.Critical else Palette.Good)
        }
        Spacer(Modifier.height(12.dp))
        when {
            token.status == TokenStatus.LOCKED_INSTALLMENT -> ErrorLine("Token terkunci sampai cicilan lunas.", color = Palette.Warn)
            token.status == TokenStatus.PRINT_PENDING -> Text("Keping sedang diproses untuk dicetak.", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 13.sp)
            !canSell -> ErrorLine("Token baru bisa dijual 24 jam setelah pembelian.", color = Palette.Warn)
        }
    }

    if (confirmSell) {
        AlertDialog(
            onDismissRequest = { confirmSell = false },
            title = { Text("Jual token ini?", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Keping ini akan dilepas dari portofolio Anda, dan dananya langsung masuk ke Saldo Kas.",
                        fontFamily = BodyFontFamily,
                    )
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Anda menerima", rupiah(sku.buybackPrice), strong = true, valueColor = Palette.Primary)
                    if (sku.buybackPrice >= Rules.TAX_FREE_SELL_LIMIT) {
                        Text(
                            "Nilai Rp 10 juta atau lebih bisa dikenai pajak sesuai ketentuan.",
                            color = Palette.Warn,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.5.sp,
                        )
                    }
                }
            },
            confirmButton = { TextLink("Lanjut", { confirmSell = false; askPin = true }) },
            dismissButton = { TextLink("Batal", { confirmSell = false }, color = Palette.InkSecondary) },
        )
    }
    if (askPin) {
        PinDialog(env, "Konfirmasi Jual Emas senilai ${rupiah(sku.buybackPrice)}", { askPin = false }) {
            askPin = false
            env.attempt { env.repo.sell(token.code) }?.let {
                env.notify("${rupiah(it)} masuk ke Saldo Kas.")
                onSold()
            }
        }
    }
}

@ScreenPreview
@Composable
private fun TokenDetailPreview() = ReidigTheme {
    val env = rememberPreviewEnv()
    TokenDetailScreen(env, env.firstToken.code, {}, {}, {})
}

@Composable
fun SellPickerScreen(env: AppEnv, onBack: (() -> Unit)?, onToken: (String) -> Unit, title: String = "Jual Emas", header: @Composable () -> Unit = {}) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val sku1g = state.sku("LMRE-1G")
    val now = System.currentTimeMillis()
    val tokens = state.openTokens.filter { it.status == TokenStatus.ACTIVE }
    FormScreen(title, onBack) {
        header()
        // Summary card matching 13 Transaksi (Jual mode) prototype
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Saldo emas aktif", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        gram(state.totalWeightMg),
                        color = Palette.Ink,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Harga jual / gr", color = Palette.InkSoft, fontFamily = BodyFontFamily, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        rupiah(sku1g.buybackPrice),
                        color = Palette.Primary,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Headline("Pilih keping yang dijual", "Dana hasil penjualan otomatis masuk ke Saldo Kas.", titleColor = Palette.Primary)
        Spacer(Modifier.height(14.dp))
        if (tokens.isEmpty()) {
            SurfaceCard {
                Text("Belum ada token emas aktif yang bisa dijual.", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 13.5.sp)
            }
        }
        tokens.forEach { token ->
            val sku = state.sku(token.skuCode)
            SurfaceCard(Modifier.padding(bottom = 10.dp), onClick = { onToken(token.code) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.GoldSoft)
                            .border(1.dp, Palette.Gold200, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            gram(sku.weightMg).removeSuffix(" gr"),
                            color = Palette.GoldDeep,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${sku.product} ${gram(sku.weightMg)}", fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                        Text(
                            rupiah(sku.buybackPrice),
                            color = Palette.Primary,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    TokenStatusChip(token, now)
                }
            }
        }
    }
}

@ScreenPreview
@Composable
private fun SellPickerPreview() = ReidigTheme { SellPickerScreen(rememberPreviewEnv(), {}, {}) }

@Composable
fun PrintScreen(env: AppEnv, initialToken: String?, onBack: (() -> Unit)?, onDone: (PrintOrder) -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val tokens = state.openTokens.filter { it.status == TokenStatus.ACTIVE }
    var selected by remember { mutableStateOf(setOfNotNull(initialToken)) }
    var method by rememberSaveable { mutableStateOf(PrintMethod.JNE) }
    var address by rememberSaveable { mutableStateOf("") }
    var branchId by rememberSaveable { mutableStateOf<String?>(null) }
    var askPin by remember { mutableStateOf(false) }

    val totalMg = tokens.filter { it.code in selected }.sumOf { state.sku(it.skuCode).weightMg }
    val pickupAllowed = Rules.isPickupEligible(totalMg)
    val effectiveMethod = if (method == PrintMethod.BRANCH && !pickupAllowed) PrintMethod.JNE else method
    val ready = selected.isNotEmpty() && when (effectiveMethod) {
        PrintMethod.JNE -> address.isNotBlank()
        PrintMethod.BRANCH -> branchId != null
    }

    FormScreen(
        "Cetak Emas Fisik",
        onBack,
        bottom = {
            if (selected.isNotEmpty()) {
                SummaryRow("Total dicetak", "${gram(totalMg)} · ${selected.size} keping", strong = true, valueColor = Palette.Primary)
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton("Konfirmasi cetak fisik", { askPin = true }, enabled = ready)
        },
    ) {
        Headline("Pilih keping emas", "Keping fisik 24K bersertifikat dikirim dari brankas.", titleColor = Palette.Primary)
        Spacer(Modifier.height(14.dp))
        if (tokens.isEmpty()) {
            SurfaceCard {
                Text("Belum ada token aktif yang bisa dicetak.", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 13.5.sp)
            }
        }
        tokens.forEach { token ->
            val sku = state.sku(token.skuCode)
            val checked = token.code in selected
            SelectableRow(checked, { selected = if (checked) selected - token.code else selected + token.code }) {
                Text("${sku.product} ${gram(sku.weightMg)}", fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                Text("Seri ${token.serial}", color = Palette.InkSecondary, fontFamily = MonoFontFamily, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Metode penerimaan", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        SelectableRow(effectiveMethod == PrintMethod.JNE, { method = PrintMethod.JNE }) {
            Text("Dikirim dengan kurir berasuransi (JNE)", fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
            Text("Dikirim langsung ke alamat rumah Anda.", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.5.sp)
        }
        SelectableRow(effectiveMethod == PrintMethod.BRANCH, { if (pickupAllowed) method = PrintMethod.BRANCH }, enabled = pickupAllowed) {
            Text(
                "Ambil di cabang Raja Emas",
                fontFamily = BodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (pickupAllowed) Palette.Ink else Palette.DisabledText,
            )
            Text(
                if (pickupAllowed) "Dikirim dulu dari gudang Jakarta. Kami kabari saat siap diambil."
                else "Minimal total ${gram(Rules.PICKUP_MIN_WEIGHT_MG)} per pesanan. Saat ini ${gram(totalMg)}.",
                color = Palette.InkSecondary,
                fontFamily = BodyFontFamily,
                fontSize = 12.5.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (effectiveMethod == PrintMethod.JNE) {
            FormField("Alamat pengiriman lengkap", address, { address = it }, placeholder = "Jalan, nomor, kecamatan, kota, kode pos", singleLine = false, keyboardType = KeyboardType.Text)
        } else {
            Text("Pilih cabang pengambilan", fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.Ink)
            Spacer(Modifier.height(8.dp))
            env.repo.branches.forEach { branch ->
                SelectableRow(branchId == branch.id, { branchId = branch.id }) {
                    Text(branch.name, fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                    Text("${branch.area} · estimasi ${branch.estimate}", color = Palette.InkSecondary, fontFamily = BodyFontFamily, fontSize = 12.5.sp)
                }
            }
        }
    }

    if (askPin) {
        PinDialog(env, "Cetak fisik ${gram(totalMg)} (${selected.size} keping)", { askPin = false }) {
            askPin = false
            env.attempt {
                env.repo.createPrintOrder(selected.toList(), effectiveMethod, address, branchId)
            }?.let(onDone)
        }
    }
}

@ScreenPreview
@Composable
private fun PrintPreview() = ReidigTheme { PrintScreen(rememberPreviewEnv(), null, {}, {}) }

@Composable
private fun SelectableRow(selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(shape)
            .background(if (selected) Palette.Primary50 else Color.White)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) Palette.Primary else Palette.Line, shape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(selected, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Palette.Primary, uncheckedColor = Palette.Line))
        Spacer(Modifier.width(10.dp))
        Column { content() }
    }
}
