package pk.fbrclients.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Query("SELECT * FROM client ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Client>>

    /**
     * [text] matches name / CNIC / NTN / mobile as typed.
     * [digits] (only the digits of the search, or null) also matches CNIC and
     * mobile with the dashes ignored, so "4210112" finds "42101-12...".
     */
    @Query(
        """
        SELECT * FROM client
        WHERE name LIKE '%' || :text || '%'
           OR businessName LIKE '%' || :text || '%'
           OR reference LIKE '%' || :text || '%'
           OR cnic LIKE '%' || :text || '%'
           OR ntn LIKE '%' || :text || '%'
           OR mobile LIKE '%' || :text || '%'
           OR (:digits IS NOT NULL AND (
                REPLACE(cnic, '-', '') LIKE '%' || :digits || '%'
             OR REPLACE(mobile, '-', '') LIKE '%' || :digits || '%'
             OR REPLACE(ntn, '-', '') LIKE '%' || :digits || '%'))
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun search(text: String, digits: String?): Flow<List<Client>>

    @Query("SELECT * FROM client WHERE id = :id")
    fun byId(id: Long): Flow<Client?>

    @Query("SELECT * FROM client WHERE id = :id")
    suspend fun get(id: Long): Client?

    @Query("SELECT id FROM client WHERE cnic = :cnic LIMIT 1")
    suspend fun idForCnic(cnic: String): Long?

    @Insert
    suspend fun insert(client: Client): Long

    @Update
    suspend fun update(client: Client)

    @Delete
    suspend fun delete(client: Client)

    @Query("SELECT * FROM client")
    suspend fun allOnce(): List<Client>

    @Insert
    suspend fun insertAll(clients: List<Client>)

    @Update
    suspend fun updateAll(clients: List<Client>)
}
