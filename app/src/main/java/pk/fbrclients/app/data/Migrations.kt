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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.v3to4.forEach(db::execSQL)
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.v4to5.forEach(db::execSQL)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}

object MigrationSql {
    /** 4 -> 5: who referred the client (owner, 2026-10-21). */
    val v4to5: List<String> = listOf("ALTER TABLE `client` ADD COLUMN `reference` TEXT NOT NULL DEFAULT ''")

    /** Tables whose records sync between devices, with the kind written in their tombstones. */
    val SYNCED_TABLES = listOf(
        "client" to Kind.CLIENT,
        "category" to Kind.CATEGORY,
        "property_sub_type" to Kind.SUB_TYPE,
        "wealth_statement" to Kind.STATEMENT,
        "wealth_item" to Kind.ITEM,
        "electricity_bill" to Kind.BILL,
    )

    /** Seed index of Residential Property (its sub-types are built in). */
    val RESIDENTIAL_INDEX = SeedData.CATEGORIES.indexOfFirst { it.code == "7109" }

    /** Now, in milliseconds since 1970, inside SQLite. */
    private const val NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /**
     * 3 -> 4: sync. Every record gets a uid (the same on every device) and the time
     * of its last change; deleted records are remembered in sync_deleted.
     * Built-in categories and sub-types get fixed uids, so all devices share them.
     */
    val v3to4: List<String> = buildList {
        for ((t, _) in SYNCED_TABLES) {
            add("ALTER TABLE `$t` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            add("ALTER TABLE `$t` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        }
        add("ALTER TABLE `disco` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        SeedData.CATEGORIES.forEachIndexed { i, r ->
            add("UPDATE category SET uid = '${BuiltInUids.category(i)}' WHERE builtIn = 1 AND name = ${sqlText(r.name)} AND uid = ''")
        }
        SeedData.RESIDENTIAL_SUB_TYPES.forEachIndexed { i, name ->
            add(
                "UPDATE property_sub_type SET uid = '${BuiltInUids.subType(i)}' WHERE id = (SELECT MIN(s.id) FROM property_sub_type s " +
                    "JOIN category c ON c.id = s.categoryId WHERE c.uid = '${BuiltInUids.category(RESIDENTIAL_INDEX)}' AND s.name = ${sqlText(name)})",
            )
        }
        for ((t, _) in SYNCED_TABLES) {
            add("UPDATE `$t` SET uid = lower(hex(randomblob(16))) WHERE uid = ''")
            add("UPDATE `$t` SET updatedAt = $NOW")
            add("CREATE UNIQUE INDEX IF NOT EXISTS `index_${t}_uid` ON `$t` (`uid`)")
        }
        add("UPDATE `disco` SET updatedAt = $NOW")
        add(
            "CREATE TABLE IF NOT EXISTS `sync_deleted` (`uid` TEXT NOT NULL, `kind` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, " +
                "`replacedBy` TEXT, PRIMARY KEY(`uid`))",
        )
    }

    private fun sqlText(s: String) = "'" + s.replace("'", "''") + "'"

    const val APPLYING_ON = "UPDATE sync_control SET applying = 1"
    const val APPLYING_OFF = "UPDATE sync_control SET applying = 0"

    /**
     * Keep uid and updatedAt right however a record is saved (run every time the
     * database opens; "IF NOT EXISTS" makes that safe):
     *  - a new record gets the current time (and a uid if it has none);
     *  - a changed record gets the current time, unless sync already set a newer one;
     *  - a record's uid never changes;
     *  - a deleted record leaves a tombstone, so the deletion reaches the other devices.
     * While sync writes what came from the other devices, sync_control.applying is 1
     * and these triggers stay out of the way (sync sets the times and tombstones itself).
     */
    val triggers: List<String> = buildList {
        add("CREATE TABLE IF NOT EXISTS sync_control (id INTEGER PRIMARY KEY NOT NULL, applying INTEGER NOT NULL)")
        add("INSERT OR IGNORE INTO sync_control (id, applying) VALUES (1, 0)")
        add("UPDATE sync_control SET applying = 0")
        val notSyncing = "(SELECT applying FROM sync_control WHERE id = 1) = 0"
        for ((t, kind) in SYNCED_TABLES) {
            add(
                "CREATE TRIGGER IF NOT EXISTS `sync_${t}_insert` AFTER INSERT ON `$t` WHEN (NEW.updatedAt = 0 OR NEW.uid = '') AND $notSyncing BEGIN " +
                    "UPDATE `$t` SET updatedAt = CASE WHEN NEW.updatedAt = 0 THEN $NOW ELSE NEW.updatedAt END, " +
                    "uid = CASE WHEN NEW.uid = '' THEN lower(hex(randomblob(16))) ELSE NEW.uid END WHERE rowid = NEW.rowid; END",
            )
            add(
                "CREATE TRIGGER IF NOT EXISTS `sync_${t}_update` AFTER UPDATE ON `$t` WHEN NEW.updatedAt <= OLD.updatedAt AND $notSyncing BEGIN " +
                    "UPDATE `$t` SET updatedAt = MAX($NOW, OLD.updatedAt + 1) WHERE rowid = NEW.rowid; END",
            )
            add(
                "CREATE TRIGGER IF NOT EXISTS `sync_${t}_keep_uid` AFTER UPDATE OF uid ON `$t` WHEN OLD.uid <> '' AND NEW.uid <> OLD.uid BEGIN " +
                    "UPDATE `$t` SET uid = OLD.uid WHERE rowid = NEW.rowid; END",
            )
            add(
                "CREATE TRIGGER IF NOT EXISTS `sync_${t}_delete` AFTER DELETE ON `$t` WHEN $notSyncing BEGIN " +
                    "INSERT OR REPLACE INTO sync_deleted (uid, kind, deletedAt, replacedBy) VALUES (OLD.uid, '$kind', $NOW, NULL); END",
            )
        }
        add(
            "CREATE TRIGGER IF NOT EXISTS `sync_disco_update` AFTER UPDATE ON `disco` WHEN NEW.updatedAt <= OLD.updatedAt AND $notSyncing BEGIN " +
                "UPDATE `disco` SET updatedAt = MAX($NOW, OLD.updatedAt + 1) WHERE rowid = NEW.rowid; END",
        )
    }

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
