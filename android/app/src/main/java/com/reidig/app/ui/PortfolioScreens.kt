package com.reidig.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
            else StatusChip("Masa tahan 24 jam", ChipTone.GOLD, Icons.Outlined.Lock)
        TokenStatus.LOCKED_INSTALLMENT -> StatusChip("Terkunci cicilan", ChipTone.WARN, Icons.Outlined.Lock)
        TokenStatus.PRINT_PENDING -> StatusChip("Sedang dicetak", ChipTone.NEUTRAL, Icons.Outlined.Inventory2)
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SurfaceCard(tint = Palette.Lavender) {
                    Text("Total emas lunas", color = Palette.InkSoft, fontSize = 13.sp)
                    Text(gram(state.totalWeightMg), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("Nilai buyback ${rupiah(state.portfolioValue)}", color = Palette.InkSoft, fontSize = 14.sp)
                }
            }
            if (state.openTokens.isEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Headline("Belum ada emas", "Mulai dari 0,1 gr.", centered = true)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Beli emas", onBuy)
                }
            }
            items(state.openTokens, key = { it.code }) { token ->
                val sku = state.sku(token.skuCode)
                SurfaceCard(onClick = { onToken(token.code) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${sku.product} ${gram(sku.weightMg)}", fontWeight = FontWeight.SemiBold)
                            Text("Seri ${token.serial}", color = Palette.InkSoft, fontSize = 13.sp)
                        }
                        TokenStatusChip(token, now)
                    }
                    Spacer(Modifier.height(4.dp))
                    TokenCodeText(token.code)
                }
            }
            if (state.printOrders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text("Pesanan cetak", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                items(state.printOrders, key = { it.id }) { order -> PrintOrderCard(order) }
            }
            if (state.openTokens.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton("Cetak emas", onPrint)
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
            Text(order.id, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusChip(order.status.label, if (order.status == PrintStatus.DONE) ChipTone.GOOD else ChipTone.NEUTRAL, Icons.Outlined.Inventory2)
        }
        Spacer(Modifier.height(4.dp))
        Text(if (order.method == PrintMethod.JNE) "JNE ke ${order.destination}" else "Ambil di ${order.destination}", fontSize = 14.sp)
        Text("Estimasi ${order.estimate}", color = Palette.InkSoft, fontSize = 13.sp)
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PrintOrderCardPreview() = ReidigTheme {
    Column(Modifier.background(Color.White).padding(16.dp)) {
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
        "Detail Token",
        onBack,
        bottom = if (token.status == TokenStatus.ACTIVE) {
            {
                PrimaryButton("Jual ${rupiah(sku.buybackPrice)}", { confirmSell = true }, enabled = canSell)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Cetak keping ini", { onPrint(token.code) })
            }
        } else {
            null
        },
    ) {
        Text("${sku.product} ${gram(sku.weightMg)}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        TokenStatusChip(token, now)
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            Text("Kode token", color = Palette.InkSoft, fontSize = 13.sp)
            TokenCodeText(token.code)
            Spacer(Modifier.height(10.dp))
            Text("Nomor seri keping", color = Palette.InkSoft, fontSize = 13.sp)
            TokenCodeText(token.serial)
            Spacer(Modifier.height(10.dp))
            Text("Dibeli", color = Palette.InkSoft, fontSize = 13.sp)
            Text(dateTime(token.acquiredAtMs), fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard {
            SummaryRow("Harga beli", rupiah(token.acquiredPrice))
            SummaryRow("Harga buyback saat ini", rupiah(sku.buybackPrice))
            SummaryRow("Selisih", rupiah(difference), strong = true, valueColor = if (difference < 0) Palette.Critical else Palette.Good)
        }
        Spacer(Modifier.height(12.dp))
        when {
            token.status == TokenStatus.LOCKED_INSTALLMENT -> ErrorLine("Token terkunci sampai cicilan lunas.", color = Palette.Warn)
            token.status == TokenStatus.PRINT_PENDING -> Text("Keping sedang diproses untuk dicetak.", color = Palette.InkSoft)
            !canSell -> ErrorLine("Token baru bisa dijual 24 jam setelah pembelian.", color = Palette.Warn)
        }
    }

    if (confirmSell) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSell = false },
            title = { Text("Jual token ini?") },
            text = {
                Column {
                    Text("Keping ini tidak lagi menjadi milik Anda, dan dananya masuk ke saldo.")
                    Spacer(Modifier.height(10.dp))
                    SummaryRow("Anda menerima", rupiah(sku.buybackPrice), strong = true)
                    if (sku.buybackPrice >= Rules.TAX_FREE_SELL_LIMIT) {
                        Text("Nilai Rp 10 juta atau lebih bisa dikenai pajak sesuai ketentuan.", color = Palette.Warn, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextLink("Lanjut", { confirmSell = false; askPin = true }) },
            dismissButton = { TextLink("Batal", { confirmSell = false }, color = Palette.InkSoft) },
        )
    }
    if (askPin) {
        PinDialog(env, "Jual ${rupiah(sku.buybackPrice)}", { askPin = false }) {
            askPin = false
            env.attempt { env.repo.sell(token.code) }?.let {
                env.notify("${rupiah(it)} masuk ke saldo.")
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
    val now = System.currentTimeMillis()
    val tokens = state.openTokens.filter { it.status == TokenStatus.ACTIVE }
    FormScreen(title, onBack) {
        header()
        Headline("Pilih token yang dijual", "Dana masuk ke saldo.")
        Spacer(Modifier.height(16.dp))
        if (tokens.isEmpty()) Text("Belum ada token yang bisa dijual.", color = Palette.InkSoft)
        tokens.forEach { token ->
            val sku = state.sku(token.skuCode)
            SurfaceCard(Modifier.padding(bottom = 10.dp), onClick = { onToken(token.code) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sku.product} ${gram(sku.weightMg)}", fontWeight = FontWeight.SemiBold)
                        Text(rupiah(sku.buybackPrice), color = Palette.Primary, fontWeight = FontWeight.Bold)
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
        "Cetak Emas",
        onBack,
        bottom = {
            if (selected.isNotEmpty()) {
                SummaryRow("Total dicetak", "${gram(totalMg)} · ${selected.size} keping", strong = true)
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton("Lanjut", { askPin = true }, enabled = ready)
        },
    ) {
        Text("Pilih keping", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        if (tokens.isEmpty()) Text("Belum ada token yang bisa dicetak.", color = Palette.InkSoft)
        tokens.forEach { token ->
            val sku = state.sku(token.skuCode)
            val checked = token.code in selected
            SelectableRow(checked, { selected = if (checked) selected - token.code else selected + token.code }) {
                Text("${sku.product} ${gram(sku.weightMg)}", fontWeight = FontWeight.SemiBold)
                Text("Seri ${token.serial}", color = Palette.InkSoft, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Cara menerima", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        SelectableRow(effectiveMethod == PrintMethod.JNE, { method = PrintMethod.JNE }) {
            Text("Dikirim dengan JNE", fontWeight = FontWeight.SemiBold)
            Text("Dikirim ke alamat Anda.", color = Palette.InkSoft, fontSize = 13.sp)
        }
        SelectableRow(effectiveMethod == PrintMethod.BRANCH, { if (pickupAllowed) method = PrintMethod.BRANCH }, enabled = pickupAllowed) {
            Text("Ambil di cabang", fontWeight = FontWeight.SemiBold, color = if (pickupAllowed) Palette.Ink else Palette.DisabledText)
            Text(
                if (pickupAllowed) "Dikirim dulu dari gudang Jakarta. Kami kabari saat bisa diambil."
                else "Minimal total ${gram(Rules.PICKUP_MIN_WEIGHT_MG)} per pesanan. Saat ini ${gram(totalMg)}.",
                color = Palette.InkSoft,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (effectiveMethod == PrintMethod.JNE) {
            FormField("Alamat pengiriman", address, { address = it }, placeholder = "Jalan, nomor, kota, kode pos", singleLine = false, keyboardType = KeyboardType.Text)
        } else {
            Text("Pilih cabang", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            env.repo.branches.forEach { branch ->
                SelectableRow(branchId == branch.id, { branchId = branch.id }) {
                    Text(branch.name, fontWeight = FontWeight.SemiBold)
                    Text("${branch.area} · estimasi ${branch.estimate}", color = Palette.InkSoft, fontSize = 13.sp)
                }
            }
        }
    }

    if (askPin) {
        PinDialog(env, "Cetak ${gram(totalMg)}", { askPin = false }) {
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
            .cardDepth(if (selected) Palette.Lavender else Color.White, shape, if (selected) Palette.Primary else null)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(selected, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Palette.Primary, uncheckedColor = Palette.Primary))
        Spacer(Modifier.width(10.dp))
        Column { content() }
    }
}
