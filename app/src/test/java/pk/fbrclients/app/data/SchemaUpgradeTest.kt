package pk.fbrclients.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Checks that upgrading from version 2 gives exactly the tables Room expects for
 * the current version, using the schema Room writes during the build
 * (app/schemas/.../<version>.json). A mismatch would make the app refuse to open
 * the database after an update, so this guards clients' data.
 */
class SchemaUpgradeTest {
    private val schemaDir = File("schemas/pk.fbrclients.app.data.AppDatabase")

    private fun currentSchema(): JSONObject {
        val latest = schemaDir.listFiles { f -> f.name.endsWith(".json") }!!.maxBy { it.nameWithoutExtension.toInt() }
        return JSONObject(latest.readText()).getJSONObject("database")
    }

    private fun createSql(entities: List<JSONObject>): List<String> = entities.flatMap { e ->
        val table = e.getString("tableName")
        val sql = mutableListOf(e.getString("createSql").replace("\${TABLE_NAME}", table))
        val idx = e.optJSONArray("indices")
        if (idx != null) for (i in 0 until idx.length()) sql += idx.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table)
        sql
    }

    private fun db(sql: List<String>): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { c ->
        c.createStatement().use { st -> sql.forEach(st::execute) }
    }

    /** Everything Room checks: columns, primary keys, foreign keys, indices. */
    private fun describe(c: Connection): Map<String, List<String>> {
        val out = sortedMapOf<String, List<String>>()
        val tables = c.createStatement().executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
        ).use { r -> buildList { while (r.next()) add(r.getString(1)) } }
        fun rows(q: String, cols: List<String>) = c.createStatement().executeQuery(q).use { r ->
            buildList { while (r.next()) add(cols.joinToString("|") { r.getString(it) ?: "null" }) }.sorted()
        }
        for (t in tables) {
            out["$t.columns"] = rows("PRAGMA table_info(`$t`)", listOf("name", "type", "notnull", "pk", "dflt_value"))
            out["$t.fks"] = rows("PRAGMA foreign_key_list(`$t`)", listOf("table", "from", "to", "on_update", "on_delete"))
            out["$t.indices"] = rows("PRAGMA index_list(`$t`)", listOf("name", "unique")).filterNot { it.startsWith("sqlite_autoindex") }
        }
        return out
    }

    private fun entities() = currentSchema().getJSONArray("entities").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    /**
     * Version 3 = the current tables without the sync columns, uid indices and sync_deleted.
     * (Schemas are rebuilt on every CI run, so version 3 is derived from the current one.)
     */
    private fun v3Sql(): List<String> = entities().filter { it.getString("tableName") != "sync_deleted" }.flatMap { e ->
        val table = e.getString("tableName")
        val create = e.getString("createSql").replace("\${TABLE_NAME}", table)
            .replace(Regex(",\\s*`uid` TEXT NOT NULL DEFAULT ''"), "")
            .replace(Regex(",\\s*`updatedAt` INTEGER NOT NULL DEFAULT 0"), "")
            .replace(Regex(",\\s*`reference` TEXT NOT NULL DEFAULT ''"), "")
        val idx = e.optJSONArray("indices")
        val indices = if (idx == null) emptyList() else (0 until idx.length()).map { idx.getJSONObject(it) }
            .filter { it.getString("name") != "index_${table}_uid" }
            .map { it.getString("createSql").replace("\${TABLE_NAME}", table) }
        listOf(create) + indices
    }

    @Test fun upgradeFrom3MatchesCurrentSchema() {
        val schema = currentSchema()
        assertEquals(5, schema.getInt("version"))
        val fresh = db(createSql(entities()))
        val old = v3Sql()
        assertTrue("v3 has no sync columns", old.none { "`uid`" in it || "updatedAt" in it || "`reference`" in it })
        val upgraded = db(old)
        upgraded.createStatement().use { st ->
            // Some version-3 data, including the built-in categories.
            SeedData.CATEGORIES.forEachIndexed { i, r ->
                st.execute("INSERT INTO category (id, code, name, grp, isProperty, sortOrder, builtIn, editable) VALUES (${i + 1}, NULL, '${r.name.replace("'", "''")}', '${r.group.name}', 0, ${i * 10}, 1, 0)")
            }
            st.execute("INSERT INTO category (id, code, name, grp, isProperty, sortOrder, builtIn, editable) VALUES (50, '9999', 'Own category', 'OTHER', 0, 500, 0, 1)")
            st.execute("INSERT INTO property_sub_type (id, categoryId, name) VALUES (1, 4, 'Flat'), (2, 4, 'Flat'), (3, 4, 'Shop')")
            st.execute("INSERT INTO client (id, cnic, irisPassword, name, businessName, ntn, mobile, email, address, notes) VALUES (1, '12345-1234567-1', 'pw', 'Test', '', '', '', '', '', '')")
            MigrationSql.v3to4.forEach(st::execute)
            MigrationSql.v4to5.forEach(st::execute)
        }
        assertEquals(describe(fresh), describe(upgraded))
        upgraded.createStatement().use { st ->
            fun one(q: String) = st.executeQuery(q).use { r -> r.next(); r.getString(1) }
            assertEquals("builtin-cat-4", one("SELECT uid FROM category WHERE id = 4"))
            assertEquals(32, one("SELECT uid FROM category WHERE id = 50").length)
            assertEquals("builtin-sub-3", one("SELECT uid FROM property_sub_type WHERE id = 1")) // Flat
            assertEquals(32, one("SELECT uid FROM property_sub_type WHERE id = 2").length) // the duplicate gets its own
            assertEquals(32, one("SELECT uid FROM client WHERE id = 1").length)
            assertTrue(one("SELECT updatedAt FROM client WHERE id = 1").toLong() > 1_700_000_000_000)
        }
    }

    @Test fun triggersKeepSyncFieldsRight() {
        val c = db(createSql(entities()) + MigrationSql.triggers + MigrationSql.triggers) // twice: must be safe
        c.createStatement().use { st ->
            fun one(q: String) = st.executeQuery(q).use { r -> if (r.next()) r.getString(1) else null }
            st.execute("INSERT INTO client (id, cnic, irisPassword, name, businessName, ntn, mobile, email, address, notes, uid, updatedAt) VALUES (1, '1234567', '', 'A', '', '', '', '', '', '', 'u1', 0)")
            val t1 = one("SELECT updatedAt FROM client")!!.toLong()
            assertTrue("insert sets the time", t1 > 1_700_000_000_000)

            // Saving an old copy (updatedAt 0, a new uid) keeps the uid and moves the time on.
            st.execute("UPDATE client SET name = 'B', uid = 'other', updatedAt = 0 WHERE id = 1")
            assertEquals("u1", one("SELECT uid FROM client"))
            assertTrue(one("SELECT updatedAt FROM client")!!.toLong() > t1)

            // Sync writes a newer time itself: kept as is.
            st.execute("UPDATE client SET name = 'C', updatedAt = 9000000000000 WHERE id = 1")
            assertEquals("9000000000000", one("SELECT updatedAt FROM client"))

            // A record without uid gets one.
            st.execute("INSERT INTO client (id, cnic, irisPassword, name, businessName, ntn, mobile, email, address, notes) VALUES (2, '1234568', '', 'D', '', '', '', '', '', '')")
            assertEquals(32, one("SELECT uid FROM client WHERE id = 2")!!.length)

            // Deleting leaves a tombstone.
            st.execute("DELETE FROM client WHERE id = 1")
            assertEquals("client", one("SELECT kind FROM sync_deleted WHERE uid = 'u1'"))

            // While sync applies changes, the triggers stay out of the way.
            st.execute(MigrationSql.APPLYING_ON)
            st.execute("INSERT INTO client (id, cnic, irisPassword, name, businessName, ntn, mobile, email, address, notes, uid, updatedAt) VALUES (3, '1234569', '', 'E', '', '', '', '', '', '', 'u3', 5)")
            st.execute("DELETE FROM client WHERE id = 2")
            st.execute(MigrationSql.APPLYING_OFF)
            assertEquals("5", one("SELECT updatedAt FROM client WHERE id = 3"))
            assertEquals("0", one("SELECT COUNT(*) FROM sync_deleted WHERE kind = 'client' AND uid <> 'u1'"))
        }
    }

    @Test fun fescoLinksAreTheOwners() {
        val f = DiscoSeed.ALL.first { it.code == "FESCO" }
        assertEquals("https://bill.pitc.com.pk/fescobill", f.billUrl)
        assertEquals("https://mis.fesco.com.pk/fescoweb/old.fesco.com.pk/NewTaxStatement/TaxStatement.aspx", f.certUrl)
    }
}
