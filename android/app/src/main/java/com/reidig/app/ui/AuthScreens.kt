package com.reidig.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reidig.app.R
import com.reidig.shared.MockRepository
import com.reidig.shared.ReidigException
import com.reidig.shared.Rules
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun BrandMark(size: Int = 44) {
    Image(
        painterResource(R.drawable.logo_rei),
        contentDescription = "Logo Raja Emas Indonesia",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4f).dp)),
    )
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().background(NightGradient).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Text("REIDIG", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Raja Emas Indonesia", color = Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(color = Palette.Gold, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(32.dp))
    }
}

@ScreenPreview
@Composable
private fun SplashPreview() = ReidigTheme { SplashScreen {} }

@Composable
fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(40)
            Spacer(Modifier.width(10.dp))
            Text("REIDIG", color = Palette.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(R.drawable.onboarding_gold),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Beli dan simpan emas asli\nlewat aplikasi",
            color = Palette.Primary,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 27.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Masuk", onLogin)
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Buat akun", onRegister)
        Spacer(Modifier.height(16.dp))
    }
}

@ScreenPreview
@Composable
private fun WelcomePreview() = ReidigTheme { WelcomeScreen({}, {}) }

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.White.copy(alpha = 0.8f),
    errorBorderColor = Color(0xFFFFB4A6),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    errorTextColor = Color.White,
    cursorColor = Color.White,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
)

