package com.reidig.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * Backend tiruan di dalam aplikasi, mengikuti docs/02-spesifikasi-backend.md.
 * Diganti klien HTTP saat API siap; layar hanya bergantung pada fungsi-fungsi ini.
 */
class MockRepository(
    private val clock: () -> Long,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val quotes = mutableMapOf<String, Quote>()
    private val pending = mutableMapOf<String, PendingOrder>()
    private val applications = mutableMapOf<String, InstallmentApplication>()
    private var pinAttempts = 0
    private var serialCounter = 500_000
    private var idCounter = 1

    val branches = listOf(
        Branch("cgk", "Cengkareng", "Jakarta Barat", "2–3 hari kerja"),
        Branch("klg", "Kelapa Gading", "Jakarta Utara", "2–3 hari kerja"),
        Branch("ptj", "Petojo", "Jakarta Pusat", "1–2 hari kerja"),
        Branch("amb", "Mal Ambasador Kuningan", "Jakarta Selatan", "1–2 hari kerja"),
    )

    private fun nextId(prefix: String) = "$prefix-${idCounter++}"

    private fun fail(code: String, message: String): Nothing = throw ReidigException(code, message)

    private fun tokenCode(sku: Sku): String {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        val suffix = (1..11).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        return "RET-${sku.code.replace("-", "").replace(".", "")}-$suffix"
    }

    private fun newSerial(sku: Sku) = "${sku.code.replace("-", "").replace(".", "")}-${serialCounter++}"

    // ----- Akun -----

    /** Akun contoh yang sudah terverifikasi, dengan token dan saldo. */
    fun login(email: String, password: String) {
        if (!Rules.isValidEmail(email) || password.isEmpty()) fail("INVALID_CREDENTIALS", "Email atau kata sandi salah. Coba lagi.")
        startDemoAccount(email.trim())
    }

    fun loginWithGoogle() = startDemoAccount("pengguna@gmail.com")

    private fun startDemoAccount(email: String) {
        val now = clock()
        val threeDaysAgo = now - 3 * Rules.HOLDING_PERIOD_MS
        _state.update { current ->
            val oneGram = current.sku("LMRE-1G")
            val halfGram = current.sku("LMRE-0.5G")
            val tokens = listOf(oneGram, oneGram, halfGram).map { sku ->
                GoldToken(tokenCode(sku), sku.code, newSerial(sku), TokenStatus.ACTIVE, sku.buyPrice, threeDaysAgo)
            }
            current.copy(
                user = UserProfile("Pengguna", email, verified = true),
                pin = DEMO_PIN,
                walletBalance = 2_310_000,
                tokens = tokens,
                bankAccounts = listOf(BankAccount("bca", "BCA", "•••• 1234", "Pengguna")),
                transactions = tokens.map {
                    Tx(nextId("TX"), TxType.BUY, "Beli ${current.sku(it.skuCode).product} ${gram(current.sku(it.skuCode).weightMg)}", -it.acquiredPrice, threeDaysAgo)
                },
            )
        }
    }

    fun register(name: String, email: String) {
        _state.update {
            it.copy(user = UserProfile(name.trim(), email.trim(), verified = false), pin = null, walletBalance = 0, tokens = emptyList(), transactions = emptyList(), bankAccounts = emptyList())
        }
    }

    fun logout() {
        pinAttempts = 0
        pending.clear()
        applications.clear()
        _state.value = initialState()
    }

    fun completeKyc(pin: String, name: String) {
        if (Rules.isWeakPin(pin)) fail("WEAK_PIN", "PIN terlalu mudah ditebak. Hindari angka berulang dan berurutan.")
        _state.update { state ->
            state.copy(
                pin = pin,
                user = state.user?.copy(verified = true),
                bankAccounts = state.bankAccounts.ifEmpty { listOf(BankAccount("bca", "BCA", "•••• 1234", name.trim())) },
            )
        }
    }

    fun verifyPin(pin: String): Boolean {
        if (pinAttempts >= Rules.MAX_PIN_ATTEMPTS) fail("PIN_LOCKED", "PIN terkunci karena terlalu banyak percobaan. Hubungi layanan pelanggan.")
        if (pin == _state.value.pin) {
            pinAttempts = 0
            return true
        }
        pinAttempts++
        return false
    }

    // ----- Beli -----

    fun quoteBuy(skuCode: String, quantity: Int): Quote {
        val sku = _state.value.sku(skuCode)
        if (quantity < 1) fail("INVALID", "Jumlah minimal 1 keping.")
        if (sku.stock < quantity) fail("INSUFFICIENT_STOCK", "Stok ${sku.code} tidak mencukupi.")
        val quote = Quote(
            id = nextId("QT"),
            skuCode = skuCode,
            quantity = quantity,
            unitPrice = sku.buyPrice,
            total = sku.buyPrice * quantity,
            buybackValueNow = sku.buybackPrice * quantity,
            expiresAtMs = clock() + Rules.QUOTE_TTL_MS,
        )
        quotes[quote.id] = quote
        return quote
    }

    /** Bayar langsung dengan saldo, atau tanpa menunggu pada pengujian. */
    fun buy(quoteId: String, method: PaymentMethod): List<GoldToken> =
        confirmPayment(createOrder(quoteId, method).id)

    /** Mengunci keping dan harga. QRIS dan VA menunggu pembayaran; keping ditahan selama batas bayar. */
    fun createOrder(quoteId: String, method: PaymentMethod): PendingOrder {
        val quote = validQuote(quoteId)
        val state = _state.value
        val user = state.user ?: fail("UNAUTHENTICATED", "Silakan masuk terlebih dahulu.")
        if (!user.verified) fail("KYC_REQUIRED", "Verifikasi identitas diperlukan sebelum membeli.")
        val sku = state.sku(quote.skuCode)
        if (sku.stock < quote.quantity) fail("INSUFFICIENT_STOCK", "Stok ${sku.code} tidak mencukupi.")
        if (method == PaymentMethod.WALLET && state.walletBalance < quote.total) {
            fail("INSUFFICIENT_BALANCE", "Saldo tidak mencukupi.")
        }
        val code = when (method) {
            PaymentMethod.WALLET -> ""
            PaymentMethod.QRIS -> "QR-" + random.nextInt(100000, 999999)
            PaymentMethod.VIRTUAL_ACCOUNT -> "70070" + random.nextLong(100000000L, 999999999L)
        }
        val order = PendingOrder(nextId("ORD"), quote, method, clock() + Rules.PAYMENT_WINDOW_MS, code)
        adjustStock(sku.code, -quote.quantity)
        pending[order.id] = order
        quotes.remove(quoteId)
        return order
    }

    /** Dalam produksi dipicu callback iFortepay yang tanda tangannya sudah diverifikasi. */
    fun confirmPayment(orderId: String): List<GoldToken> {
        val order = pending[orderId] ?: fail("ORDER_NOT_FOUND", "Pesanan tidak ditemukan.")
        if (clock() > order.payBeforeMs) {
            cancelOrder(orderId)
            fail("ORDER_EXPIRED", "Batas waktu pembayaran habis. Keping dilepas kembali.")
        }
        val state = _state.value
        val quote = order.quote
        val sku = state.sku(quote.skuCode)
        if (order.method == PaymentMethod.WALLET && state.walletBalance < quote.total) {
            cancelOrder(orderId)
            fail("INSUFFICIENT_BALANCE", "Saldo tidak mencukupi.")
        }
        val now = clock()
        val minted = List(quote.quantity) {
            GoldToken(tokenCode(sku), sku.code, newSerial(sku), TokenStatus.ACTIVE, quote.unitPrice, now)
        }
        _state.update { s ->
            s.copy(
                walletBalance = if (order.method == PaymentMethod.WALLET) s.walletBalance - quote.total else s.walletBalance,
                tokens = minted + s.tokens,
                transactions = listOf(Tx(nextId("TX"), TxType.BUY, "Beli ${quote.quantity} × ${sku.product} ${gram(sku.weightMg)}", -quote.total, now)) + s.transactions,
            )
        }
        pending.remove(orderId)
        return minted
    }

    fun cancelOrder(orderId: String) {
        val order = pending.remove(orderId) ?: return
        adjustStock(order.quote.skuCode, order.quote.quantity)
    }

    private fun adjustStock(skuCode: String, delta: Int) {
        _state.update { s -> s.copy(skus = s.skus.map { if (it.code == skuCode) it.copy(stock = it.stock + delta) else it }) }
    }

    private fun validQuote(id: String): Quote {
        val quote = quotes[id] ?: fail("QUOTE_EXPIRED", "Harga sudah berubah. Silakan ulangi.")
        if (clock() > quote.expiresAtMs) {
            quotes.remove(id)
            fail("QUOTE_EXPIRED", "Batas waktu harga habis. Silakan ulangi.")
        }
        return quote
    }

    // ----- Jual -----

    /** Token kembali ke sistem dan keping tersedia lagi; dana masuk saldo. */
    fun sell(tokenCode: String): Long {
        val state = _state.value
        val token = token(tokenCode)
        if (token.status != TokenStatus.ACTIVE) fail("TOKEN_LOCKED", "Token ini sedang terkunci dan tidak bisa dijual.")
        if (!Rules.canSellAt(token, clock())) fail("HOLDING_PERIOD_ACTIVE", "Token baru bisa dijual 24 jam setelah pembelian.")
        val sku = state.sku(token.skuCode)
        _state.update { s ->
            s.copy(
                walletBalance = s.walletBalance + sku.buybackPrice,
                skus = s.skus.map { if (it.code == sku.code) it.copy(stock = it.stock + 1) else it },
                tokens = s.tokens.map { if (it.code == tokenCode) it.copy(status = TokenStatus.RETURNED) else it },
                transactions = listOf(Tx(nextId("TX"), TxType.SELL, "Jual ${sku.product} ${gram(sku.weightMg)}", sku.buybackPrice, clock())) + s.transactions,
            )
        }
        return sku.buybackPrice
    }

    private fun token(code: String): GoldToken =
        _state.value.tokens.firstOrNull { it.code == code } ?: fail("TOKEN_NOT_OWNED", "Token tidak ditemukan.")

    // ----- Saldo -----

    fun withdraw(amount: Long, bankAccountId: String) {
        val state = _state.value
        val account = state.bankAccounts.firstOrNull { it.id == bankAccountId } ?: fail("INVALID", "Pilih rekening tujuan.")
        if (amount <= 0) fail("INVALID", "Masukkan nominal penarikan.")
        if (amount > state.walletBalance) fail("INSUFFICIENT_BALANCE", "Saldo tidak mencukupi.")
        _state.update { s ->
            s.copy(
                walletBalance = s.walletBalance - amount,
                transactions = listOf(Tx(nextId("TX"), TxType.WITHDRAW, "Tarik dana ke ${account.bank} ${account.number}", -amount, clock())) + s.transactions,
            )
        }
    }

    // ----- Cetak -----

    fun createPrintOrder(tokenCodes: List<String>, method: PrintMethod, address: String?, branchId: String?): PrintOrder {
        val state = _state.value
        if (tokenCodes.isEmpty()) fail("INVALID", "Pilih minimal satu token.")
        val tokens = tokenCodes.map { token(it) }
        if (tokens.any { it.status != TokenStatus.ACTIVE }) fail("TOKEN_LOCKED", "Ada token yang tidak bisa dicetak saat ini.")
        val total = tokens.sumOf { state.sku(it.skuCode).weightMg }
        val destination: String
        val estimate: String
        val status: PrintStatus
        when (method) {
            PrintMethod.BRANCH -> {
                if (!Rules.isPickupEligible(total)) {
                    fail("PICKUP_MIN_WEIGHT_NOT_MET", "Ambil di cabang memerlukan total minimal 2 gr. Pilih JNE atau tambah token.")
                }
                val branch = branches.firstOrNull { it.id == branchId } ?: fail("INVALID", "Pilih cabang pengambilan.")
                destination = "Cabang ${branch.name}"
                estimate = branch.estimate
                status = PrintStatus.WAITING
            }
            PrintMethod.JNE -> {
                if (address.isNullOrBlank()) fail("INVALID", "Alamat pengiriman wajib diisi.")
                destination = address.trim()
                estimate = "3–5 hari kerja"
                status = PrintStatus.WAITING
            }
        }
        val order = PrintOrder(nextId("CTK"), tokenCodes, method, destination, status, estimate, clock())
        _state.update { s ->
            s.copy(
                tokens = s.tokens.map { if (it.code in tokenCodes) it.copy(status = TokenStatus.PRINT_PENDING) else it },
                printOrders = listOf(order) + s.printOrders,
                transactions = listOf(Tx(nextId("TX"), TxType.PRINT, "Cetak ${gram(total)} ke $destination", 0, clock())) + s.transactions,
            )
        }
        return order
    }

    // ----- Cicil -----

    /** Mencatat persetujuan perjanjian dan menahan satu keping sampai uang muka dibayar. */
    fun submitInstallment(
        skuCode: String,
        downPayment: Long,
        tenorMonths: Int,
        expectedPrice: Long,
        agreementVersion: String,
    ): InstallmentApplication {
        val state = _state.value
        val sku = state.sku(skuCode)
        if (state.user?.verified != true) fail("KYC_REQUIRED", "Verifikasi identitas diperlukan sebelum mencicil.")
        if (agreementVersion != Rules.INSTALLMENT_AGREEMENT_VERSION) fail("AGREEMENT_REQUIRED", "Setujui perjanjian cicilan terlebih dahulu.")
        if (sku.buyPrice != expectedPrice) fail("PRICE_CHANGED", "Harga sudah berubah. Silakan periksa lagi perjanjiannya.")
        if (downPayment <= 0 || downPayment >= sku.buyPrice || tenorMonths <= 0) fail("INVALID", "Periksa kembali uang muka dan tenor.")
        if (sku.stock < 1) fail("INSUFFICIENT_STOCK", "Stok ${sku.code} tidak mencukupi.")
        val now = clock()
        val application = InstallmentApplication(
            nextId("AJU"),
            skuCode,
            sku.buyPrice,
            downPayment,
            tenorMonths,
            agreementVersion,
            now,
            now + Rules.PAYMENT_WINDOW_MS,
        )
        adjustStock(skuCode, -1)
        applications[application.id] = application
        return application
    }

    /** Kode QRIS atau VA untuk uang muka. */
    fun downPaymentCode(method: PaymentMethod): String = when (method) {
        PaymentMethod.WALLET -> ""
        PaymentMethod.QRIS -> "QR-" + random.nextInt(100000, 999999)
        PaymentMethod.VIRTUAL_ACCOUNT -> "70070" + random.nextLong(100000000L, 999999999L)
    }

    /** Token terkunci baru terbit setelah uang muka lunas. */
    fun payDownPayment(applicationId: String, method: PaymentMethod): InstallmentContract {
        val application = applications[applicationId] ?: fail("ORDER_NOT_FOUND", "Pengajuan tidak ditemukan.")
        if (clock() > application.payBeforeMs) {
            cancelInstallment(applicationId)
            fail("ORDER_EXPIRED", "Batas waktu pembayaran habis. Pengajuan dibatalkan.")
        }
        val state = _state.value
        if (method == PaymentMethod.WALLET && state.walletBalance < application.downPayment) {
            fail("INSUFFICIENT_BALANCE", "Saldo tidak mencukupi.")
        }
        val sku = state.sku(application.skuCode)
        val token = GoldToken(tokenCode(sku), sku.code, newSerial(sku), TokenStatus.LOCKED_INSTALLMENT, application.price, clock())
        val contract = InstallmentContract(
            nextId("MTR"),
            "Mitra pembiayaan",
            token.code,
            application.price,
            application.downPayment,
            application.tenorMonths,
            0,
            application.agreementVersion,
            application.agreedAtMs,
        )
        _state.update { s ->
            s.copy(
                walletBalance = if (method == PaymentMethod.WALLET) s.walletBalance - application.downPayment else s.walletBalance,
                tokens = listOf(token) + s.tokens,
                contracts = listOf(contract) + s.contracts,
                transactions = listOf(Tx(nextId("TX"), TxType.INSTALLMENT, "Uang muka cicil ${sku.product} ${gram(sku.weightMg)}", -application.downPayment, clock())) + s.transactions,
            )
        }
        applications.remove(applicationId)
        return contract
    }

    fun cancelInstallment(applicationId: String) {
        val application = applications.remove(applicationId) ?: return
        adjustStock(application.skuCode, 1)
    }

    /** Dalam produksi dipicu webhook lunas dari mitra. */
    fun markInstallmentPaidOff(contractId: String) {
        _state.update { s ->
            val contract = s.contracts.first { it.id == contractId }
            s.copy(
                tokens = s.tokens.map { if (it.code == contract.tokenCode) it.copy(status = TokenStatus.ACTIVE) else it },
                contracts = s.contracts.map { if (it.id == contractId) it.copy(paidMonths = it.tenorMonths) else it },
            )
        }
    }

    companion object {
        const val DEMO_PIN = "147258"
        const val DEMO_EMAIL_OTP = "654321"
        const val DEMO_PHONE_OTP = "123456"
        private const val BUYBACK_PER_GRAM = 2_310_000L

        /** Harga dari halaman publik emasmurniasli.co.id, 3 November 2026. */
        private fun sku(code: String, product: String, weightMg: Long, buyPrice: Long, stock: Int) =
            Sku(code, product, weightMg, buyPrice, BUYBACK_PER_GRAM * weightMg / 1000, stock)

        private fun initialState() = AppState(
            skus = listOf(
                sku("LMRE-0.1G", "LM Raja Emas", 100, 305_000, 40),
                sku("LMRE-0.5G", "LM Raja Emas", 500, 1_308_500, 30),
                sku("LMRE-1G", "LM Raja Emas", 1_000, 2_513_500, 40),
                sku("LMRE-2G", "LM Raja Emas", 2_000, 5_007_000, 20),
                sku("LMRE-5G", "LM Raja Emas", 5_000, 12_442_500, 16),
                sku("LMRE-10G", "LM Raja Emas", 10_000, 25_135_000, 12),
                sku("LMRE-25G", "LM Raja Emas", 25_000, 61_737_500, 8),
                sku("LMRE-50G", "LM Raja Emas", 50_000, 123_375_000, 6),
                sku("LMRE-100G", "LM Raja Emas", 100_000, 246_550_000, 4),
                sku("LMRE-250G", "LM Raja Emas", 250_000, 614_265_000, 2),
                sku("LMRE-500G", "LM Raja Emas", 500_000, 1_228_320_000, 2),
                sku("LMRE-1000G", "LM Raja Emas", 1_000_000, 2_456_600_000, 1),
            ),
            buybackPerGram = BUYBACK_PER_GRAM,
            priceUpdatedAtLabel = "3 Nov 2026, 09.13 WIB",
        )
    }
}
