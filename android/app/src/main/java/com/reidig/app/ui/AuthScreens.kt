package com.reidig.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reidig.app.R
import com.reidig.shared.MockRepository
import com.reidig.shared.ReidigException
import com.reidig.shared.Rules
import com.reidig.shared.rupiah
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
        Modifier.fillMaxSize().background(NightGradient).background(GlowGradient).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        GoldBarArt(170.dp)
        Spacer(Modifier.height(24.dp))
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
fun WelcomeScreen(env: AppEnv, onLogin: () -> Unit, onRegister: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().background(NightGradient).background(GlowGradient).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(42)
            Spacer(Modifier.width(12.dp))
            Text("REIDIG", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp),
        ) {
            Text("Harga emas / gram", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(rupiah(state.sku("LMRE-1G").buyPrice), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text("Diperbarui ${state.priceUpdatedAtLabel}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(36.dp))
        GoldBarArt(150.dp)
        Spacer(Modifier.height(26.dp))
        Text(
            "Beli dan simpan emas asli\nlewat aplikasi",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            lineHeight = 33.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        LightButton("Buat akun", onRegister)
        Spacer(Modifier.height(12.dp))
        GhostOnDarkButton("Masuk", onLogin)
        Spacer(Modifier.height(16.dp))
    }
}

@ScreenPreview
@Composable
private fun WelcomePreview() = ReidigTheme { WelcomeScreen(rememberPreviewEnv(false), {}, {}) }

/** Kolom kata sandi dengan tombol tampilkan dan sembunyikan. */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    error: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    FormField(
        label,
        value,
        onValueChange,
        placeholder = placeholder,
        error = error,
        keyboardType = KeyboardType.Password,
        password = !visible,
        trailing = {
            Text(
                if (visible) "Sembunyikan" else "Tampilkan",
                color = Palette.Primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { visible = !visible }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    )
}

@Composable
fun LoginScreen(
    env: AppEnv,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    onGoogle: () -> Unit,
) {
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

    FormScreen(
        "",
        onBack,
        bottomDivider = true,
        bottom = {
            PrimaryButton("Masuk", { loading = true }, enabled = email.isNotBlank() && password.isNotBlank(), loading = loading)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Baru di sini?", color = Palette.InkSoft, fontSize = 14.sp)
                TextLink("Buat akun", onRegister)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(40)
            Spacer(Modifier.width(10.dp))
            Text("REIDIG", color = Palette.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(24.dp))
        Headline("Halo, selamat datang kembali!")
        Spacer(Modifier.height(24.dp))
        FormField(
            "Email",
            email,
            { email = it; error = null },
            placeholder = "Masukkan email",
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(16.dp))
        PasswordField("Kata sandi", password, { password = it; error = null }, placeholder = "Masukkan kata sandi", error = error)
        Spacer(Modifier.height(6.dp))
        TextLink("Lupa kata sandi?", onForgot, Modifier.offset(x = (-6).dp))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
            Text("atau", color = Palette.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
        }
        Spacer(Modifier.height(14.dp))
        GoogleButton("Masuk dengan Google", onGoogle)
    }
}

@ScreenPreview
@Composable
private fun LoginPreview() = ReidigTheme { LoginScreen(rememberPreviewEnv(false), {}, {}, {}, {}, {}) }

/** Tiruan untuk uji alur. Di versi rilis diganti pemilih akun bawaan Android lewat Credential Manager. */
@Composable
fun GoogleAccountScreen(onBack: () -> Unit, onSelected: () -> Unit) {
    FormScreen("", onBack) {
        Spacer(Modifier.height(8.dp))
        Image(painterResource(R.drawable.ic_google), null, Modifier.size(36.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
        Text("Pilih akun", fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                append("untuk melanjutkan ke ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Palette.Primary)) { append("REIDIG") }
            },
            color = Palette.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Palette.Line)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onSelected).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Primary), contentAlignment = Alignment.Center) {
                Text("P", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Pengguna", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text("pengguna@gmail.com", color = Palette.InkSoft, fontSize = 14.sp)
            }
        }
        HorizontalDivider(color = Palette.Line)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, Palette.Line, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PersonOutline, null, tint = Palette.InkSoft)
            }
            Spacer(Modifier.width(14.dp))
            Text("Gunakan akun lain", fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
        HorizontalDivider(color = Palette.Line)
        Spacer(Modifier.height(18.dp))
        Text("REIDIG menerima nama dan alamat email Anda. Kata sandi Google tidak dibagikan.", color = Palette.InkSoft, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@ScreenPreview
@Composable
private fun GoogleAccountPreview() = ReidigTheme { GoogleAccountScreen({}, {}) }

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
        bottomDivider = true,
        bottom = {
            PrimaryButton("Kirim kode", { signup.email = email.trim(); onSent() }, enabled = valid)
            Spacer(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    append("Dengan mendaftar, Anda menyetujui ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.Primary, textDecoration = TextDecoration.Underline)) { append("Ketentuan Layanan") }
                    append(" dan ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.Primary, textDecoration = TextDecoration.Underline)) { append("Kebijakan Privasi") }
                    append(".")
                },
                color = Palette.InkSoft,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Headline("Masukkan email Anda")
        Spacer(Modifier.height(20.dp))
        GoogleButton("Lanjut dengan Google", onGoogle)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
            Text("atau pakai email", color = Palette.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
        }
        Spacer(Modifier.height(18.dp))
        FormField(
            "Alamat email",
            email,
            { email = it; touched = true },
            placeholder = "nama@email.com",
            keyboardType = KeyboardType.Email,
            hint = "Kami kirim kode 6 angka ke email ini.",
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
    sentVia: String,
    target: String,
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
        Spacer(Modifier.height(12.dp))
        Headline(
            "Masukkan kode 6 angka",
            centered = true,
            subtitleRich = buildAnnotatedString {
                append("$sentVia ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Palette.Ink)) { append(target) }
            },
        )
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
private fun EmailOtpPreview() = ReidigTheme { OtpScreen("Verifikasi email", 2 to 3, "Dikirim ke", "sari@gmail.com", DEMO_EMAIL_OTP, {}, {}) }

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
        bottomDivider = true,
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
        Spacer(Modifier.height(14.dp))
        VerifiedBanner(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(signup.email) }
                append(" terverifikasi")
            },
        )
        Spacer(Modifier.height(18.dp))
        FormField(
            "Nama lengkap",
            name,
            { name = it; nameTouched = true },
            placeholder = "Sesuai KTP",
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
        PasswordField("Kata sandi", password, { password = it })
        RuleLine("Minimal 8 karakter", Rules.hasMinLength(password))
        RuleLine("Berisi huruf dan angka", Rules.hasLettersAndNumbers(password))
        Spacer(Modifier.height(16.dp))
        PasswordField(
            "Ulangi kata sandi",
            confirm,
            { confirm = it },
            error = if (confirm.isNotEmpty() && !passwordMatch) "Kata sandi belum sama." else null,
        )
    }
}

@ScreenPreview
@Composable
private fun RegisterProfilePreview() = ReidigTheme { RegisterProfileScreen(rememberPreviewEnv(false), previewSignup(), {}, {}) }

const val DEMO_EMAIL_OTP = MockRepository.DEMO_EMAIL_OTP
