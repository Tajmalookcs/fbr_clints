package pk.fbrclients.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.fbrclients.app.data.sync.Sync

/** Reads all data for sync and writes back what sync changed. See SyncStore for the transaction. */
@Dao
abstract class SyncDao {
    @Query("SELECT * FROM client") abstract suspend fun clients(): List<Client>
    @Query("SELECT * FROM category") abstract suspend fun categories(): List<Category>
    @Query("SELECT * FROM property_sub_type") abstract suspend fun subTypes(): List<PropertySubType>
    @Query("SELECT * FROM wealth_statement") abstract suspend fun statements(): List<WealthStatement>
    @Query("SELECT * FROM wealth_item") abstract suspend fun items(): List<WealthItem>
    @Query("SELECT * FROM electricity_bill") abstract suspend fun bills(): List<ElectricityBill>
    @Query("SELECT * FROM disco") abstract suspend fun discos(): List<Disco>
    @Query("SELECT * FROM sync_deleted") abstract suspend fun tombstones(): List<Tombstone>

    suspend fun exportAll() = BackupData(clients(), categories(), subTypes(), statements(), items(), bills(), discos())

    @Insert abstract suspend fun insertClients(x: List<Client>)
    @Update abstract suspend fun updateClients(x: List<Client>)
    @Delete abstract suspend fun deleteClients(x: List<Client>)

    @Insert abstract suspend fun insertCategories(x: List<Category>)
    @Update abstract suspend fun updateCategories(x: List<Category>)
    @Delete abstract suspend fun deleteCategories(x: List<Category>)

    @Insert abstract suspend fun insertSubTypes(x: List<PropertySubType>)
    @Update abstract suspend fun updateSubTypes(x: List<PropertySubType>)
    @Delete abstract suspend fun deleteSubTypes(x: List<PropertySubType>)

    @Insert abstract suspend fun insertStatements(x: List<WealthStatement>)
    @Update abstract suspend fun updateStatements(x: List<WealthStatement>)
    @Delete abstract suspend fun deleteStatements(x: List<WealthStatement>)

    @Insert abstract suspend fun insertItems(x: List<WealthItem>)
    @Update abstract suspend fun updateItems(x: List<WealthItem>)
    @Delete abstract suspend fun deleteItems(x: List<WealthItem>)

    @Insert abstract suspend fun insertBills(x: List<ElectricityBill>)
    @Update abstract suspend fun updateBills(x: List<ElectricityBill>)
    @Delete abstract suspend fun deleteBills(x: List<ElectricityBill>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertDiscos(x: List<Disco>)

    @Query("DELETE FROM sync_deleted") abstract suspend fun clearTombstones()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertTombstones(x: List<Tombstone>)

    /**
     * Writes [d] (call inside a transaction, with the sync triggers paused).
     * Deletes children before parents, then adds and changes parents before children.
     */
    suspend fun apply(d: Sync.LocalDiff, tombstones: List<Tombstone>) {
        deleteItems(d.items.removed)
        deleteBills(d.bills.removed)
        deleteStatements(d.statements.removed)
        deleteSubTypes(d.subTypes.removed)
        deleteClients(d.clients.removed)
        deleteCategories(d.categories.removed)

        insertCategories(d.categories.added); updateCategories(d.categories.changed)
        insertSubTypes(d.subTypes.added); updateSubTypes(d.subTypes.changed)
        insertClients(d.clients.added); updateClients(d.clients.changed)
        insertStatements(d.statements.added); updateStatements(d.statements.changed)
        insertItems(d.items.added); updateItems(d.items.changed)
        insertBills(d.bills.added); updateBills(d.bills.changed)
        upsertDiscos(d.discos.added + d.discos.changed)

        clearTombstones()
        insertTombstones(tombstones)
    }
}
