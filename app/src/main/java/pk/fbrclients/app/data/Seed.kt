package pk.fbrclients.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Fills a new database with the built-in categories (see [SeedData]). */
object Seed {
    fun insert(db: SupportSQLiteDatabase) {
        DiscoSeed.insertSql.forEach { (sql, args) -> db.execSQL(sql, args) }
        SeedData.CATEGORIES.forEachIndexed { i, r ->
            db.execSQL(
                "INSERT INTO category (code, name, grp, isProperty, sortOrder, builtIn, editable) VALUES (?, ?, ?, ?, ?, 1, ?)",
                arrayOf<Any?>(r.code, r.name, r.group.name, if (r.isProperty) 1 else 0, (i + 1) * 10, if (r.editable) 1 else 0),
            )
        }
        SeedData.RESIDENTIAL_SUB_TYPES.forEach { name ->
            db.execSQL(
                "INSERT INTO property_sub_type (categoryId, name) SELECT id, ? FROM category WHERE code = '7109'",
                arrayOf<Any?>(name),
            )
        }
    }
}
