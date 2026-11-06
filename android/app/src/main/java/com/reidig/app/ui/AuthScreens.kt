package com.reidig.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@Composable
fun BrandMark(size: Int = 40) {
    Image(
        painterResource(R.drawable.logo_rei),
        contentDescription = "Logo Raja Emas Indonesia",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.5f).dp)),
    )
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1000)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        GoldBarArt(160.dp)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoldBarsBadge(44.dp, 14.dp)
            Spacer(Modifier.width(12.dp))
            Text("REIDIG", color = Palette.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Tabungan & Cicilan Emas Fisik 24K", color = Palette.InkSecondary, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(color = Palette.Primary, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(32.dp))
    }
}

@ScreenPreview
@Composable
private fun SplashPreview() = ReidigTheme { SplashScreen {} }

@Composable
fun WelcomeScreen(env: AppEnv, onLogin: () -> Unit, onRegister: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val buyPrice = state.sku("LMRE-1G").buyPrice
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var simAmount by remember { mutableLongStateOf(50_000L) }

    Column(
        Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top header: Brand + Live Gold Price Pill
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoldBarsBadge(34.dp, 10.dp)
                Spacer(Modifier.width(8.dp))
                Text("REIDIG", color = Palette.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            }
            Row(
                Modifier.cardDepth(Color.White, RoundedCornerShape(50), Palette.Line)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Mint))
                Spacer(Modifier.width(6.dp))
                Text("Emas", color = Palette.InkSecondary, fontSize = 11.5.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "${rupiah(buyPrice)}/gr",
                    color = Palette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(4.dp))
                Text("▲ 0,4%", color = Palette.Good, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }

        // 3-Slide Interactive Carousel
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> {
                        Box(Modifier.height(210.dp), contentAlignment = Alignment.Center) {
                            GoldBarArt(190.dp)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Nabung emas makin praktis, kumpulkan perlahan tanpa beban",
                            color = Palette.Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            lineHeight = 29.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tabung emas 24 karat dari nominal kecil, langsung dari ponsel.",
                            color = Palette.InkSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    1 -> {
                        // Interactive Simulation Card
                        Column(
                            Modifier.fillMaxWidth()
                                .cardDepth(Color.White, RoundedCornerShape(16.dp), Palette.Line)
                                .padding(16.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Kamu nabung", color = Palette.InkSoft, fontSize = 12.sp)
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(Palette.Primary50).padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text("Simulasi", color = Palette.Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                rupiah(simAmount),
                                color = Palette.Ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background(Palette.Primary50),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.SwapVert, null, tint = Palette.Primary, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                HorizontalDivider(color = Palette.Line, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Kamu dapat", color = Palette.InkSoft, fontSize = 12.sp)
                            val grams = simAmount.toDouble() / buyPrice.toDouble()
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    String.format(Locale.forLanguageTag("id-ID"), "%.4f", grams),
                                    color = Palette.Primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("gram emas 24K", color = Palette.InkSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(10_000L to "10rb", 50_000L to "50rb", 100_000L to "100rb", 500_000L to "500rb").forEach { (amt, label) ->
                                    val active = simAmount == amt
                                    Box(
                                        Modifier.weight(1f).height(32.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (active) Palette.Primary else Palette.Paper)
                                            .border(1.dp, if (active) Palette.Primary else Palette.Line, RoundedCornerShape(50))
                                            .clickable { simAmount = amt },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            label,
                                            color = if (active) Color.White else Palette.InkSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Mulai dari Rp10.000",
                            color = Palette.Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Beli sedikit demi sedikit atau atur sesuai kemampuan. Emas dihitung otomatis ke gram.",
                            color = Palette.InkSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        // Slide 2: Security & Vault illustration
                        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(160.dp).clip(CircleShape).background(Palette.Primary100))
                            Box(
                                Modifier.size(104.dp)
                                    .shadow(12.dp, RoundedCornerShape(30.dp), ambientColor = Palette.Primary, spotColor = Palette.Primary)
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(Palette.Primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Shield, null, tint = Palette.Gold, modifier = Modifier.size(52.dp))
                            }
                            // Floating badges
                            SecurityPill("Emas 24 karat", Modifier.align(Alignment.TopStart).padding(top = 12.dp))
                            SecurityPill("Disimpan di brankas", Modifier.align(Alignment.TopEnd).padding(top = 24.dp))
                            SecurityPill("Bisa tarik fisik", Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp, start = 12.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Aman, tercatat atas namamu",
                            color = Palette.Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Emas fisik tersimpan di brankas mitra berizin dan bisa dicetak kapan saja.",
                            color = Palette.InkSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Animated Page Indicator Dots
        Row(
            Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { idx ->
                val active = pagerState.currentPage == idx
                val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotW")
                val color by animateColorAsState(if (active) Palette.Primary else Palette.Line, label = "dotC")
                Box(
                    Modifier.width(width).height(8.dp).clip(RoundedCornerShape(50)).background(color)
                        .clickable { scope.launch { pagerState.animateScrollToPage(idx) } },
                )
            }
        }

        // Bottom Buttons (Masuk primary on top, Buat akun outlined below)
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
            PrimaryButton("Masuk", onLogin)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Buat akun", onRegister)
        }
    }
}

@Composable
private fun SecurityPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.cardDepth(Color.White, RoundedCornerShape(50), Palette.Line).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Palette.GoodSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Palette.Good, modifier = Modifier.size(11.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
    }
}

@ScreenPreview
@Composable
private fun WelcomePreview() = ReidigTheme { WelcomeScreen(rememberPreviewEnv(false), {}, {}) }

/** Kolom kata sandi dengan ikon gembok di kiri dan tombol mata di kanan. */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "••••••••",
    error: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    FormField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        error = error,
        keyboardType = KeyboardType.Password,
        password = !visible,
        leadingIcon = Icons.Outlined.Lock,
        trailing = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Sembunyikan" else "Tampilkan",
                    tint = Palette.InkSoft,
                    modifier = Modifier.size(20.dp),
                )
            }
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
        title = "",
        onBack = onBack,
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Belum memiliki akun?", color = Palette.InkSecondary, fontSize = 13.5.sp)
                TextLink("Daftar", onRegister)
            }
        },
    ) {
        Text(
            "Halo,\nSelamat datang kembali!",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 35.sp,
            color = Palette.Primary,
        )
        Spacer(Modifier.height(6.dp))
        Text("Masuk untuk cek tabungan emasmu hari ini.", color = Palette.InkSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(26.dp))
        FormField(
            label = "Email",
            value = email,
            onValueChange = { email = it; error = null },
            placeholder = "nama@email.com",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Outlined.Email,
        )
        Spacer(Modifier.height(16.dp))
        PasswordField("Kata sandi", password, { password = it; error = null }, placeholder = "Masukkan kata sandi", error = error)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextLink("Lupa kata sandi?", onForgot)
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Masuk", { loading = true }, enabled = email.isNotBlank() && password.isNotBlank(), loading = loading)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
            Text("atau", color = Palette.InkSoft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
        }
        Spacer(Modifier.height(18.dp))
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
        Text("Pilih akun", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                append("untuk melanjutkan ke ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Palette.Primary)) { append("REIDIG") }
            },
            color = Palette.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        SurfaceCard {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onSelected).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Primary), contentAlignment = Alignment.Center) {
                    Text("P", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Pengguna", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("pengguna@gmail.com", color = Palette.InkSecondary, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, Palette.Line, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PersonOutline, null, tint = Palette.InkSecondary)
                }
                Spacer(Modifier.width(14.dp))
                Text("Gunakan akun lain", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("REIDIG menerima nama dan alamat email Anda. Kata sandi Google tidak dibagikan.", color = Palette.InkSoft, fontSize = 12.5.sp, lineHeight = 19.sp)
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
        Headline("Atur ulang kata sandi", "Kami kirim tautannya ke email Anda.", titleColor = Palette.Primary)
        Spacer(Modifier.height(24.dp))
        FormField(
            "Email",
            email,
            { email = it; sent = false },
            placeholder = "nama@email.com",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Outlined.Email,
        )
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
    var agreed by rememberSaveable { mutableStateOf(true) }
    val valid = Rules.isValidEmail(email)
    FormScreen(
        title = "",
        onBack = onBack,
        step = 1 to 3,
        stepLabels = listOf("Buat akun", "Verifikasi", "Data diri"),
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Sudah memiliki akun?", color = Palette.InkSecondary, fontSize = 13.5.sp)
                TextLink("Masuk", onBack)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Headline(
            "Buat akun baru",
            "Masukkan email aktif untuk menerima kode verifikasi.",
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(22.dp))
        FormField(
            label = "Email",
            value = email,
            onValueChange = { email = it; touched = true },
            placeholder = "nama@email.com",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Outlined.Email,
            hint = "Kami akan mengirimkan 6 digit kode OTP ke email ini.",
            error = if (touched && email.isNotEmpty() && !valid) "Format email belum benar. Contoh: nama@email.com" else null,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clickable { agreed = !agreed }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = { agreed = it },
                colors = CheckboxDefaults.colors(checkedColor = Palette.Primary, uncheckedColor = Palette.Line),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                buildAnnotatedString {
                    append("Saya menyetujui ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.Primary)) { append("Syarat & Ketentuan") }
                    append(" serta ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.Primary)) { append("Kebijakan Privasi") }
                    append(".")
                },
                color = Palette.InkSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Lanjutkan", { signup.email = email.trim(); onSent() }, enabled = valid && agreed)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
            Text("atau", color = Palette.InkSoft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Palette.Line)
        }
        Spacer(Modifier.height(18.dp))
        GoogleButton("Daftar dengan Google", onGoogle)
    }
}

@ScreenPreview
@Composable
private fun RegisterEmailPreview() = ReidigTheme { RegisterEmailScreen(previewSignup(), {}, {}, {}) }

/** Layar OTP bersama untuk email dan nomor HP, menggunakan 6 kotak digit sesuai prototipe. */
@Composable
fun OtpScreen(
    title: String,
    step: Pair<Int, Int>,
    sentVia: String,
    target: String,
    demoCode: String,
    stepLabels: List<String>? = null,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
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
        delay(450)
        if (code == demoCode) {
            verified = true
            delay(350)
            onVerified()
        } else {
            error = true
            code = ""
            verifying = false
        }
    }

    FormScreen(
        title = if (stepLabels != null) "" else title,
        onBack = onBack,
        step = step,
        stepLabels = stepLabels,
        bottom = {
            NumberPad(
                onDigit = {
                    if (code.length < 6 && !verified) {
                        error = false
                        code += it
                        if (code.length == 6) verifying = true
                    }
                },
                onDelete = { if (!verified) code = code.dropLast(1) },
                enabled = !verifying && !verified,
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Headline(
            "Verifikasi OTP",
            titleColor = Palette.Primary,
            subtitleRich = buildAnnotatedString {
                append("$sentVia ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Palette.Ink)) { append(target.ifBlank { "nama@email.com" }) }
            },
        )
        Spacer(Modifier.height(28.dp))
        CodeBoxes(code = code, error = error, verified = verified)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                verified -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = Palette.Good, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Kode terverifikasi! Mengalihkan...", color = Palette.Good, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                verifying -> Text("Memverifikasi…", color = Palette.InkSecondary, fontSize = 13.sp)
                error -> ErrorLine("Kode salah (gunakan $demoCode). Coba lagi.")
                seconds > 0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = Palette.InkSoft, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildAnnotatedString {
                                append("Kirim ulang kode dalam ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.Ink, fontFamily = FontFamily.Monospace)) {
                                    append("0:${seconds.toString().padStart(2, '0')}")
                                }
                            },
                            color = Palette.InkSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
                else -> TextLink("Kirim ulang kode sekarang", { resendRound++ })
            }
        }
    }
}

@ScreenPreview
@Composable
private fun EmailOtpPreview() = ReidigTheme {
    OtpScreen("Verifikasi email", 2 to 3, "Masukkan 6 digit kode yang kami kirim ke", "sari@gmail.com", DEMO_EMAIL_OTP, listOf("Buat akun", "Verifikasi", "Data diri"), {}) {}
}

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
        title = "",
        onBack = onBack,
        step = 3 to 3,
        stepLabels = listOf("Buat akun", "Verifikasi", "Data diri"),
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Sebelumnya", onBack, modifier = Modifier.weight(1f))
                PrimaryButton(
                    "Selesai",
                    {
                        signup.name = name.trim()
                        signup.dob = dob
                        env.repo.register(name, signup.email)
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = nameValid && dobValid && Rules.isValidPassword(password) && passwordMatch,
                )
            }
        },
    ) {
        Spacer(Modifier.height(6.dp))
        Headline(
            "Lengkapi data diri",
            "Buat nama lengkap dan kata sandi untuk mengamankan akunmu.",
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(14.dp))
        VerifiedBanner(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(signup.email.ifBlank { "nama@email.com" }) }
                append(" terverifikasi")
            },
        )
        Spacer(Modifier.height(16.dp))
        FormField(
            label = "Nama lengkap",
            value = name,
            onValueChange = { name = it; nameTouched = true },
            placeholder = "Sesuai KTP",
            leadingIcon = Icons.Outlined.PersonOutline,
            hint = "Sesuai KTP.",
            error = if (nameTouched && !nameValid) "Masukkan nama lengkap." else null,
        )
        Spacer(Modifier.height(14.dp))
        FormField(
            label = "Tanggal lahir",
            value = dobDigits,
            onValueChange = { dobDigits = it.filter { c -> c.isDigit() }.take(8) },
            placeholder = "HH/BB/TTTT",
            keyboardType = KeyboardType.Number,
            leadingIcon = Icons.Outlined.CalendarToday,
            transformation = DateTransformation,
            hint = "Sesuai KTP. Usia minimal 18 tahun.",
            error = if (dob.length == 10 && !dobValid) "Tanggal tidak valid atau usia di bawah 18 tahun." else null,
        )
        Spacer(Modifier.height(14.dp))
        PasswordField("Kata sandi", password, { password = it })
        RuleLine("Minimal 8 karakter", Rules.hasMinLength(password))
        RuleLine("Kombinasi huruf dan angka", Rules.hasLettersAndNumbers(password))
        Spacer(Modifier.height(14.dp))
        PasswordField(
            "Konfirmasi kata sandi",
            confirm,
            { confirm = it },
            placeholder = "Ketik ulang kata sandi",
            error = if (confirm.isNotEmpty() && !passwordMatch) "Kata sandi tidak cocok" else null,
        )
    }
}

