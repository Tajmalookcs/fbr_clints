package pk.fbrclients.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class BackupDao {
    @Query("SELECT * FROM client") abstract suspend fun clients(): List<Client>
    @Query("SELECT * FROM category") abstract suspend fun categories(): List<Category>
    @Query("SELECT * FROM property_sub_type") abstract suspend fun subTypes(): List<PropertySubType>
    @Query("SELECT * FROM wealth_statement") abstract suspend fun statements(): List<WealthStatement>
    @Query("SELECT * FROM wealth_item") abstract suspend fun items(): List<WealthItem>
    @Query("SELECT * FROM electricity_bill") abstract suspend fun bills(): List<ElectricityBill>
    @Query("SELECT * FROM disco") abstract suspend fun discos(): List<Disco>
    @Query("DELETE FROM electricity_bill") abstract suspend fun clearBills()
    @Query("DELETE FROM disco") abstract suspend fun clearDiscos()
    @Insert abstract suspend fun insertBills(x: List<ElectricityBill>)
    @Insert abstract suspend fun insertDiscos(x: List<Disco>)

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
    open suspend fun exportAll() = BackupData(clients(), categories(), subTypes(), statements(), items(), bills(), discos())

    /** Replaces everything on this phone with the backup, in one transaction. */
    @Transaction
    open suspend fun replaceAll(d: BackupData, now: Long = System.currentTimeMillis()) {
        clearBills(); clearItems(); clearStatements(); clearSubTypes(); clearCategories(); clearClients()
        // The restored records count as changed now, so sync gives them to the other devices
        // (and deletes there what this phone no longer has).
        insertClients(d.clients.map { it.copy(updatedAt = now) })
        insertCategories(d.categories.map { it.copy(updatedAt = now) })
        insertSubTypes(d.subTypes.map { it.copy(updatedAt = now) })
        insertStatements(d.statements.map { it.copy(updatedAt = now) })
        insertItems(d.items.map { it.copy(updatedAt = now) })
        insertBills(d.bills.map { it.copy(updatedAt = now) })
        if (d.discos.isNotEmpty()) {
            clearDiscos()
            insertDiscos(d.discos.map { it.copy(updatedAt = now) })
        }
        forgetRestoredTombstones()
    }

    /** Clearing the tables left tombstones; drop those of records that came back. */
    @Query(
        """DELETE FROM sync_deleted WHERE uid IN (SELECT uid FROM client UNION SELECT uid FROM category
           UNION SELECT uid FROM property_sub_type UNION SELECT uid FROM wealth_statement
           UNION SELECT uid FROM wealth_item UNION SELECT uid FROM electricity_bill)""",
    )
    abstract suspend fun forgetRestoredTombstones()
}
