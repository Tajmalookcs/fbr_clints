package pk.fbrclients.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WealthDao {

    // ---- Categories and sub-types ----

    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    abstract fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM category WHERE id = :id")
    abstract suspend fun category(id: Long): Category?

    @Query("SELECT * FROM category WHERE isProperty = 1 ORDER BY sortOrder, id")
    abstract suspend fun propertyCategories(): List<Category>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM category")
    abstract suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM wealth_item WHERE categoryId = :categoryId")
    abstract suspend fun itemsUsing(categoryId: Long): Int

    @Insert abstract suspend fun insertCategory(c: Category): Long
    @Update abstract suspend fun updateCategory(c: Category)
    @Delete abstract suspend fun deleteCategory(c: Category)

    @Query("SELECT * FROM property_sub_type ORDER BY name COLLATE NOCASE")
    abstract fun subTypes(): Flow<List<PropertySubType>>

    @Insert abstract suspend fun insertSubType(s: PropertySubType): Long
    @Delete abstract suspend fun deleteSubType(s: PropertySubType)

    // ---- Statements ----

    @Query(
        """
        SELECT s.*, COALESCE(SUM(CASE WHEN c.grp = 'LIABILITY' THEN -i.amount ELSE i.amount END), 0) AS netAssets
        FROM wealth_statement s
        LEFT JOIN wealth_item i ON i.statementId = s.id
        LEFT JOIN category c ON c.id = i.categoryId
        WHERE s.clientId = :clientId
        GROUP BY s.id
        ORDER BY s.taxYear DESC
        """,
    )
    abstract fun summaries(clientId: Long): Flow<List<StatementSummary>>

    @Query("SELECT * FROM wealth_statement WHERE id = :id")
    abstract fun statement(id: Long): Flow<WealthStatement?>

    @Query("SELECT * FROM wealth_statement WHERE id = :id")
    abstract suspend fun getStatement(id: Long): WealthStatement?

    @Query("SELECT * FROM wealth_statement WHERE clientId = :clientId AND taxYear = :year")
    abstract suspend fun statementFor(clientId: Long, year: Int): WealthStatement?

    /** Number of statements for that client and year (0 or 1). */
    @Query("SELECT COUNT(*) FROM wealth_statement WHERE clientId = :clientId AND taxYear = :year")
    abstract fun yearExists(clientId: Long, year: Int): Flow<Int>

    /** Net assets (assets − liabilities) of a year's statement; null when it has no items. */
    @Query(
        """
        SELECT SUM(CASE WHEN c.grp = 'LIABILITY' THEN -i.amount ELSE i.amount END)
        FROM wealth_item i
        JOIN category c ON c.id = i.categoryId
        JOIN wealth_statement s ON s.id = i.statementId
        WHERE s.clientId = :clientId AND s.taxYear = :year
        """,
    )
    abstract fun netAssetsOf(clientId: Long, year: Int): Flow<Long?>

    @Insert abstract suspend fun insertStatement(s: WealthStatement): Long
    @Update abstract suspend fun updateStatement(s: WealthStatement)
    @Delete abstract suspend fun deleteStatement(s: WealthStatement)

    // ---- Items ----

    @Query("SELECT * FROM wealth_item WHERE statementId = :statementId ORDER BY id")
    abstract fun items(statementId: Long): Flow<List<WealthItem>>

    @Query("SELECT * FROM wealth_item WHERE statementId = :statementId ORDER BY id")
    abstract suspend fun itemsOf(statementId: Long): List<WealthItem>

    @Query("SELECT * FROM wealth_item WHERE id = :id")
    abstract suspend fun item(id: Long): WealthItem?

    @Insert abstract suspend fun insertItem(i: WealthItem): Long
    @Insert abstract suspend fun insertItems(i: List<WealthItem>)
    @Update abstract suspend fun updateItem(i: WealthItem)
    @Delete abstract suspend fun deleteItem(i: WealthItem)

    /** New tax year, optionally copying every item from another statement ("new year from last year"). */
    @Transaction
    open suspend fun createYear(clientId: Long, year: Int, copyFromStatementId: Long?): Long {
        val id = insertStatement(WealthStatement(clientId = clientId, taxYear = year))
        if (copyFromStatementId != null) {
            insertItems(itemsOf(copyFromStatementId).map { it.copy(id = 0, statementId = id) })
        }
        return id
    }
}
