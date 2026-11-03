package com.reidig.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reidig.shared.MockRepository
import com.reidig.shared.ReidigException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AppEnv(
    val repo: MockRepository,
    val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    /** Menjalankan aksi repository dan menampilkan pesan kalau ditolak. */
    fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: ReidigException) {
        notify(e.message ?: "Terjadi kesalahan.")
        null
    }
}

class SignupDraft {
    var email: String = ""
    var name: String = ""
    var dob: String = ""
}

class KycDraft {
    var phone: String = ""
    var channel: String = "WhatsApp"
    var name: String = ""
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", Locale.forLanguageTag("id-ID"))
    .withZone(ZoneId.of("Asia/Jakarta"))

fun dateTime(ms: Long): String = DATE_FORMAT.format(Instant.ofEpochMilli(ms)) + " WIB"

/** Konfirmasi PIN sebelum transaksi. */
@Composable
fun PinDialog(env: AppEnv, title: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding(),
        ) {
            TopTitleBar("Masukkan PIN", onDismiss)
            Column(Modifier.weight(1f).padding(20.dp)) {
                Headline(title)
                Spacer(Modifier.height(36.dp))
                CodeDots(code.length, error = error != null)
                Spacer(Modifier.height(18.dp))
                error?.let { ErrorLine(it, Modifier.align(Alignment.CenterHorizontally)) }
            }
            Column(Modifier.padding(20.dp)) {
                NumberPad(
                    onDigit = { digit ->
                        if (code.length < 6) {
                            error = null
                            code += digit
                            if (code.length == 6) {
                                try {
                                    if (env.repo.verifyPin(code)) {
                                        onVerified()
                                    } else {
                                        error = "PIN salah. Coba lagi."
                                    }
                                } catch (e: ReidigException) {
                                    error = e.message
                                }
                                code = ""
                            }
                        }
                    },
                    onDelete = { code = code.dropLast(1) },
                )
            }
        }
    }
}

@ScreenPreview
@Composable
private fun PinDialogPreview() = ReidigTheme { PinDialog(rememberPreviewEnv(), "Bayar Rp 2.513.500", {}, {}) }
