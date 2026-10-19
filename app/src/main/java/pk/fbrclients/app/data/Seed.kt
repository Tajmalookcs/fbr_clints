package pk.fbrclients.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Categories and sub-types from the approved design (docs/DESIGN.md §4).
 * Only known IRIS codes are filled in. Industrial Property has no code yet;
 * the owner adds it in Settings. Financial Assets has no categories yet.
 */
object Seed {
    private data class Row(
        val code: String?,
        val name: String,
        val group: AssetGroup,
        val isProperty: Boolean = false,
        val editable: Boolean = false,
    )

    private val CATEGORIES = listOf(
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

    private val RESIDENTIAL_SUB_TYPES = listOf("Constructed Property", "Farm House", "Flat", "Open Plot")

    fun insert(db: SupportSQLiteDatabase) {
        DiscoSeed.insertSql.forEach { (sql, args) -> db.execSQL(sql, args) }
        CATEGORIES.forEachIndexed { i, r ->
            db.execSQL(
                "INSERT INTO category (code, name, grp, isProperty, sortOrder, builtIn, editable) VALUES (?, ?, ?, ?, ?, 1, ?)",
                arrayOf<Any?>(r.code, r.name, r.group.name, if (r.isProperty) 1 else 0, (i + 1) * 10, if (r.editable) 1 else 0),
            )
        }
        RESIDENTIAL_SUB_TYPES.forEach { name ->
            db.execSQL(
                "INSERT INTO property_sub_type (categoryId, name) SELECT id, ? FROM category WHERE code = '7109'",
                arrayOf<Any?>(name),
            )
        }
    }
}
