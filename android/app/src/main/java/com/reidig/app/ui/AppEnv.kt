package com.reidig.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Konfirmasi PIN dengan gaya bottom-sheet seperti pada prototipe Goldin v1. */
@Composable
fun PinDialog(env: AppEnv, title: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(Color(0x660F052C))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Palette.Line))
                Spacer(Modifier.height(18.dp))
                Text(
                    "Masukkan PIN",
                    fontFamily = TitleFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    fontFamily = BodyFontFamily,
                    color = Palette.InkSecondary,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                CodeBoxes(code = code, masked = true, error = error != null)
                Spacer(Modifier.height(10.dp))
                if (error != null) {
                    ErrorLine(error!!)
                } else {
                    Text(
                        "Masukkan 6 digit PIN keamananmu",
                        fontFamily = BodyFontFamily,
                        color = Palette.InkSoft,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
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
