package com.reidig.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/** Isi perjanjian masih draf dan perlu ditinjau tim legal serta disesuaikan dengan kontrak mitra. */
private val AGREEMENT_CLAUSES = listOf(
    "1. Para pihak" to "Perjanjian ini berlaku antara Anda sebagai pembeli, Raja Emas Indonesia sebagai penjual, dan mitra pembiayaan sebagai penyedia pembiayaan.",
    "2. Harga" to "Harga emas dikunci saat pengajuan dan tidak berubah selama masa cicilan.",
    "3. Uang muka" to "Uang muka dibayar dalam 15 menit setelah pengajuan. Kalau tidak dibayar, pengajuan batal dan keping dilepas kembali.",
    "4. Penguncian keping" to "Setelah uang muka lunas, satu keping bernomor seri dikunci untuk Anda. Tokennya tidak bisa dijual atau dicetak sampai cicilan lunas.",
    "5. Angsuran" to "Angsuran dibayar ke mitra sesuai jadwal. Nilai yang tertera belum termasuk biaya dari mitra, yang dirinci di kontrak mitra.",
    "6. Keterlambatan dan gagal bayar" to "Denda dan penanganan gagal bayar mengikuti kontrak mitra. Jika terjadi gagal bayar, token bisa dibatalkan dan keping dilepas. Pengembalian dana mengikuti kontrak.",
    "7. Pelunasan" to "Token menjadi aktif setelah mitra mengonfirmasi cicilan lunas. Setelah itu token bisa dijual atau dicetak.",
    "8. Data pribadi" to "Data identitas dan transaksi Anda dibagikan ke mitra untuk menilai dan mengelola pembiayaan.",
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
        Headline("Baca sebelum mengajukan", "Versi ${Rules.INSTALLMENT_AGREEMENT_VERSION}")
        Spacer(Modifier.height(16.dp))
        SurfaceCard(tint = Palette.Lavender) {
            SummaryRow("Barang", "${sku.product} ${gram(sku.weightMg)}")
            SummaryRow("Harga", rupiah(draft.price))
            SummaryRow("Uang muka", rupiah(draft.downPayment))
            SummaryRow("Sisa pembiayaan", rupiah(draft.price - draft.downPayment))
            SummaryRow("Tenor", "${draft.tenorMonths} bulan")
            SummaryRow("Angsuran per bulan", rupiah((draft.price - draft.downPayment) / draft.tenorMonths), strong = true)
        }
        Spacer(Modifier.height(20.dp))
        AGREEMENT_CLAUSES.forEach { (title, body) ->
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(body, color = Palette.InkSoft, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
        }
        ConsentRow("Saya sudah membaca dan menyetujui perjanjian ini.", agreed) { agreed = it }
        ConsentRow("Saya setuju data saya dibagikan ke mitra pembiayaan.", dataShared) { dataShared = it }
    }

    if (askPin) {
        PinDialog(env, "Setujui perjanjian", { askPin = false }) {
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
        Text(rupiah(application.downPayment), fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text(
            "Bayar dalam ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
            color = Palette.InkSoft,
        )
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            SummaryRow("Barang", "${sku.product} ${gram(sku.weightMg)}")
            SummaryRow("Tenor", "${application.tenorMonths} bulan")
            SummaryRow("Angsuran per bulan", rupiah(application.monthlyBeforeFees))
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
    }

    if (askPin) {
        PinDialog(env, "Bayar ${rupiah(application.downPayment)}", { askPin = false }) {
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
            title = { Text("Batalkan pengajuan?") },
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
