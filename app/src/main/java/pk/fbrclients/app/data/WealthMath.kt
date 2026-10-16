package pk.fbrclients.app.data

/** Totals and reconciliation from docs/DESIGN.md §4.4. All amounts in whole rupees. */
data class Reconciliation(
    /** 7019 */ val totalAssets: Long,
    /** 7029 */ val totalLiabilities: Long,
    /** 703001 = 7019 − 7029 */ val netAssetsCurrent: Long,
    /** 703002 */ val netAssetsPrevious: Long,
    /** 703003 = 703001 − 703002 */ val increase: Long,
    /** 7049 = 7031 + 7032 + 7033 */ val inflows: Long,
    /** 7087 */ val outflows: Long,
    /** 703000 = 7049 − outflows − 703003 */ val unreconciled: Long,
)

object WealthMath {
    fun totals(items: List<WealthItem>, categories: Map<Long, Category>): Pair<Long, Long> {
        var assets = 0L
        var liabilities = 0L
        for (i in items) {
            if (categories[i.categoryId]?.isLiability == true) liabilities += i.amount else assets += i.amount
        }
        return assets to liabilities
    }

    fun reconcile(
        totalAssets: Long,
        totalLiabilities: Long,
        netAssetsPrevious: Long,
        statement: WealthStatement,
    ): Reconciliation {
        val net = totalAssets - totalLiabilities
        val increase = net - netAssetsPrevious
        val inflows = statement.incomeNormal + statement.incomeExempt + statement.incomeFinal
        val outflows = statement.personalExpenses
        return Reconciliation(
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netAssetsCurrent = net,
            netAssetsPrevious = netAssetsPrevious,
            increase = increase,
            inflows = inflows,
            outflows = outflows,
            unreconciled = inflows - outflows - increase,
        )
    }

    /** Current tax year: TY 2026 runs 1 July 2025 – 30 June 2026 and is filed from July 2026. */
    fun currentTaxYear(year: Int, month: Int): Int = if (month >= 7) year else year - 1
}
