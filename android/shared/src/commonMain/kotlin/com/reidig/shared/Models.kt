package com.reidig.shared

enum class TokenStatus { ACTIVE, LOCKED_INSTALLMENT, PRINT_PENDING, RETURNED, PRINTED, CANCELLED }

enum class PaymentMethod(val label: String) {
    WALLET("Saldo REIDIG"),
    QRIS("QRIS"),
    VIRTUAL_ACCOUNT("Virtual Account BCA"),
}

enum class PrintMethod { JNE, BRANCH }

enum class PrintStatus(val label: String) {
    WAITING("Disiapkan gudang"),
    SHIPPED("Dikirim JNE"),
    IN_TRANSIT("Dalam perjalanan ke cabang"),
    READY_PICKUP("Siap diambil"),
    DONE("Selesai"),
}

enum class TxType(val label: String) {
    BUY("Beli emas"),
    SELL("Jual emas"),
    WITHDRAW("Tarik dana"),
    PRINT("Cetak emas"),
    INSTALLMENT("Cicil emas"),
}

data class Sku(
    val code: String,
    val product: String,
    val weightMg: Long,
    val buyPrice: Long,
    val buybackPrice: Long,
    val stock: Int,
) {
    val spreadPercent: Double get() = (buyPrice - buybackPrice) * 100.0 / buyPrice
}

data class GoldToken(
    val code: String,
    val skuCode: String,
    val serial: String,
    val status: TokenStatus,
    val acquiredPrice: Long,
    val acquiredAtMs: Long,
)

data class Quote(
    val id: String,
    val skuCode: String,
    val quantity: Int,
    val unitPrice: Long,
    val total: Long,
    /** Nilai jual kembali saat ini, ditampilkan agar pengguna tahu selisihnya. */
    val buybackValueNow: Long,
    val expiresAtMs: Long,
)

data class PendingOrder(
    val id: String,
    val quote: Quote,
    val method: PaymentMethod,
    val payBeforeMs: Long,
    /** Isi QR atau nomor VA dari iFortepay. */
    val paymentCode: String,
)

data class BankAccount(val id: String, val bank: String, val number: String, val holder: String)

data class Branch(val id: String, val name: String, val area: String, val estimate: String)

data class PrintOrder(
    val id: String,
    val tokenCodes: List<String>,
    val method: PrintMethod,
    val destination: String,
    val status: PrintStatus,
    val estimate: String,
    val createdAtMs: Long,
)

data class InstallmentContract(
    val id: String,
    val partner: String,
    val tokenCode: String,
    val price: Long,
    val downPayment: Long,
    val tenorMonths: Int,
    val paidMonths: Int,
) {
    val monthlyBeforeFees: Long get() = (price - downPayment) / tenorMonths
}

data class Tx(val id: String, val type: TxType, val title: String, val amount: Long, val atMs: Long)

data class UserProfile(val name: String, val email: String, val verified: Boolean)

data class AppState(
    val user: UserProfile? = null,
    val pin: String? = null,
    val skus: List<Sku> = emptyList(),
    val buybackPerGram: Long = 0,
    val priceUpdatedAtLabel: String = "",
    val walletBalance: Long = 0,
    val tokens: List<GoldToken> = emptyList(),
    val transactions: List<Tx> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val printOrders: List<PrintOrder> = emptyList(),
    val contracts: List<InstallmentContract> = emptyList(),
) {
    val openTokens: List<GoldToken>
        get() = tokens.filter { it.status != TokenStatus.RETURNED && it.status != TokenStatus.PRINTED && it.status != TokenStatus.CANCELLED }

    fun sku(code: String): Sku = skus.first { it.code == code }

    /** Token cicilan belum lunas tidak dihitung sebagai emas milik pengguna. */
    val ownedTokens: List<GoldToken> get() = openTokens.filter { it.status != TokenStatus.LOCKED_INSTALLMENT }

    val totalWeightMg: Long get() = ownedTokens.sumOf { sku(it.skuCode).weightMg }

    val portfolioValue: Long get() = ownedTokens.sumOf { sku(it.skuCode).buybackPrice }
}

class ReidigException(val code: String, message: String) : Exception(message)
