package com.reidig.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.reidig.shared.GoldToken
import com.reidig.shared.InstallmentApplication
import com.reidig.shared.InstallmentContract
import com.reidig.shared.MockRepository
import com.reidig.shared.PrintMethod
import com.reidig.shared.PrintOrder
import com.reidig.shared.gram
import com.reidig.shared.rupiah

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Beranda", Icons.Outlined.Home),
    Tab("catalog", "Beli", Icons.Outlined.ShoppingBag),
    Tab("portfolio", "Portofolio", Icons.Outlined.Inventory2),
    Tab("wallet", "Saldo", Icons.Outlined.AccountBalanceWallet),
    Tab("account", "Akun", Icons.Outlined.Person),
)

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
                    WelcomeScreen(onLogin = { nav.navigate("login") }, onRegister = { nav.navigate("register/email") })
                }
                composable("login") {
                    LoginScreen(env, ::enterMain, onRegister = { nav.navigate("register/email") }, onForgot = { nav.navigate("forgot") })
                }
                composable("forgot") { ForgotPasswordScreen { nav.popBackStack() } }
                composable("register/email") {
                    RegisterEmailScreen(
                        signup,
                        onBack = { nav.popBackStack() },
                        onSent = { nav.navigate("register/otp") },
                        onGoogle = {
                            repo.loginWithGoogle()
                            enterMain()
                        },
                    )
                }
                composable("register/otp") {
                    OtpScreen("Verifikasi email", 2 to 3, "Dikirim ke ${signup.email}", DEMO_EMAIL_OTP, { nav.popBackStack() }) {
                        nav.navigate("register/profile") { popUpTo("register/email") }
                    }
                }
                composable("register/profile") {
                    RegisterProfileScreen(env, signup, { nav.popBackStack() }, ::enterMain)
                }

                composable("kyc/consent") { KycConsentScreen({ nav.popBackStack() }) { nav.navigate("kyc/phone") } }
                composable("kyc/phone") { KycPhoneScreen(kyc, { nav.popBackStack() }) { nav.navigate("kyc/channel") } }
                composable("kyc/channel") { KycChannelScreen(kyc, { nav.popBackStack() }) { nav.navigate("kyc/otp") } }
                composable("kyc/otp") {
                    OtpScreen("Verifikasi HP", 2 to 5, "Dikirim lewat ${kyc.channel} ke +62 ${kyc.phone}", DEMO_PHONE_OTP, { nav.popBackStack() }) {
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
                        onSell = { nav.navigate("sell") },
                        onPrint = { nav.navigate("print?token=") },
                        onInstallment = { requireVerified("installment") },
                        onWithdraw = { nav.navigate("withdraw") },
                        onVerify = { nav.navigate("kyc/consent") },
                        onPrices = { nav.navigate("prices") },
                    )
                }
                composable("prices") { PriceTableScreen(env) { nav.popBackStack() } }
                composable("catalog") { CatalogScreen(env) { code -> requireVerified("buy/$code") } }
                composable("buy/{sku}", listOf(navArgument("sku") { type = NavType.StringType })) { entry ->
                    BuyScreen(env, entry.arguments!!.getString("sku")!!, { nav.popBackStack() }) { minted ->
                        results.minted = minted
                        nav.navigate("buy-success") { popUpTo("catalog") }
                    }
                }
                composable("buy-success") {
                    SuccessScreen(
                        "Pembelian berhasil",
                        "Token sudah terbit untuk keping ini.",
                        "Lihat portofolio",
                        { nav.switchTab("portfolio") },
                    ) {
                        results.minted.forEach { token ->
                            SurfaceCard(Modifier.padding(bottom = 10.dp)) {
                                Text(gram(state.sku(token.skuCode).weightMg), fontWeight = FontWeight.Bold)
                                Text("Seri ${token.serial}", color = Palette.InkSoft, fontSize = 13.sp)
                                TokenCodeText(token.code)
                            }
                        }
                    }
                }
                composable("portfolio") {
                    PortfolioScreen(
                        env,
                        onToken = { nav.navigate("token/$it") },
                        onPrint = { nav.navigate("print?token=") },
                        onBuy = { nav.switchTab("catalog") },
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
                composable("sell") {
                    SellPickerScreen(env, { nav.popBackStack() }) { nav.navigate("token/$it") }
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
                composable("wallet") { WalletScreen(env) { nav.navigate("withdraw") } }
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
                            // Setelah diajukan, tombol kembali tidak lagi membuka perjanjian.
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
            if (TABS.any { it.route == route }) {
                NavigationBar(containerColor = Color.White) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Primary,
                                selectedTextColor = Palette.Primary,
                                indicatorColor = Palette.Ring,
                            ),
                        )
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp).fillMaxWidth(),
        ) { data ->
            Snackbar(data, containerColor = Palette.Night, contentColor = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
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
