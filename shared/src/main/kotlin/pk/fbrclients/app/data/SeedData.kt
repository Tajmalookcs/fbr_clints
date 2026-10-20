package pk.fbrclients.app.data

/**
 * Categories and sub-types from the approved design (docs/DESIGN.md §4).
 * Only known IRIS codes are filled in. Industrial Property has no code yet;
 * the owner adds it in Settings. Financial Assets has no categories yet.
 * Used by the phone database and the PC app.
 */
object SeedData {
    data class Row(
        val code: String?,
        val name: String,
        val group: AssetGroup,
        val isProperty: Boolean = false,
        val editable: Boolean = false,
    )

    val CATEGORIES = listOf(
        Row("7001", "Agricultural Property", AssetGroup.IMMOVABLE, isProperty = true),
        Row("7002", "Commercial Property", AssetGroup.IMMOVABLE, isProperty = true),
        Row(null, "Industrial Property", AssetGroup.IMMOVABLE, isProperty = true, editable = true),
        Row("7109", "Residential Property", AssetGroup.IMMOVABLE, isProperty = true),
        Row("7008", "Motor Vehicle(s)", AssetGroup.MOVEABLE),
        Row("7009", "Precious Possession", AssetGroup.MOVEABLE),
        Row("7010", "Household Effects", AssetGroup.MOVEABLE),
        Row("7014", "Assets held in others' names (including non-filer spouse/dependents)", AssetGroup.OTHER),
        Row("7013", "Any Other Asset(s)", AssetGroup.OTHER),
        Row("7003", "Business Capital", AssetGroup.OTHER),
        Row("700302", "Business Capital in AOPs/Companies", AssetGroup.OTHER),
        Row("7021", "Payables (Borrowing / Loan / Credits, etc.)", AssetGroup.LIABILITY),
    )

    val RESIDENTIAL_SUB_TYPES = listOf("Constructed Property", "Farm House", "Flat", "Open Plot")

    /** The built-in categories as records (ids 1, 2, 3 … in this order). */
    fun categories(): List<Category> = CATEGORIES.mapIndexed { i, r ->
        Category(id = i + 1L, code = r.code, name = r.name, group = r.group, isProperty = r.isProperty,
            sortOrder = (i + 1) * 10, builtIn = true, editable = r.editable)
    }
}
