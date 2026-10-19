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

    @Test fun upgradeFrom2MatchesCurrentSchema() {
        val schema = currentSchema()
        assertTrue("schema version", schema.getInt("version") >= 3)
        val entities = schema.getJSONArray("entities").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val newIn3 = setOf("electricity_bill", "disco")

        val fresh = db(createSql(entities))
        val upgraded = db(createSql(entities.filter { it.getString("tableName") !in newIn3 }) + MigrationSql.v2to3)
        upgraded.createStatement().use { st ->
            DiscoSeed.insertSql.forEach { (sql, args) ->
                val p = upgraded.prepareStatement(sql)
                args.forEachIndexed { i, v -> p.setObject(i + 1, v) }
                p.executeUpdate()
            }
            st.executeQuery("SELECT COUNT(*) FROM disco").use { r -> r.next(); assertEquals(DiscoSeed.ALL.size, r.getInt(1)) }
        }
        assertEquals(describe(fresh), describe(upgraded))
    }

    @Test fun fescoLinksAreTheOwners() {
        val f = DiscoSeed.ALL.first { it.code == "FESCO" }
        assertEquals("https://bill.pitc.com.pk/fescobill", f.billUrl)
        assertEquals("https://mis.fesco.com.pk/fescoweb/old.fesco.com.pk/NewTaxStatement/TaxStatement.aspx", f.certUrl)
    }
}
