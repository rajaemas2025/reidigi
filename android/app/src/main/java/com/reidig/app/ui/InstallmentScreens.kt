package com.reidig.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reidig.shared.InstallmentApplication
import com.reidig.shared.InstallmentContract
import com.reidig.shared.PaymentMethod
import com.reidig.shared.Rules
import com.reidig.shared.gram
import com.reidig.shared.rupiah
import kotlinx.coroutines.delay

/** Pilihan pengguna di layar simulasi, dibawa ke perjanjian. */
data class InstallmentDraft(val skuCode: String, val price: Long, val downPayment: Long, val tenorMonths: Int)

/** Klausul akad pembiayaan syariah melalui agregator dan lembaga pembiayaan mitra (BPRS / Koperasi Syariah). */
private val AGREEMENT_CLAUSES = listOf(
    "1. Para Pihak & Akad Pembiayaan" to "Pembiayaan cicilan ini difasilitasi oleh lembaga keuangan syariah mitra berizin (BPRS / Koperasi Syariah) melalui agregator pembiayaan resmi berdasarkan akad Murabahah.",
    "2. Pelunasan Emas di Awal" to "Pihak penyedia pembiayaan melunasi 100% harga emas fisik secara langsung ke Raja Emas Indonesia di awal saat pengajuan disetujui untuk kepentingan nasabah.",
    "3. Jaminan Fisik di Brankas" to "Keping emas fisik yang telah dibayar lunas oleh mitra pembiayaan disimpan aman di brankas terpusat Raja Emas Indonesia sebagai jaminan pembiayaan selama masa angsuran berlangsung.",
    "4. Angsuran ke Pihak Pembiayaan" to "Anda mencicil pembayaran pokok dan margin angsuran secara berkala langsung kepada pihak agregator/penyedia pembiayaan sesuai jadwal jatuh tempo.",
    "5. Penyerahan Hak Milik Emas Saat Lunas" to "Setelah seluruh angsuran dinyatakan lunas 100% oleh pihak pembiayaan, hak kepemilikan token emas digital resmi diterbitkan dan dipindahkan ke portofolio aktif Anda di aplikasi REIDIG (dapat disimpan, dijual kembali, atau dicetak fisik).",
    "6. Perlindungan & Pembagian Data" to "Data identitas dan dokumen verifikasi Anda dibagikan secara aman kepada mitra pembiayaan khusus untuk keperluan verifikasi kelayakan dan pencatatan akad.",
)

