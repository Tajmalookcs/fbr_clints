package pk.fbrclients.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "client", indices = [Index(value = ["cnic"], unique = true), Index(value = ["uid"], unique = true)])
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * IRIS username: CNIC stored as 12345-1234567-1 for individuals, or the NTN
     * (1234567 / A123456) for companies and AOPs. (Column keeps the name "cnic".)
     */
    val cnic: String,
    /** Kept inside the encrypted database; hidden in the UI by default. */
    val irisPassword: String = "",
    val name: String,
    val businessName: String = "",
    val ntn: String = "",
    /** Stored as 03XX-XXXXXXX. */
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    /** Same on every device; used by sync. */
    @ColumnInfo(defaultValue = "") val uid: String = newUid(),
    /** Last change (ms since 1970). 0 = set by the database on save. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)
