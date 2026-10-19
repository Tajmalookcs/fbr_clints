package pk.fbrclients.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A client's commercial electricity connection. */
@Entity(
    tableName = "electricity_bill",
    foreignKeys = [ForeignKey(Client::class, ["id"], ["clientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("clientId")],
)
data class ElectricityBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    /** Disco.code, e.g. "FESCO". */
    val company: String,
    /** Digits only, as on the bill. */
    val referenceNo: String,
    /** Optional, e.g. "Shop, Main Bazar". */
    val label: String = "",
)

/** An electricity company with its bill and tax-certificate pages (editable in Settings). */
@Entity(tableName = "disco")
data class Disco(
    @PrimaryKey val code: String,
    val name: String,
    val billUrl: String,
    val certUrl: String,
    val sortOrder: Int,
)

object DiscoSeed {
    private fun pitc(code: String) = "https://bill.pitc.com.pk/${code.lowercase()}bill"

    /**
     * Bill pages follow the official PITC pattern. Only FESCO's certificate page
     * was given by the owner; the others are left empty for the owner to add.
     */
    val ALL: List<Disco> = listOf(
        Disco("FESCO", "FESCO (Faisalabad)", pitc("FESCO"), "https://mis.fesco.com.pk/fescoweb/old.fesco.com.pk/NewTaxStatement/TaxStatement.aspx", 10),
        Disco("GEPCO", "GEPCO (Gujranwala)", pitc("GEPCO"), "", 20),
        Disco("HESCO", "HESCO (Hyderabad)", pitc("HESCO"), "", 30),
        Disco("IESCO", "IESCO (Islamabad)", pitc("IESCO"), "", 40),
        Disco("LESCO", "LESCO (Lahore)", pitc("LESCO"), "", 50),
        Disco("MEPCO", "MEPCO (Multan)", pitc("MEPCO"), "", 60),
        Disco("PESCO", "PESCO (Peshawar)", pitc("PESCO"), "", 70),
        Disco("QESCO", "QESCO (Quetta)", pitc("QESCO"), "", 80),
        Disco("SEPCO", "SEPCO (Sukkur)", pitc("SEPCO"), "", 90),
        Disco("TESCO", "TESCO (Tribal Areas)", pitc("TESCO"), "", 100),
        Disco("KE", "K-Electric (Karachi)", "", "", 110),
    )

    /** INSERT statements (used for new installs and the 2 -> 3 upgrade). */
    val insertSql: List<Pair<String, Array<Any?>>> = ALL.map { d ->
        "INSERT OR IGNORE INTO disco (code, name, billUrl, certUrl, sortOrder) VALUES (?, ?, ?, ?, ?)" to
            arrayOf<Any?>(d.code, d.name, d.billUrl, d.certUrl, d.sortOrder)
    }
}

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
