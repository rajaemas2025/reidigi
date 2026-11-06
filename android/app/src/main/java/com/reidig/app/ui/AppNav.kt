package com.reidig.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reidig.app.R
import com.reidig.shared.GoldToken
import com.reidig.shared.InstallmentApplication
import com.reidig.shared.InstallmentContract
import com.reidig.shared.MockRepository
import com.reidig.shared.PrintMethod
import com.reidig.shared.PrintOrder
import com.reidig.shared.gram
import com.reidig.shared.rupiah

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Painter)

private val TABS = listOf(
    Tab("home", "Beranda") { rememberVectorPainter(Icons.Outlined.Home) },
    Tab("wallet", "Dompet") { rememberVectorPainter(Icons.Outlined.AccountBalanceWallet) },
    Tab("catalog", "Transaksi") { rememberVectorPainter(Icons.Filled.SwapHoriz) },
    Tab("print-tab", "Cetak") { painterResource(R.drawable.ic_nav_gold) },
    Tab("account", "Profil") { rememberVectorPainter(Icons.Outlined.Person) },
)

/** Tab yang sedang menyala; satu tab bisa berisi dua halaman. */
private fun activeTab(route: String?): String? = when (route) {
    "portfolio" -> "wallet"
    "sell-tab" -> "catalog"
    else -> route?.takeIf { current -> TABS.any { it.route == current } }
}