@Composable
fun LoginScreen(env: AppEnv, onLoggedIn: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        delay(500)
        try {
            env.repo.login(email, password)
            onLoggedIn()
        } catch (e: ReidigException) {
            error = e.message
        }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().background(NightGradient).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(72.dp))
        Text("Halo,\nSelamat datang kembali!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 36.sp)
        Spacer(Modifier.height(32.dp))

        Text("Email", color = Color.White, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            email,
            { email = it; error = null },
            placeholder = { Text("Masukkan email", color = Color.White.copy(alpha = 0.7f)) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp),
            colors = darkFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Text("Kata sandi", color = Color.White, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password,
            { password = it; error = null },
            placeholder = { Text("Masukkan kata sandi", color = Color.White.copy(alpha = 0.7f)) },
            singleLine = true,
            isError = error != null,
            visualTransformation = if (password.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp),
            colors = darkFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextLink("Lupa kata sandi?", onForgot, color = Color.White)
        }
        error?.let {
            ErrorLine(it, color = Color(0xFFFFB4A6))
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        LightButton("Masuk", { loading = true }, enabled = !loading && email.isNotBlank() && password.isNotBlank())
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f))
            Text("atau", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
            HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(18.dp))
        GhostOnDarkButton("Masuk dengan Google", {
            env.repo.loginWithGoogle()
            onLoggedIn()
        })
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Baru di sini?", color = Color.White.copy(alpha = 0.8f))
            TextLink("Buat akun", onRegister, color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@ScreenPreview
@Composable
private fun LoginPreview() = ReidigTheme { LoginScreen(rememberPreviewEnv(), {}, {}, {}) }

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    FormScreen(
        "Lupa kata sandi",
        onBack,
        bottom = { PrimaryButton(if (sent) "Kembali ke halaman masuk" else "Kirim tautan", { if (sent) onBack() else sent = true }, enabled = Rules.isValidEmail(email)) },
    ) {
        Headline("Atur ulang kata sandi", "Kami kirim tautannya ke email Anda.")
        Spacer(Modifier.height(24.dp))
        FormField("Email", email, { email = it; sent = false }, placeholder = "nama@email.com", keyboardType = KeyboardType.Email)
        if (sent) {
            Spacer(Modifier.height(16.dp))
            StatusChip("Tautan sudah dikirim", ChipTone.GOOD)
        }
    }
}

@ScreenPreview
@Composable
private fun ForgotPasswordPreview() = ReidigTheme { ForgotPasswordScreen {} }

@Composable
fun RegisterEmailScreen(signup: SignupDraft, onBack: () -> Unit, onSent: () -> Unit, onGoogle: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(signup.email) }
    var touched by remember { mutableStateOf(false) }
    val valid = Rules.isValidEmail(email)
    FormScreen(
        "Buat akun",
        onBack,
        step = 1 to 3,
        bottom = {
            PrimaryButton("Kirim kode", { signup.email = email.trim(); onSent() }, enabled = valid)
            Spacer(Modifier.height(10.dp))
            Text("Dengan mendaftar, Anda menyetujui Ketentuan Layanan dan Kebijakan Privasi.", color = Palette.InkSoft, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Headline("Masukkan email Anda")
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Lanjut dengan Google", onGoogle)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
            Text("atau pakai email", color = Palette.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
        }
        Spacer(Modifier.height(20.dp))
        FormField(
            "Alamat email",
            email,
            { email = it; touched = true },
            placeholder = "nama@email.com",
            keyboardType = KeyboardType.Email,
                        error = if (touched && email.isNotEmpty() && !valid) "Masukkan alamat email yang valid." else null,
        )
    }
}

@ScreenPreview
@Composable
private fun RegisterEmailPreview() = ReidigTheme { RegisterEmailScreen(previewSignup(), {}, {}, {}) }

/** Layar OTP bersama untuk email dan nomor HP. */
@Composable
fun OtpScreen(
    title: String,
    step: Pair<Int, Int>,
    sentTo: String,
    demoCode: String,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(30) }
    var resendRound by remember { mutableIntStateOf(0) }

    LaunchedEffect(resendRound) {
        seconds = 30
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }
    LaunchedEffect(verifying) {
        if (!verifying) return@LaunchedEffect
        delay(600)
        if (code == demoCode) {
            onVerified()
        } else {
            error = true
            code = ""
            verifying = false
        }
    }

    FormScreen(title, onBack, step = step, bottom = {
        NumberPad(
            onDigit = {
                if (code.length < 6) {
                    error = false
                    code += it
                    if (code.length == 6) verifying = true
                }
            },
            onDelete = { code = code.dropLast(1) },
            enabled = !verifying,
        )
    }) {
        Headline("Masukkan kode 6 angka", sentTo)
        Spacer(Modifier.height(32.dp))
        CodeDots(code.length, error = error)
        Spacer(Modifier.height(20.dp))
        when {
            verifying -> Text("Memverifikasi…", color = Palette.InkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            error -> ErrorLine("Kode salah. Coba lagi.", Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (seconds > 0) {
                Text("Kirim ulang kode dalam 0:${seconds.toString().padStart(2, '0')}", color = Palette.InkSoft, fontSize = 14.sp)
            } else {
                TextLink("Kirim ulang kode", { resendRound++ })
            }
        }
    }
}

@ScreenPreview
@Composable
private fun EmailOtpPreview() = ReidigTheme { OtpScreen("Verifikasi email", 2 to 3, "Dikirim ke sari@gmail.com", DEMO_EMAIL_OTP, {}, {}) }

@Composable
fun RegisterProfileScreen(env: AppEnv, signup: SignupDraft, onBack: () -> Unit, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(signup.name) }
    var dobDigits by rememberSaveable { mutableStateOf(signup.dob.filter { it.isDigit() }) }
    val dob = formatDateDigits(dobDigits)
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val dobValid = Rules.isAdult(dob, today.year, today.monthValue, today.dayOfMonth)
    val nameValid = Rules.isValidFullName(name)
    val passwordMatch = password.isNotEmpty() && password == confirm
    FormScreen(
        "Data diri",
        onBack,
        step = 3 to 3,
        bottom = {
            PrimaryButton(
                "Buat akun",
                {
                    signup.name = name.trim()
                    signup.dob = dob
                    env.repo.register(name, signup.email)
                    onDone()
                },
                enabled = nameValid && dobValid && Rules.isValidPassword(password) && passwordMatch,
            )
        },
    ) {
        Headline("Lengkapi data diri")
        Spacer(Modifier.height(12.dp))
        StatusChip("${signup.email} terverifikasi", ChipTone.GOOD)
        Spacer(Modifier.height(20.dp))
        FormField(
            "Nama lengkap",
            name,
            { name = it; nameTouched = true },
            placeholder = "Nama lengkap",
            hint = "Sesuai KTP.",
            error = if (nameTouched && !nameValid) "Masukkan nama lengkap." else null,
        )
        Spacer(Modifier.height(16.dp))
        FormField(
            "Tanggal lahir",
            dobDigits,
            { dobDigits = it.filter { c -> c.isDigit() }.take(8) },
            placeholder = "HH/BB/TTTT",
            keyboardType = KeyboardType.Number,
            transformation = DateTransformation,
            hint = "Sesuai KTP. Usia minimal 18 tahun.",
            error = if (dob.length == 10 && !dobValid) "Tanggal tidak valid atau usia di bawah 18 tahun." else null,
        )
        Spacer(Modifier.height(16.dp))
        FormField("Kata sandi", password, { password = it }, password = true, keyboardType = KeyboardType.Password)
        RuleLine("Minimal 8 karakter", Rules.hasMinLength(password))
        RuleLine("Berisi huruf dan angka", Rules.hasLettersAndNumbers(password))
        Spacer(Modifier.height(16.dp))
        FormField(
            "Ulangi kata sandi",
            confirm,
            { confirm = it },
            password = true,
            keyboardType = KeyboardType.Password,
            error = if (confirm.isNotEmpty() && !passwordMatch) "Kata sandi belum sama." else null,
        )
    }
}

@ScreenPreview
@Composable
private fun RegisterProfilePreview() = ReidigTheme { RegisterProfileScreen(rememberPreviewEnv(false), previewSignup(), {}, {}) }

const val DEMO_EMAIL_OTP = MockRepository.DEMO_EMAIL_OTP
