package pk.fbrclients.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ElectricityDao {
    @Query("SELECT * FROM electricity_bill WHERE clientId = :clientId ORDER BY id")
    fun bills(clientId: Long): Flow<List<ElectricityBill>>

    @Insert suspend fun insert(b: ElectricityBill): Long
    @Update suspend fun update(b: ElectricityBill)
    @Delete suspend fun delete(b: ElectricityBill)

    @Query("SELECT * FROM disco ORDER BY sortOrder, code")
    fun discos(): Flow<List<Disco>>

    @Update suspend fun updateDisco(d: Disco)
}
