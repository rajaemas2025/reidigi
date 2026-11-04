package com.reidig.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reidig.shared.InstallmentContract
import com.reidig.shared.Rules
import com.reidig.shared.TokenStatus
import com.reidig.shared.Tx
import com.reidig.shared.gram
import com.reidig.shared.rupiah

@Composable
fun WalletScreen(env: AppEnv, header: @Composable () -> Unit = {}, onWithdraw: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Dompet", null)
        header()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NightGradient).padding(20.dp)) {
                    Text("Saldo REIDIG", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    Text(rupiah(state.walletBalance), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                    Spacer(Modifier.height(16.dp))
                    LightButton("Tarik dana", onWithdraw, enabled = state.walletBalance > 0)
                }
            }
            if (!Rules.TOPUP_ENABLED) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip("Isi saldo belum tersedia", ChipTone.NEUTRAL, Icons.Outlined.Lock)
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text("Riwayat transaksi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            if (state.transactions.isEmpty()) {
                item { Text("Belum ada transaksi.", color = Palette.InkSoft) }
            }
            items(state.transactions, key = { it.id }) { TxRow(it) }
        }
    }
}

@ScreenPreview
@Composable
private fun WalletPreview() = ReidigTheme { WalletScreen(rememberPreviewEnv()) {} }

@Composable
private fun TxRow(tx: Tx) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(dateTime(tx.atMs), color = Palette.InkSoft, fontSize = 12.5.sp)
            }
            Spacer(Modifier.width(10.dp))
            if (tx.amount != 0L) {
                Text(
                    (if (tx.amount > 0) "+" else "") + rupiah(tx.amount),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
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
        bottom = { PrimaryButton("Tarik ${if (amount > 0) rupiah(amount) else ""}", { askPin = true }, enabled = amount > 0 && !tooMuch && account != null) },
    ) {
        Headline("Tarik ke rekening", "Hanya ke rekening atas nama Anda.")
        Spacer(Modifier.height(16.dp))
        SurfaceCard(tint = Palette.Lavender) {
            SummaryRow("Saldo tersedia", rupiah(state.walletBalance), strong = true)
        }
        Spacer(Modifier.height(16.dp))
        FormField(
            "Nominal (Rp)",
            input,
            { input = it.filter { c -> c.isDigit() }.take(12).trimStart('0') },
            keyboardType = KeyboardType.Number,
            placeholder = "0",
            error = if (tooMuch) "Melebihi saldo tersedia." else null,
        )
        Spacer(Modifier.height(4.dp))
        TextLink("Tarik semua", { input = state.walletBalance.toString() })
        Spacer(Modifier.height(10.dp))
        Text("Rekening tujuan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        if (account != null) {
            SurfaceCard {
                Text("${account.bank} ${account.number}", fontWeight = FontWeight.SemiBold)
                Text(account.holder, color = Palette.InkSoft, fontSize = 13.sp)
            }
        } else {
            Text("Belum ada rekening terverifikasi.", color = Palette.InkSoft)
        }
    }

    if (askPin && account != null) {
        PinDialog(env, "Tarik ${rupiah(amount)}", { askPin = false }) {
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
            SummaryRow("Uang muka", rupiah(downPayment), strong = true)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Lanjut ke perjanjian", { onNext(InstallmentDraft(skuCode, sku.buyPrice, downPayment, tenor)) }, enabled = sku.stock > 0)
        },
    ) {
        Headline("Cicil emas", "Token baru bisa dijual atau dicetak setelah lunas.")
        Spacer(Modifier.height(16.dp))
        Text("Pilih SKU", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(state.skus.filter { it.weightMg in 500L..10_000L }.map { it.code to gram(it.weightMg) }, skuCode) { skuCode = it }
        Spacer(Modifier.height(14.dp))
        Text("Uang muka", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(DOWN_PAYMENT_PERCENTS.map { it to "$it%" }, dpPercent) { dpPercent = it }
        Spacer(Modifier.height(14.dp))
        Text("Tenor", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(TENORS.map { it to "$it bulan" }, tenor) { tenor = it }
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            SummaryRow("Harga ${sku.product} ${gram(sku.weightMg)}", rupiah(sku.buyPrice))
            SummaryRow("Uang muka $dpPercent%", rupiah(downPayment))
            SummaryRow("Angsuran per bulan", rupiah(monthly), strong = true)
        }
        Spacer(Modifier.height(8.dp))
        Text("Angsuran belum termasuk biaya dari mitra.", color = Palette.InkSoft, fontSize = 13.sp)
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
            androidx.compose.material3.FilterChip(
                selected = active,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 13.sp, maxLines = 1) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Palette.Primary,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
fun AccountScreen(env: AppEnv, onVerify: () -> Unit, onInstallments: () -> Unit, onLogout: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val user = state.user
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        TopTitleBar("Profil", null)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SurfaceCard {
                    Text(user?.name ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(user?.email ?: "", color = Palette.InkSoft, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    if (user?.verified == true) {
                        StatusChip("Identitas terverifikasi", ChipTone.GOOD)
                    } else {
                        StatusChip("Belum terverifikasi", ChipTone.WARN)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("Verifikasi sekarang", onVerify)
                    }
                }
            }
            state.bankAccounts.firstOrNull()?.let { account ->
                item {
                    SurfaceCard {
                        Text("Rekening", color = Palette.InkSoft, fontSize = 13.sp)
                        Text("${account.bank} ${account.number}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { SurfaceCard(onClick = onInstallments) { Text("Ajukan cicilan emas", fontWeight = FontWeight.SemiBold) } }
            if (state.contracts.isNotEmpty()) {
                item { Text("Kontrak cicilan", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                items(state.contracts, key = { it.id }) { contract -> ContractCard(env, contract) }
            }
            item {
                DestructiveButton("Keluar", onLogout)
                Spacer(Modifier.height(10.dp))
                Text("REIDIG · Raja Emas Indonesia · versi 0.1.0", color = Palette.InkSoft, fontSize = 12.5.sp)
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
            Text(contract.id, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (locked) StatusChip("Berjalan", ChipTone.WARN, Icons.Outlined.Lock) else StatusChip("Lunas", ChipTone.GOOD)
        }
        Spacer(Modifier.height(4.dp))
        token?.let { Text("${state.sku(it.skuCode).product} ${gram(state.sku(it.skuCode).weightMg)}", fontSize = 14.sp) }
        Text("${rupiah(contract.monthlyBeforeFees)} per bulan · ${contract.paidMonths} dari ${contract.tenorMonths} bulan", color = Palette.InkSoft, fontSize = 13.sp)
        Text(contract.partner, color = Palette.InkSoft, fontSize = 13.sp)
        Text("Perjanjian ${contract.agreementVersion} disetujui ${dateTime(contract.agreedAtMs)}", color = Palette.InkSoft, fontSize = 13.sp)
    }
}
