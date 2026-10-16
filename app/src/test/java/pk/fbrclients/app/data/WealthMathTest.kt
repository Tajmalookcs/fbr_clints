package pk.fbrclients.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import pk.fbrclients.app.util.Formats

class WealthMathTest {
    private val house = Category(id = 1, code = "7109", name = "Residential Property", group = AssetGroup.IMMOVABLE, isProperty = true)
    private val car = Category(id = 2, code = "7008", name = "Motor Vehicle(s)", group = AssetGroup.MOVEABLE)
    private val loan = Category(id = 3, code = "7021", name = "Payables", group = AssetGroup.LIABILITY)
    private val cats = listOf(house, car, loan).associateBy { it.id }

    private fun item(cat: Category, amount: Long) =
        WealthItem(statementId = 1, categoryId = cat.id, description = "x", amount = amount)

    @Test fun totalsSplitAssetsAndLiabilities() {
        val (a, l) = WealthMath.totals(
            listOf(item(house, 5_000_000), item(car, 1_200_000), item(car, 0), item(loan, 700_000)),
            cats,
        )
        assertEquals(6_200_000L, a)
        assertEquals(700_000L, l)
    }

    @Test fun reconciliationBalances() {
        val s = WealthStatement(
            clientId = 1, taxYear = 2026,
            incomeNormal = 1_500_000, incomeExempt = 100_000, incomeFinal = 50_000, personalExpenses = 900_000,
        )
        val r = WealthMath.reconcile(totalAssets = 6_200_000, totalLiabilities = 700_000, netAssetsPrevious = 4_750_000, statement = s)
        assertEquals(5_500_000L, r.netAssetsCurrent)
        assertEquals(750_000L, r.increase)
        assertEquals(1_650_000L, r.inflows)
        assertEquals(900_000L, r.outflows)
        assertEquals(0L, r.unreconciled)
    }

    @Test fun unreconciledShowsTheGap() {
        val s = WealthStatement(clientId = 1, taxYear = 2026, incomeNormal = 1_000_000, personalExpenses = 600_000)
        val r = WealthMath.reconcile(2_000_000, 0, 1_500_000, s)
        assertEquals(-100_000L, r.unreconciled)
    }

    @Test fun taxYear() {
        assertEquals(2026, WealthMath.currentTaxYear(2026, 10))
        assertEquals(2025, WealthMath.currentTaxYear(2026, 6))
        assertEquals(2026, WealthMath.currentTaxYear(2026, 7))
    }

    @Test fun propertyDescriptionFollowsIrisStyle() {
        assertEquals(
            "Residential – Constructed Property, 5 Marla, House 12, Street 4, Mohalla Gulbahar, Peshawar, 1/2 share",
            propertyDescription("Residential", "Constructed Property", "5", "Marla", "House 12, Street 4,\nMohalla Gulbahar, Peshawar, 1/2 share"),
        )
        assertEquals("Agricultural – Land, 1.38 Acre", propertyDescription("Agricultural", "Land", "1.38", "Acre", ""))
    }

    @Test fun amountFormatting() {
        assertEquals("0", Formats.amount(0))
        assertEquals("999", Formats.amount(999))
        assertEquals("1,000", Formats.amount(1000))
        assertEquals("1,234,567", Formats.amount(1_234_567))
        assertEquals("-100,000", Formats.amount(-100_000))
        assertEquals("1,234,567", Formats.groupDigits("1234567"))
        assertEquals(1_234_567L, Formats.parseAmount("1,234,567"))
        assertEquals(0L, Formats.parseAmount(""))
        assertEquals("", Formats.amountInput(0))
    }

    @Test fun amountCursorRoundTrips() {
        for (raw in listOf("", "1", "123", "1234", "1234567", "123456789012")) {
            val pos = Formats.commaPositions(raw.length)
            val shown = Formats.withDashes(raw, pos, ',')
            for (o in 0..raw.length) {
                val s = Formats.shownOffset(o, pos)
                assert(s <= shown.length)
                assertEquals(o, Formats.rawOffset(shown, s))
            }
        }
    }

    @Test fun areaRule() {
        assert(Formats.isValidArea("1.38"))
        assert(Formats.isValidArea("12"))
        assert(!Formats.isValidArea("1.2.3"))
        assert(!Formats.isValidArea(""))
    }
}