@Composable
fun InstallmentAgreementScreen(
    env: AppEnv,
    draft: InstallmentDraft,
    onBack: () -> Unit,
    onSubmitted: (InstallmentApplication) -> Unit,
) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val sku = state.sku(draft.skuCode)
    var agreed by rememberSaveable { mutableStateOf(false) }
    var dataShared by rememberSaveable { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }

    FormScreen(
        "Perjanjian Cicilan",
        onBack,
        bottom = {
            PrimaryButton("Setuju & ajukan cicilan", { askPin = true }, enabled = agreed && dataShared)
        },
    ) {
        Headline(
            "Akad Pembiayaan Cicil Emas",
            "Akad Murabahah · Versi ${Rules.INSTALLMENT_AGREEMENT_VERSION}",
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(14.dp))
        SurfaceCard(tint = Palette.Primary50, outline = Palette.Primary100) {
            SummaryRow("Barang", "${sku.product} ${gram(sku.weightMg)}", strong = true, valueColor = Palette.Primary)
            SummaryRow("Harga dikunci", rupiah(draft.price))
            SummaryRow("Uang muka", rupiah(draft.downPayment))
            SummaryRow("Sisa pembiayaan", rupiah(draft.price - draft.downPayment))
            SummaryRow("Tenor", "${draft.tenorMonths} bulan")
            HorizontalDivider(color = Palette.Primary200, modifier = Modifier.padding(vertical = 6.dp))
            SummaryRow("Angsuran per bulan", rupiah((draft.price - draft.downPayment) / draft.tenorMonths), strong = true, valueColor = Palette.Primary)
        }
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            AGREEMENT_CLAUSES.forEachIndexed { idx, (title, body) ->
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Palette.Ink)
                Spacer(Modifier.height(3.dp))
                Text(body, color = Palette.InkSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                if (idx < AGREEMENT_CLAUSES.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        ConsentRow("Saya telah membaca dan menyetujui seluruh ketentuan akad pembiayaan ini.", agreed) { agreed = it }
        ConsentRow("Saya menyetujui data saya dibagikan ke agregator & mitra pembiayaan.", dataShared) { dataShared = it }
    }

    if (askPin) {
        PinDialog(env, "Setujui perjanjian cicilan ${sku.product} ${gram(sku.weightMg)}", { askPin = false }) {
            askPin = false
            env.attempt {
                env.repo.submitInstallment(draft.skuCode, draft.downPayment, draft.tenorMonths, draft.price, Rules.INSTALLMENT_AGREEMENT_VERSION)
            }?.let(onSubmitted)
        }
    }
}

@ScreenPreview
@Composable
private fun InstallmentAgreementPreview() = ReidigTheme {
    InstallmentAgreementScreen(rememberPreviewEnv(), InstallmentDraft("LMRE-5G", 12_442_500, 2_488_500, 6), {}, {})
}

@Composable
fun DownPaymentScreen(
    env: AppEnv,
    application: InstallmentApplication,
    onCancelled: () -> Unit,
    onPaid: (InstallmentContract) -> Unit,
) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val sku = state.sku(application.skuCode)
    var method by remember { mutableStateOf(PaymentMethod.QRIS) }
    var askPin by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var paymentCode by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableLongStateOf(Rules.PAYMENT_WINDOW_MS / 1000) }
    val insufficient = method == PaymentMethod.WALLET && state.walletBalance < application.downPayment

    fun cancel() {
        env.repo.cancelInstallment(application.id)
        onCancelled()
    }

    fun pay() {
        env.attempt { env.repo.payDownPayment(application.id, method) }?.let(onPaid)
    }

    LaunchedEffect(application.id) {
        while (true) {
            secondsLeft = ((application.payBeforeMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (secondsLeft == 0L) {
                env.notify("Batas waktu pembayaran habis. Pengajuan dibatalkan.")
                cancel()
                break
            }
            delay(1000)
        }
    }
    BackHandler { confirmCancel = true }

    FormScreen(
        "Bayar Uang Muka",
        { confirmCancel = true },
        bottom = {
            PrimaryButton(
                "Bayar ${rupiah(application.downPayment)}",
                { if (method == PaymentMethod.WALLET) askPin = true else paymentCode = env.repo.downPaymentCode(method) },
                enabled = !insufficient,
            )
        },
    ) {
        SurfaceCard(tint = Palette.WarnSoft, outline = Palette.WarnBorder) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Batas waktu pembayaran DP", color = Palette.Warn, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                    color = Palette.Warn,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SurfaceCard {
            Text("Uang muka (DP)", color = Palette.InkSoft, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                rupiah(application.downPayment),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                color = Palette.Primary,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Palette.Line)
            Spacer(Modifier.height(10.dp))
            SummaryRow("Barang", "${sku.product} ${gram(sku.weightMg)}")
            SummaryRow("Tenor", "${application.tenorMonths} bulan")
            SummaryRow("Angsuran per bulan", rupiah(application.monthlyBeforeFees), strong = true)
        }
        Spacer(Modifier.height(18.dp))
        Text("Metode pembayaran", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        PaymentMethod.entries.forEach { option ->
            val detail = if (option == PaymentMethod.WALLET) "Tersedia ${rupiah(state.walletBalance)}" else "Konfirmasi otomatis"
            PaymentOption(option.label, detail, option == method) { method = option }
            Spacer(Modifier.height(8.dp))
        }
        if (insufficient) ErrorLine("Saldo tidak mencukupi. Pilih metode lain.")
    }

    if (askPin) {
        PinDialog(env, "Bayar DP ${rupiah(application.downPayment)}", { askPin = false }) {
            askPin = false
            pay()
        }
    }
    paymentCode?.let { code ->
        PaymentWaitDialog(
            env,
            application.downPayment,
            method,
            code,
            application.payBeforeMs,
            "Ganti metode pembayaran",
            onCancel = { paymentCode = null },
            onConfirm = ::pay,
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Batalkan pengajuan?", fontWeight = FontWeight.Bold) },
            text = { Text("Keping yang ditahan akan dilepas kembali.") },
            confirmButton = { TextLink("Batalkan", ::cancel, color = Palette.Critical) },
            dismissButton = { TextLink("Lanjut bayar", { confirmCancel = false }) },
        )
    }
}

@ScreenPreview
@Composable
private fun DownPaymentPreview() = ReidigTheme {
    val env = rememberPreviewEnv()
    DownPaymentScreen(
        env,
        remember { env.repo.submitInstallment("LMRE-5G", 2_488_500, 6, 12_442_500, Rules.INSTALLMENT_AGREEMENT_VERSION) },
        {},
        {},
    )
}
