package pk.fbrclients.desktop.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import pk.fbrclients.app.data.BackupData
import pk.fbrclients.app.data.BackupJson
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.Disco
import pk.fbrclients.app.data.DiscoSeed
import pk.fbrclients.app.data.ElectricityBill
import pk.fbrclients.app.data.Kind
import pk.fbrclients.app.data.PropertySubType
import pk.fbrclients.app.data.SeedData
import pk.fbrclients.app.data.Tombstone
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.WealthStatement
import pk.fbrclients.app.data.newUid
import pk.fbrclients.desktop.platform.AppPaths
import pk.fbrclients.desktop.platform.SecretBox
import pk.fbrclients.desktop.platform.writeAtomically
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * All data of the PC app, kept in memory and saved (encrypted) after every change.
 * The file key is random and locked to this Windows user (SecretBox).
 * Every change sets the record's time and remembers deletions, like the phone
 * database does, so sync works the same way.
 */
class DataStore(
    private val file: File = AppPaths.data,
    private val previous: File = AppPaths.dataPrevious,
    private val keyFile: File = AppPaths.keys,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var data by mutableStateOf(empty())
        private set
    var deleted: List<Tombstone> = emptyList()
        private set

    private val lock = Any()

    // ---------------- Loading and saving ----------------

    fun load() = synchronized(lock) {
        if (!file.exists()) {
            data = empty()
            deleted = emptyList()
            save()
            return@synchronized
        }
        val root = JSONObject(String(decrypt(file.readBytes()), Charsets.UTF_8))
        data = BackupJson.read(root.getJSONObject("data").toString().toByteArray(Charsets.UTF_8))
        deleted = root.optJSONArray("deleted")?.let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Tombstone(o.getString("uid"), o.getString("kind"), o.getLong("deletedAt"), if (o.isNull("replacedBy")) null else o.getString("replacedBy"))
            }
        }.orEmpty()
    }

    private fun save() {
        val root = JSONObject()
            .put("data", JSONObject(String(BackupJson.write(data, ""), Charsets.UTF_8)))
            .put("deleted", JSONArray(deleted.map { t ->
                JSONObject().put("uid", t.uid).put("kind", t.kind).put("deletedAt", t.deletedAt).put("replacedBy", t.replacedBy ?: JSONObject.NULL)
            }))
        writeAtomically(file, encrypt(root.toString().toByteArray(Charsets.UTF_8)), keepPrevious = previous)
    }

    private fun key(): SecretKeySpec {
        if (!keyFile.exists()) {
            val k = ByteArray(32).also { SecureRandom().nextBytes(it) }
            writeAtomically(keyFile, SecretBox.protect(k))
        }
        return SecretKeySpec(SecretBox.unprotect(keyFile.readBytes()), "AES")
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(MAGIC.size + 12).put(MAGIC).put(iv).array()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        c.updateAAD(header)
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray()
        return header + c.doFinal(gz)
    }

    private fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size > MAGIC.size + 12 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not an FBR Clients data file" }
        val headerSize = MAGIC.size + 12
        val iv = bytes.copyOfRange(MAGIC.size, headerSize)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        c.updateAAD(bytes.copyOfRange(0, headerSize))
        val gz = c.doFinal(bytes, headerSize, bytes.size - headerSize)
        return GZIPInputStream(ByteArrayInputStream(gz)).use { it.readBytes() }
    }

    // ---------------- Changing data ----------------

    private fun change(block: (BackupData) -> BackupData) = synchronized(lock) {
        data = block(data)
        save()
    }

    /** The time for a changed record: now, but always after its previous time. */
    private fun stamp(old: Long) = maxOf(clock(), old + 1)

    private fun tomb(uid: String, kind: String) {
        deleted = deleted.filter { it.uid != uid } + Tombstone(uid, kind, clock())
    }

    private fun <T> nextId(list: List<T>, id: (T) -> Long) = (list.maxOfOrNull(id) ?: 0L) + 1

    /** Adds (id 0) or changes a client; returns its id. */
    fun saveClient(c: Client): Long {
        var id = c.id
        change { d ->
            if (c.id == 0L) {
                id = nextId(d.clients) { it.id }
                d.copy(clients = d.clients + c.copy(id = id, uid = c.uid.ifEmpty { newUid() }, updatedAt = clock()))
            } else d.copy(clients = d.clients.map { if (it.id == c.id) c.copy(uid = it.uid, updatedAt = stamp(it.updatedAt)) else it })
        }
        return id
    }

    fun deleteClient(id: Long) = change { d ->
        val c = d.clients.firstOrNull { it.id == id } ?: return@change d
        val sts = d.statements.filter { it.clientId == id }
        val stIds = sts.map { it.id }.toSet()
        val items = d.items.filter { it.statementId in stIds }
        val bills = d.bills.filter { it.clientId == id }
        tomb(c.uid, Kind.CLIENT)
        sts.forEach { tomb(it.uid, Kind.STATEMENT) }
        items.forEach { tomb(it.uid, Kind.ITEM) }
        bills.forEach { tomb(it.uid, Kind.BILL) }
        d.copy(
            clients = d.clients - c,
            statements = d.statements - sts.toSet(),
            items = d.items - items.toSet(),
            bills = d.bills - bills.toSet(),
        )
    }

    fun saveStatement(s: WealthStatement): Long {
        var id = s.id
        change { d ->
            if (s.id == 0L) {
                id = nextId(d.statements) { it.id }
                d.copy(statements = d.statements + s.copy(id = id, updatedAt = clock()))
            } else d.copy(statements = d.statements.map { if (it.id == s.id) s.copy(uid = it.uid, updatedAt = stamp(it.updatedAt)) else it })
        }
        return id
    }

    /** New tax year, optionally with every item copied from another statement. Returns its id. */
    fun createYear(clientId: Long, year: Int, copyFromStatementId: Long?): Long {
        var id = 0L
        change { d ->
            id = nextId(d.statements) { it.id }
            val now = clock()
            var nextItem = nextId(d.items) { it.id }
            val copies = if (copyFromStatementId == null) emptyList() else d.items.filter { it.statementId == copyFromStatementId }
                .sortedBy { it.id }
                .map { it.copy(id = nextItem++, statementId = id, uid = newUid(), updatedAt = now) }
            d.copy(
                statements = d.statements + WealthStatement(id = id, clientId = clientId, taxYear = year, updatedAt = now),
                items = d.items + copies,
            )
        }
        return id
    }

    fun deleteStatement(id: Long) = change { d ->
        val s = d.statements.firstOrNull { it.id == id } ?: return@change d
        val items = d.items.filter { it.statementId == id }
        tomb(s.uid, Kind.STATEMENT)
        items.forEach { tomb(it.uid, Kind.ITEM) }
        d.copy(statements = d.statements - s, items = d.items - items.toSet())
    }

    fun saveItem(i: WealthItem) = change { d ->
        if (i.id == 0L) d.copy(items = d.items + i.copy(id = nextId(d.items) { it.id }, updatedAt = clock()))
        else d.copy(items = d.items.map { if (it.id == i.id) i.copy(uid = it.uid, updatedAt = stamp(it.updatedAt)) else it })
    }

    fun deleteItem(id: Long) = change { d ->
        val i = d.items.firstOrNull { it.id == id } ?: return@change d
        tomb(i.uid, Kind.ITEM)
        d.copy(items = d.items - i)
    }

    fun saveBill(b: ElectricityBill) = change { d ->
        if (b.id == 0L) d.copy(bills = d.bills + b.copy(id = nextId(d.bills) { it.id }, updatedAt = clock()))
        else d.copy(bills = d.bills.map { if (it.id == b.id) b.copy(uid = it.uid, updatedAt = stamp(it.updatedAt)) else it })
    }

    fun deleteBill(id: Long) = change { d ->
        val b = d.bills.firstOrNull { it.id == id } ?: return@change d
        tomb(b.uid, Kind.BILL)
        d.copy(bills = d.bills - b)
    }

    fun saveCategory(c: Category) = change { d ->
        if (c.id == 0L) d.copy(categories = d.categories + c.copy(id = nextId(d.categories) { it.id }, updatedAt = clock()))
        else d.copy(categories = d.categories.map { if (it.id == c.id) c.copy(uid = it.uid, updatedAt = stamp(it.updatedAt)) else it })
    }

    /** False when items still use the category. */
    fun deleteCategory(id: Long): Boolean {
        if (data.items.any { it.categoryId == id }) return false
        change { d ->
            val c = d.categories.firstOrNull { it.id == id } ?: return@change d
            val subs = d.subTypes.filter { it.categoryId == id }
            tomb(c.uid, Kind.CATEGORY)
            subs.forEach { tomb(it.uid, Kind.SUB_TYPE) }
            d.copy(categories = d.categories - c, subTypes = d.subTypes - subs.toSet())
        }
        return true
    }

    fun addSubType(categoryId: Long, name: String) = change { d ->
        d.copy(subTypes = d.subTypes + PropertySubType(id = nextId(d.subTypes) { it.id }, categoryId = categoryId, name = name, updatedAt = clock()))
    }

    fun deleteSubType(id: Long) = change { d ->
        val s = d.subTypes.firstOrNull { it.id == id } ?: return@change d
        tomb(s.uid, Kind.SUB_TYPE)
        d.copy(subTypes = d.subTypes - s)
    }

    fun saveDisco(x: Disco) = change { d ->
        d.copy(discos = d.discos.map { if (it.code == x.code) x.copy(updatedAt = stamp(it.updatedAt)) else it })
    }

    /** Excel import: new clients and changed existing ones. */
    fun importClients(add: List<Client>, update: List<Client>) = change { d ->
        val now = clock()
        var next = nextId(d.clients) { it.id }
        val byId = update.associateBy { it.id }
        d.copy(
            clients = d.clients.map { old -> byId[old.id]?.copy(uid = old.uid, updatedAt = stamp(old.updatedAt)) ?: old } +
                add.map { it.copy(id = next++, updatedAt = now) },
        )
    }

    /** Restore from a backup: everything is replaced, and sync passes that on. */
    fun replaceAll(b: BackupData) = synchronized(lock) {
        val now = clock()
        val old = data
        val oldUids = listOf(
            old.clients.map { it.uid to Kind.CLIENT }, old.categories.map { it.uid to Kind.CATEGORY },
            old.subTypes.map { it.uid to Kind.SUB_TYPE }, old.statements.map { it.uid to Kind.STATEMENT },
            old.items.map { it.uid to Kind.ITEM }, old.bills.map { it.uid to Kind.BILL },
        ).flatten()
        val restored = BackupData(
            clients = b.clients.map { it.copy(updatedAt = now) },
            categories = b.categories.map { it.copy(updatedAt = now) },
            subTypes = b.subTypes.map { it.copy(updatedAt = now) },
            statements = b.statements.map { it.copy(updatedAt = now) },
            items = b.items.map { it.copy(updatedAt = now) },
            bills = b.bills.map { it.copy(updatedAt = now) },
            discos = if (b.discos.isEmpty()) old.discos else b.discos.map { it.copy(updatedAt = now) },
        )
        val keep = (restored.clients.map { it.uid } + restored.categories.map { it.uid } + restored.subTypes.map { it.uid } +
            restored.statements.map { it.uid } + restored.items.map { it.uid } + restored.bills.map { it.uid }).toSet()
        val gone = oldUids.filter { it.first !in keep }.map { (u, k) -> Tombstone(u, k, now) }
        deleted = (deleted.filter { it.uid !in keep && gone.none { g -> g.uid == it.uid } } + gone)
        data = restored
        save()
    }

    /**
     * One sync step under the store's lock: [run] gets the current data and returns
     * the new data and tombstones (or null for no change).
     */
    fun <R> syncWith(run: (BackupData, List<Tombstone>) -> Triple<BackupData, List<Tombstone>, R>): R = synchronized(lock) {
        val (newData, newDeleted, r) = run(data, deleted)
        if (newData != data || newDeleted.toSet() != deleted.toSet()) {
            data = newData
            deleted = newDeleted
            save()
        }
        r
    }

    companion object {
        private val MAGIC = "FBRPC1".toByteArray()

        /** A new PC: the built-in categories, sub-types and electricity companies. */
        fun empty() = BackupData(
            clients = emptyList(),
            categories = SeedData.categories(),
            subTypes = SeedData.subTypes(),
            statements = emptyList(),
            items = emptyList(),
            bills = emptyList(),
            discos = DiscoSeed.ALL,
        )
    }
}
