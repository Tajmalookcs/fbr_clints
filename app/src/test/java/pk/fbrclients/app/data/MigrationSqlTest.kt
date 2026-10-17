package pk.fbrclients.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.DriverManager

/** Runs the 1 -> 2 upgrade on a real SQLite table shaped like version 1. */
class MigrationSqlTest {
    @Test fun oldAreaMovesIntoTheRightBox() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use { st ->
                st.execute(
                    """CREATE TABLE wealth_item (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, statementId INTEGER NOT NULL,
                       categoryId INTEGER NOT NULL, description TEXT NOT NULL, amount INTEGER NOT NULL, subType TEXT,
                       acquisitionDate TEXT, areaUnit TEXT, area TEXT, propertyAddress TEXT, holderCnic TEXT)""",
                )
                val rows = listOf("Acre" to "1.38", "Kanal" to "2", "Marla" to "5", "Square Foot" to "900", "Square Meter" to "10", null to null)
                rows.forEachIndexed { i, (u, a) ->
                    st.execute("INSERT INTO wealth_item (id, statementId, categoryId, description, amount, areaUnit, area) VALUES (${i + 1}, 1, 1, 'x', 0, ${u?.let { "'$it'" } ?: "NULL"}, ${a?.let { "'$it'" } ?: "NULL"})")
                }
                MigrationSql.v1to2.forEach(st::execute)

                fun row(id: Int): List<String?> = st.executeQuery(
                    "SELECT areaUnit, area, areaAcre, areaKanal, areaMarla, areaSarsahi, areaSft, areaSqm FROM wealth_item WHERE id = $id",
                ).use { r -> r.next(); (1..8).map { r.getString(it) } }

                assertEquals(listOf("Acre", "1.38", "1.38", null, null, null, null, null), row(1))
                assertEquals(listOf("Kanal", "2", null, "2", null, null, null, null), row(2))
                assertEquals(listOf("Marla", "5", null, null, "5", null, null, null), row(3))
                assertEquals(listOf("Sft", "900", null, null, null, null, "900", null), row(4))
                assertEquals(listOf("Sft", "107.64", null, null, null, null, null, "10"), row(5))
                assertEquals(listOf<String?>(null, null, null, null, null, null, null, null), row(6))
            }
        }
    }
}
