package com.reidig.shared

object Rules {
    const val PICKUP_MIN_WEIGHT_MG = 2_000L
    const val HOLDING_PERIOD_MS = 24L * 60 * 60 * 1000
    const val QUOTE_TTL_MS = 60_000L
    const val PAYMENT_WINDOW_MS = 15L * 60 * 1000
    const val MAX_PIN_ATTEMPTS = 5
    /** Situs menampilkan buyback di bawah nilai ini bebas PPh 22. Perlu konfirmasi konsultan pajak. */
    const val TAX_FREE_SELL_LIMIT = 10_000_000L
    /** Nonaktif sampai cakupan izin saldo terkonfirmasi. */
    const val TOPUP_ENABLED = false

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun isValidEmail(value: String) = EMAIL.matches(value.trim())

    fun hasMinLength(password: String) = password.length >= 8

    fun hasLettersAndNumbers(password: String) = password.any { it.isLetter() } && password.any { it.isDigit() }

    fun isValidPassword(password: String) = hasMinLength(password) && hasLettersAndNumbers(password)

    /** Nomor setelah +62, tanpa angka 0 di depan. */
    fun isValidIndonesianPhone(digits: String) = digits.all { it.isDigit() } && digits.startsWith("8") && digits.length in 9..12

    fun isValidNik(nik: String) = nik.length == 16 && nik.all { it.isDigit() }

    fun isValidFullName(name: String) = name.trim().length >= 3 && name.trim().any { it.isLetter() }

    /** Format DD/MM/YYYY, minimal 18 tahun pada tanggal acuan. */
    fun isAdult(dob: String, todayYear: Int, todayMonth: Int, todayDay: Int): Boolean {
        val parts = dob.split("/")
        if (parts.size != 3) return false
        val day = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val year = parts[2].toIntOrNull() ?: return false
        if (month !in 1..12 || day !in 1..daysInMonth(month, year) || year < 1900) return false
        var age = todayYear - year
        if (todayMonth < month || (todayMonth == month && todayDay < day)) age--
        return age >= 18
    }

    private fun daysInMonth(month: Int, year: Int) = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun isWeakPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return true
        if (pin.toSet().size <= 2) return true
        val steps = pin.zipWithNext { a, b -> b - a }
        return steps.all { it == 1 } || steps.all { it == -1 }
    }

    fun isPickupEligible(totalWeightMg: Long) = totalWeightMg >= PICKUP_MIN_WEIGHT_MG

    fun canSellAt(token: GoldToken, nowMs: Long) = token.status == TokenStatus.ACTIVE && nowMs - token.acquiredAtMs >= HOLDING_PERIOD_MS
}

fun rupiah(value: Long): String {
    val digits = kotlin.math.abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return (if (value < 0) "-Rp " else "Rp ") + grouped
}

fun gram(weightMg: Long): String {
    val whole = weightMg / 1000
    val fraction = (weightMg % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole gr" else "$whole,$fraction gr"
}

fun percent(value: Double): String {
    val tenths = kotlin.math.round(value * 10).toLong()
    return "${tenths / 10},${tenths % 10}%"
}
