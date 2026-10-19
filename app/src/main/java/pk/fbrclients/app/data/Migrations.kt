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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.v2to3.forEach(db::execSQL)
            DiscoSeed.insertSql.forEach { (sql, args) -> db.execSQL(sql, args) }
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}

object MigrationSql {
    /** 2 -> 3: electricity bills per client, and electricity companies with their links. */
    val v2to3: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `electricity_bill` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` INTEGER NOT NULL, " +
            "`company` TEXT NOT NULL, `referenceNo` TEXT NOT NULL, `label` TEXT NOT NULL, " +
            "FOREIGN KEY(`clientId`) REFERENCES `client`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_electricity_bill_clientId` ON `electricity_bill` (`clientId`)",
        "CREATE TABLE IF NOT EXISTS `disco` (`code` TEXT NOT NULL, `name` TEXT NOT NULL, `billUrl` TEXT NOT NULL, " +
            "`certUrl` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`code`))",
    )

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