/** Navigasi bawah sesuai prototipe Goldin v1, dengan tombol Transaksi melayang di tengah. */
@Composable
private fun ReidigBottomBar(active: String, onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().background(Palette.Paper)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp)
                .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TABS.forEach { tab ->
                val selected = tab.route == active
                val tint = if (selected) Palette.Primary else Palette.DisabledText
                Column(
                    Modifier.weight(1f).clickable(role = Role.Tab) { onSelect(tab.route) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (tab.route == "catalog") {
                        Spacer(Modifier.height(26.dp))
                    } else {
                        Icon(tab.icon(), null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
        val center = TABS.first { it.route == "catalog" }
        val centerActive = active == "catalog"
        Box(
            Modifier.align(Alignment.TopCenter)
                .size(56.dp)
                .then(
                    if (centerActive) Modifier.border(4.dp, Palette.Primary200, CircleShape)
                    else Modifier
                )
                .shadow(8.dp, CircleShape, ambientColor = Palette.Primary, spotColor = Palette.Primary)
                .clip(CircleShape)
                .background(Palette.Primary)
                .clickable(role = Role.Tab, onClickLabel = center.label) { onSelect(center.route) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(center.icon(), center.label, tint = Palette.Primary50, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun ReidigApp(repo: MockRepository) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val env = remember { AppEnv(repo, snackbar, scope) }
    val nav = rememberNavController()
    val signup = remember { SignupDraft() }
    val kyc = remember { KycDraft() }
    // Hasil transaksi terakhir untuk layar sukses.
    val results = remember { TransactionResults() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val state by repo.state.collectAsStateWithLifecycle()

    fun enterMain() = nav.navigate("home") { popUpTo(0) { inclusive = true } }
    fun requireVerified(next: String) {
        if (state.user?.verified == true) nav.navigate(next) else nav.navigate("kyc/consent")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = "splash", modifier = Modifier.weight(1f)) {
                composable("splash") {
                    SplashScreen { nav.navigate("welcome") { popUpTo("splash") { inclusive = true } } }
                }
                composable("welcome") {
                    WelcomeScreen(env, onLogin = { nav.navigate("login") }, onRegister = { nav.navigate("register/email") })
                }
                composable("login") {
                    LoginScreen(
                        env,
                        onBack = { nav.popBackStack() },
                        onLoggedIn = ::enterMain,
                        onRegister = { nav.navigate("register/email") },
                        onForgot = { nav.navigate("forgot") },
                        onGoogle = { nav.navigate("google") },
                    )
                }
                composable("forgot") { ForgotPasswordScreen { nav.popBackStack() } }
                composable("register/email") {
                    RegisterEmailScreen(
                        signup,
                        onBack = { nav.popBackStack() },
                        onSent = { nav.navigate("register/otp") },
                        onGoogle = { nav.navigate("google") },
                    )
                }
                composable("google") {
                    GoogleAccountScreen(
                        onBack = { nav.popBackStack() },
                        onSelected = {
                            repo.loginWithGoogle()
                            enterMain()
                        },
                    )
                }
                composable("register/otp") {
                    OtpScreen(
                        title = "Verifikasi email",
                        step = 2 to 3,
                        sentVia = "Masukkan 6 digit kode yang kami kirim ke",
                        target = signup.email,
                        demoCode = DEMO_EMAIL_OTP,
                        stepLabels = listOf("Buat akun", "Verifikasi", "Data diri"),
                        onBack = { nav.popBackStack() },
                    ) {
                        nav.navigate("register/profile") { popUpTo("register/email") }
                    }
                }
                composable("register/profile") {
                    RegisterProfileScreen(
                        env,
                        signup,
                        onBack = { nav.popBackStack() },
                        onDone = { nav.navigate("register/done") { popUpTo("welcome") } },
                    )
                }
                composable("register/done") {
                    RegisterDoneScreen(env, signup, onStart = ::enterMain)
                }

                composable("kyc/consent") { KycConsentScreen({ nav.popBackStack() }) { nav.navigate("kyc/phone") } }
                composable("kyc/phone") { KycPhoneScreen(kyc, { nav.popBackStack() }) { nav.navigate("kyc/channel") } }
                composable("kyc/channel") { KycChannelScreen(kyc, { nav.popBackStack() }) { nav.navigate("kyc/otp") } }
                composable("kyc/otp") {
                    OtpScreen(
                        title = "Verifikasi nomor HP",
                        step = 2 to 5,
                        sentVia = "Kode OTP dikirim lewat ${kyc.channel} ke",
                        target = "+62 ${kyc.phone}",
                        demoCode = DEMO_PHONE_OTP,
                        onBack = { nav.popBackStack() },
                    ) {
                        nav.navigate("kyc/scan") { popUpTo("kyc/phone") }
                    }
                }
                composable("kyc/scan") { KycScanIdScreen({ nav.popBackStack() }) { nav.navigate("kyc/review") } }
                composable("kyc/review") {
                    KycReviewScreen(signup, kyc, { nav.popBackStack() }, onRescan = { nav.popBackStack() }) { nav.navigate("kyc/liveness") }
                }
                composable("kyc/liveness") { KycLivenessIntroScreen({ nav.popBackStack() }) { nav.navigate("kyc/face") } }
                composable("kyc/face") {
                    KycFaceScanScreen({ nav.popBackStack() }) { nav.navigate("kyc/pin") { popUpTo("kyc/liveness") } }
                }
                composable("kyc/pin") {
                    KycPinScreen(env, kyc, { nav.popBackStack() }) { nav.navigate("kyc/done") { popUpTo("home") } }
                }
                composable("kyc/done") { KycVerifiedScreen { nav.navigate("catalog") { popUpTo("home") } } }

                composable("home") {
                    HomeScreen(
                        env,
                        onBuy = { nav.switchTab("catalog") },
                        onSell = { nav.switchTab("sell-tab") },
                        onPrint = { nav.switchTab("print-tab") },
                        onInstallment = { requireVerified("installment") },
                        onWithdraw = { nav.switchTab("wallet") },
                        onVerify = { nav.navigate("kyc/consent") },
                        onPrices = { nav.navigate("prices") },
                    )
                }
                composable("prices") { PriceTableScreen(env) { nav.popBackStack() } }
                composable("catalog") {
                    CatalogScreen(env, header = { TransactionSwitch(0) { nav.switchTab("sell-tab") } }) { code -> requireVerified("buy/$code") }
                }
                composable("buy/{sku}", listOf(navArgument("sku") { type = NavType.StringType })) { entry ->
                    BuyScreen(env, entry.arguments!!.getString("sku")!!, { nav.popBackStack() }) { minted ->
                        results.minted = minted
                        nav.navigate("buy-success") { popUpTo("catalog") }
                    }
                }
                composable("buy-success") {
                    SuccessScreen(
                        "Pembelian berhasil!",
                        "Emasmu sudah tersimpan di brankas dan tercatat dalam portofolio.",
                        "Lihat portofolio",
                        { nav.switchTab("portfolio") },
                    ) {
                        results.minted.forEach { token ->
                            SurfaceCard(Modifier.padding(bottom = 10.dp)) {
                                SummaryRow("Keping emas", "${state.sku(token.skuCode).product} ${gram(state.sku(token.skuCode).weightMg)}", strong = true)
                                SummaryRow("Nomor seri", token.serial)
                                SummaryRow("Kode token", token.code)
                            }
                        }
                    }
                }
                composable("portfolio") {
                    PortfolioScreen(
                        env,
                        onToken = { nav.navigate("token/$it") },
                        onPrint = { nav.switchTab("print-tab") },
                        onBuy = { nav.switchTab("catalog") },
                        header = { WalletSwitch(1) { nav.switchTab("wallet") } },
                    )
                }
                composable("token/{code}", listOf(navArgument("code") { type = NavType.StringType })) { entry ->
                    TokenDetailScreen(
                        env,
                        entry.arguments!!.getString("code")!!,
                        onBack = { nav.popBackStack() },
                        onSold = { nav.switchTab("wallet") },
                        onPrint = { nav.navigate("print?token=$it") },
                    )
                }
                composable("sell-tab") {
                    SellPickerScreen(
                        env,
                        null,
                        { nav.navigate("token/$it") },
                        title = "Transaksi",
                        header = {
                            TransactionSwitch(1, inset = 0.dp) { nav.switchTab("catalog") }
                            Spacer(Modifier.height(14.dp))
                        },
                    )
                }
                composable("print-tab") {
                    PrintScreen(env, null, null) { order ->
                        results.printOrder = order
                        nav.navigate("print-success") { popUpTo("home") }
                    }
                }
                composable("print?token={token}", listOf(navArgument("token") { type = NavType.StringType; defaultValue = "" })) { entry ->
                    PrintScreen(env, entry.arguments?.getString("token")?.ifEmpty { null }, { nav.popBackStack() }) { order ->
                        results.printOrder = order
                        nav.navigate("print-success") { popUpTo("home") }
                    }
                }
                composable("print-success") {
                    val order = results.printOrder
                    SuccessScreen(
                        "Pesanan cetak dibuat",
                        if (order?.method == PrintMethod.BRANCH) {
                            "Keping dikirim dulu dari gudang Jakarta. Kami kabari saat bisa diambil. Bawa KTP dan kode pengambilan."
                        } else {
                            "Keping sedang disiapkan untuk dikirim dengan JNE."
                        },
                        "Lihat portofolio",
                        { nav.switchTab("portfolio") },
                    ) {
                        order?.let { PrintOrderCard(it) }
                    }
                }
                composable("wallet") {
                    WalletScreen(
                        env,
                        header = { WalletSwitch(0) { nav.switchTab("portfolio") } },
                        onPortfolio = { nav.switchTab("portfolio") },
                        onWithdraw = { nav.navigate("withdraw") },
                    )
                }
                composable("withdraw") {
                    WithdrawScreen(env, { nav.popBackStack() }) { nav.switchTab("wallet") }
                }
                composable("installment") {
                    InstallmentScreen(env, { nav.popBackStack() }) { draft ->
                        results.installmentDraft = draft
                        nav.navigate("installment-agreement")
                    }
                }
                composable("installment-agreement") {
                    results.installmentDraft?.let { draft ->
                        InstallmentAgreementScreen(env, draft, { nav.popBackStack() }) { application ->
                            results.application = application
                            nav.navigate("installment-pay") { popUpTo("home") }
                        }
                    }
                }
                composable("installment-pay") {
                    results.application?.let { application ->
                        DownPaymentScreen(env, application, onCancelled = { nav.popBackStack("home", false) }) { contract ->
                            results.contract = contract
                            nav.navigate("installment-success") { popUpTo("home") }
                        }
                    }
                }
                composable("installment-success") {
                    SuccessScreen(
                        "Uang muka diterima",
                        "Cicilan Anda aktif. Token terkunci sampai lunas.",
                        "Lihat kontrak",
                        { nav.switchTab("account") },
                    ) {
                        results.contract?.let { contract ->
                            SurfaceCard {
                                SummaryRow("Kontrak", contract.id)
                                SummaryRow("Uang muka", rupiah(contract.downPayment))
                                SummaryRow("Angsuran per bulan", rupiah(contract.monthlyBeforeFees), strong = true)
                                SummaryRow("Tenor", "${contract.tenorMonths} bulan")
                            }
                        }
                    }
                }
                composable("account") {
                    AccountScreen(
                        env,
                        onVerify = { nav.navigate("kyc/consent") },
                        onInstallments = { requireVerified("installment") },
                        onLogout = {
                            repo.logout()
                            nav.navigate("welcome") { popUpTo(0) { inclusive = true } }
                        },
                    )
                }
            }
            activeTab(route)?.let { active ->
                ReidigBottomBar(active) { nav.switchTab(it) }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp).fillMaxWidth(),
        ) { data ->
            Snackbar(data, containerColor = Palette.Ink, contentColor = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun WalletSwitch(selected: Int, onSwitch: () -> Unit) {
    SegmentSwitch(listOf("Dompet Kas & Emas", "Keping Token"), selected) { onSwitch() }
}

@Composable
private fun TransactionSwitch(selected: Int, inset: Dp = 20.dp, onSwitch: () -> Unit) {
    SegmentSwitch(listOf("Beli emas", "Jual emas"), selected, inset) { onSwitch() }
}

private class TransactionResults {
    var minted: List<GoldToken> = emptyList()
    var printOrder: PrintOrder? = null
    var installmentDraft: InstallmentDraft? = null
    var application: InstallmentApplication? = null
    var contract: InstallmentContract? = null
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo("home")
        launchSingleTop = true
    }
}
