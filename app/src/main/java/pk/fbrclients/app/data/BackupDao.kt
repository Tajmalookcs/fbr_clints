package pk.fbrclients.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Everything in the database, for the encrypted backup file. */
data class BackupData(
    val clients: List<Client>,
    val categories: List<Category>,
    val subTypes: List<PropertySubType>,
    val statements: List<WealthStatement>,
    val items: List<WealthItem>,
)

@Dao
abstract class BackupDao {
    @Query("SELECT * FROM client") abstract suspend fun clients(): List<Client>
    @Query("SELECT * FROM category") abstract suspend fun categories(): List<Category>
    @Query("SELECT * FROM property_sub_type") abstract suspend fun subTypes(): List<PropertySubType>
    @Query("SELECT * FROM wealth_statement") abstract suspend fun statements(): List<WealthStatement>
    @Query("SELECT * FROM wealth_item") abstract suspend fun items(): List<WealthItem>

    @Query("DELETE FROM wealth_item") abstract suspend fun clearItems()
    @Query("DELETE FROM wealth_statement") abstract suspend fun clearStatements()
    @Query("DELETE FROM property_sub_type") abstract suspend fun clearSubTypes()
    @Query("DELETE FROM category") abstract suspend fun clearCategories()
    @Query("DELETE FROM client") abstract suspend fun clearClients()

    @Insert abstract suspend fun insertClients(x: List<Client>)
    @Insert abstract suspend fun insertCategories(x: List<Category>)
    @Insert abstract suspend fun insertSubTypes(x: List<PropertySubType>)
    @Insert abstract suspend fun insertStatements(x: List<WealthStatement>)
    @Insert abstract suspend fun insertItems(x: List<WealthItem>)

    @Transaction
    open suspend fun exportAll() = BackupData(clients(), categories(), subTypes(), statements(), items())

    /** Replaces everything on this phone with the backup, in one transaction. */
    @Transaction
    open suspend fun replaceAll(d: BackupData) {
        clearItems(); clearStatements(); clearSubTypes(); clearCategories(); clearClients()
        insertClients(d.clients)
        insertCategories(d.categories)
        insertSubTypes(d.subTypes)
        insertStatements(d.statements)
        insertItems(d.items)
    }
}
