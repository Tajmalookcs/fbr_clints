package pk.fbrclients.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database upgrades. v1.0.0 shipped schema version 1; every later change to the
 * tables must add a step here, or clients' data is lost on update.
 * The SQL lives in [MigrationSql] (plain JVM) so it can be unit-tested.
 */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.v1to2.forEach(db::execSQL)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}

object MigrationSql {
    /**
     * 1 -> 2: land area boxes (Acre, Kanal, Marla, Sarsahi, Sft, Sq m).
     * The old single area + unit moves into the matching box; area/areaUnit
     * now hold the calculated Total. Square Foot / Square Meter totals become Sft.
     */
    val v1to2: List<String> = buildList {
        for (c in listOf("areaAcre", "areaKanal", "areaMarla", "areaSarsahi", "areaSft", "areaSqm")) {
            add("ALTER TABLE wealth_item ADD COLUMN $c TEXT")
        }
        mapOf(
            "Acre" to "areaAcre",
            "Kanal" to "areaKanal",
            "Marla" to "areaMarla",
            "Square Foot" to "areaSft",
            "Square Meter" to "areaSqm",
        ).forEach { (unit, column) ->
            add("UPDATE wealth_item SET $column = area WHERE areaUnit = '$unit'")
        }
        add("UPDATE wealth_item SET area = CAST(ROUND(CAST(area AS REAL) * 10.7639, 2) AS TEXT), areaUnit = 'Sft' WHERE areaUnit = 'Square Meter'")
        add("UPDATE wealth_item SET areaUnit = 'Sft' WHERE areaUnit = 'Square Foot'")
    }
}
