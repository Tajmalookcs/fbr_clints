package pk.fbrclients.app.data

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Land area entered in several boxes, with one Total (owner's rules, 2026-10-17):
 *  1 Acre = 8 Kanal, 1 Kanal = 20 Marla, 1 Marla = 9 Sarsahi = 272 Sft, 1 Sq m = 10.7639 Sft.
 * The Total is in the biggest unit that has a value, in the order
 * Acre → Kanal → Marla → Sft (Sarsahi and Sq m are counted in Sft), rounded to 2 decimals.
 */
object LandArea {
    data class Parts(
        val acre: String = "",
        val kanal: String = "",
        val marla: String = "",
        val sarsahi: String = "",
        val sft: String = "",
        val sqm: String = "",
    )

    data class Total(val value: String, val unit: String) {
        override fun toString() = "$value $unit"
    }

    private val MC = MathContext.DECIMAL64
    private val SFT_PER_MARLA = BigDecimal(272)
    private val SFT_PER_KANAL = SFT_PER_MARLA.multiply(BigDecimal(20))
    private val SFT_PER_ACRE = SFT_PER_KANAL.multiply(BigDecimal(8))
    private val SFT_PER_SARSAHI = SFT_PER_MARLA.divide(BigDecimal(9), MC)
    private val SFT_PER_SQM = BigDecimal("10.7639")

    private val NUMBER = Regex("^\\d{0,9}(\\.\\d{0,4})?$")

    /** What a box may hold while typing: digits and one dot, up to 4 decimals. */
    fun isTypable(s: String) = NUMBER.matches(s)

    fun parse(s: String): BigDecimal? = s.trim().takeIf { it.isNotEmpty() && it != "." }?.toBigDecimalOrNull()

    private fun positive(s: String) = (parse(s) ?: BigDecimal.ZERO).signum() > 0

    fun totalSft(p: Parts): BigDecimal =
        listOf(
            p.acre to SFT_PER_ACRE, p.kanal to SFT_PER_KANAL, p.marla to SFT_PER_MARLA,
            p.sarsahi to SFT_PER_SARSAHI, p.sft to BigDecimal.ONE, p.sqm to SFT_PER_SQM,
        ).fold(BigDecimal.ZERO) { acc, (v, f) -> acc.add((parse(v) ?: BigDecimal.ZERO).multiply(f, MC), MC) }

    /** Null when every box is empty or 0. */
    fun total(p: Parts): Total? {
        val (unit, perUnit) = when {
            positive(p.acre) -> "Acre" to SFT_PER_ACRE
            positive(p.kanal) -> "Kanal" to SFT_PER_KANAL
            positive(p.marla) -> "Marla" to SFT_PER_MARLA
            positive(p.sarsahi) || positive(p.sft) || positive(p.sqm) -> "Sft" to BigDecimal.ONE
            else -> return null
        }
        val v = totalSft(p).divide(perUnit, MC).setScale(2, RoundingMode.HALF_UP)
        return Total(plain(v), unit)
    }

    /** 3.50 -> "3.5", 5.00 -> "5" */
    private fun plain(v: BigDecimal): String {
        val s = v.stripTrailingZeros()
        return (if (s.scale() < 0) s.setScale(0) else s).toPlainString()
    }
}
