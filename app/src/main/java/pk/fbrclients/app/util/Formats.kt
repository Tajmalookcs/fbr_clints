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

    fun withDashes(digits: String, dashes: IntArray): String = buildString {
        digits.forEachIndexed { i, c ->
            if (i in dashes) append('-')
            append(c)
        }
    }

    /** Cursor position in the dashed text for a position in the raw digits. */
    fun shownOffset(rawOffset: Int, dashes: IntArray): Int =
        rawOffset + dashes.count { it < rawOffset }

    /** Cursor position in the raw digits for a position in the dashed text. */
    fun rawOffset(shown: String, shownOffset: Int): Int =
        shown.take(shownOffset).count { it != '-' }

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