@ScreenPreview
@Composable
private fun RegisterProfilePreview() = ReidigTheme { RegisterProfileScreen(rememberPreviewEnv(false), previewSignup(), {}, {}) }

/** Layar 06 Selesai: menampilkan lencana cek ganda dan kartu Tabungan Emas ungu tua sesuai prototipe. */
@Composable
fun RegisterDoneScreen(env: AppEnv, signup: SignupDraft, onStart: () -> Unit) {
    val state by env.repo.state.collectAsStateWithLifecycle()
    val firstName = signup.name.substringBefore(' ').ifBlank { state.user?.name?.substringBefore(' ') ?: "Sobat" }
    val buyPrice = state.sku("LMRE-1G").buyPrice

    Column(
        Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        DoubleRingSuccessBadge()
        Spacer(Modifier.height(24.dp))
        Text(
            "Selamat datang, $firstName!",
            color = Palette.Primary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Akunmu sudah aktif. Yuk mulai perjalanan nabung emasmu sekarang.",
            color = Palette.InkSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
        HeroPurpleCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tabungan Emas", color = Palette.Primary200, fontSize = 12.sp)
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Palette.GoldSoft).padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text("Siap diisi", color = Palette.GoldDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "0,0000 gr",
                    color = Palette.Gold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Harga beli hari ini", color = Palette.Primary200, fontSize = 12.sp)
                    Text(
                        "${rupiah(buyPrice)}/gr",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Mulai jelajahi", onStart)
    }
}

@ScreenPreview
@Composable
private fun RegisterDonePreview() = ReidigTheme { RegisterDoneScreen(rememberPreviewEnv(false), previewSignup()) {} }

const val DEMO_EMAIL_OTP = MockRepository.DEMO_EMAIL_OTP
