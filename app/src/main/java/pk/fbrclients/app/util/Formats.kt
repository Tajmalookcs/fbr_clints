package pk.fbrclients.app.util

/** Input formats from the design. Pure Kotlin so they are unit-tested. */
object Formats {
    const val CNIC_DIGITS = 13
    const val MOBILE_DIGITS = 11

    /** Dash positions, counted in digits: 12345-1234567-1 */
    val CNIC_DASHES = intArrayOf(5, 12)

    /** 03XX-XXXXXXX */
    val MOBILE_DASHES = intArrayOf(4)

    fun digitsOnly(s: String): String = s.filter { it in '0'..'9' }

    fun withDashes(digits: String, dashes: IntArray, sep: Char = '-'): String = buildString {
        digits.forEachIndexed { i, c ->
            if (i in dashes) append(sep)
            append(c)
        }
    }

    /** Cursor position in the dashed text for a position in the raw digits. */
    fun shownOffset(rawOffset: Int, dashes: IntArray): Int =
        rawOffset + dashes.count { it < rawOffset }

    /** Cursor position in the raw digits for a position in the dashed text. */
    fun rawOffset(shown: String, shownOffset: Int): Int =
        shown.take(shownOffset).count { it in '0'..'9' }

    // ---- Amounts: whole rupees, shown as 1,234,567 ----

    const val AMOUNT_MAX_DIGITS = 15

    /** Where commas go for a number with [length] digits (before these digit indexes). */
    fun commaPositions(length: Int): IntArray =
        (1 until length).filter { (length - it) % 3 == 0 }.toIntArray()

    fun groupDigits(digits: String): String = withDashes(digits, commaPositions(digits.length), ',')

    fun amount(value: Long): String =
        if (value < 0) "-" + groupDigits((-value).toString()) else groupDigits(value.toString())

    /** Digits typed in an amount field -> rupees (empty = 0). */
    fun parseAmount(digits: String): Long =
        digitsOnly(digits).trimStart('0').take(AMOUNT_MAX_DIGITS).ifEmpty { "0" }.toLong()

    /** Rupees -> text for an amount field (0 shows as empty). */
    fun amountInput(value: Long): String = if (value == 0L) "" else value.toString()

    /** Area like 1.38: digits with at most one dot and 4 decimals. */
    private val AREA = Regex("^\\d{1,9}(\\.\\d{1,4})?$")
    fun isValidArea(s: String) = AREA.matches(s.trim())

    fun formatCnic(digits: String) = withDashes(digitsOnly(digits), CNIC_DASHES)
    fun formatMobile(digits: String) = withDashes(digitsOnly(digits), MOBILE_DASHES)

    fun isValidCnic(digits: String) = digits.length == CNIC_DIGITS && digits.all { it in '0'..'9' }

    fun isValidMobile(digits: String) =
        digits.length == MOBILE_DIGITS && digits.startsWith("03") && digits.all { it in '0'..'9' }

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    fun isValidEmail(s: String) = EMAIL.matches(s.trim())

    /** 03001234567 -> 923001234567, for wa.me links. */
    fun whatsAppNumber(mobile: String): String? {
        val d = digitsOnly(mobile)
        return if (isValidMobile(d)) "92" + d.drop(1) else null
    }
}
