package pk.fbrclients.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** A new random id that is the same on every device (32 hex characters). */
fun newUid(): String = UUID.randomUUID().toString().replace("-", "")

/** Ids of the built-in categories and sub-types are fixed, so every device has the same ones. */
object BuiltInUids {
    fun category(seedIndex: Int) = "builtin-cat-${seedIndex + 1}"
    fun subType(seedIndex: Int) = "builtin-sub-${seedIndex + 1}"
}

/** What a [Tombstone] was. */
object Kind {
    const val CLIENT = "client"
    const val CATEGORY = "category"
    const val SUB_TYPE = "subType"
    const val STATEMENT = "statement"
    const val ITEM = "item"
    const val BILL = "bill"
}

/**
 * Remembers that a record was deleted, so sync deletes it on the other devices too.
 * [replacedBy]: when sync joined two copies of the same client (same CNIC) or the
 * same tax year, the uid of the copy that was kept.
 */
@Entity(tableName = "sync_deleted")
data class Tombstone(
    @PrimaryKey val uid: String,
    val kind: String,
    val deletedAt: Long,
    val replacedBy: String? = null,
)
