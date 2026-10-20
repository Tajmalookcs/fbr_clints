package pk.fbrclients.app.data.sync

import org.json.JSONArray
import org.json.JSONObject
import pk.fbrclients.app.data.BackupData
import pk.fbrclients.app.data.BackupFile
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.Disco
import pk.fbrclients.app.data.ElectricityBill
import pk.fbrclients.app.data.Kind
import pk.fbrclients.app.data.PropertySubType
import pk.fbrclients.app.data.RecordJson
import pk.fbrclients.app.data.Tombstone
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.WealthStatement
import pk.fbrclients.app.data.objects

/*
 * Sync between the phone and the PC (docs/DESIGN.md §9).
 *
 * Every device keeps all its data. The sync file holds a full copy too.
 * Records are matched by uid (the same on every device); links between records
 * use uids, because each device numbers its records differently.
 * When the same record was changed on two devices, the newest change wins.
 * Deleted records are remembered (tombstones), so deletions spread too.
 * Merging is done the same way on every device, so all copies end up equal.
 */

data class SStatement(val s: WealthStatement, val clientUid: String)
data class SItem(val i: WealthItem, val statementUid: String, val categoryUid: String)
data class SBill(val b: ElectricityBill, val clientUid: String)
data class SSubType(val t: PropertySubType, val categoryUid: String)

/** All data, linked by uid. The ids inside the records mean nothing here (always 0). */
data class SyncSet(
    val clients: List<Client> = emptyList(),
    val categories: List<Category> = emptyList(),
    val subTypes: List<SSubType> = emptyList(),
    val statements: List<SStatement> = emptyList(),
    val items: List<SItem> = emptyList(),
    val bills: List<SBill> = emptyList(),
    val discos: List<Disco> = emptyList(),
    val deleted: List<Tombstone> = emptyList(),
) {
    /** Same content, whatever the order of the lists. */
    fun normalized(): SyncSet = SyncSet(
        clients.sortedBy { it.uid }, categories.sortedBy { it.uid }, subTypes.sortedBy { it.t.uid },
        statements.sortedBy { it.s.uid }, items.sortedBy { it.i.uid }, bills.sortedBy { it.b.uid },
        discos.sortedBy { it.code }, deleted.sortedBy { it.uid },
    )

    val recordCount get() = clients.size + categories.size + subTypes.size + statements.size + items.size + bills.size
}

object Sync {

    // ---------------- Local data (numbered) <-> SyncSet (uids) ----------------

    fun toSync(d: BackupData, deleted: List<Tombstone>): SyncSet {
        val clientUid = d.clients.associate { it.id to it.uid }
        val categoryUid = d.categories.associate { it.id to it.uid }
        val statementUid = d.statements.associate { it.id to it.uid }
        return SyncSet(
            clients = d.clients.map { it.copy(id = 0) },
            categories = d.categories.map { it.copy(id = 0) },
            subTypes = d.subTypes.mapNotNull { t -> categoryUid[t.categoryId]?.let { SSubType(t.copy(id = 0, categoryId = 0), it) } },
            statements = d.statements.mapNotNull { s -> clientUid[s.clientId]?.let { SStatement(s.copy(id = 0, clientId = 0), it) } },
            items = d.items.mapNotNull { i ->
                val st = statementUid[i.statementId] ?: return@mapNotNull null
                val cat = categoryUid[i.categoryId] ?: return@mapNotNull null
                SItem(i.copy(id = 0, statementId = 0, categoryId = 0), st, cat)
            },
            bills = d.bills.mapNotNull { b -> clientUid[b.clientId]?.let { SBill(b.copy(id = 0, clientId = 0), it) } },
            discos = d.discos,
            deleted = deleted,
        )
    }

    /**
     * The local data after taking in [merged]. Records keep their local numbers;
     * new records get new numbers after the highest one in use.
     */
    fun applyTo(local: BackupData, merged: SyncSet): BackupData {
        fun nextIds(existing: List<Long>): () -> Long {
            var n = (existing.maxOrNull() ?: 0L)
            return { ++n }
        }

        val oldClient = local.clients.associateBy { it.uid }
        val nextClient = nextIds(local.clients.map { it.id })
        val clients = merged.clients.map { c -> c.copy(id = oldClient[c.uid]?.id ?: nextClient()) }
        val clientId = clients.associate { it.uid to it.id }

        val oldCat = local.categories.associateBy { it.uid }
        val nextCat = nextIds(local.categories.map { it.id })
        val categories = merged.categories.map { c -> c.copy(id = oldCat[c.uid]?.id ?: nextCat()) }
        val categoryId = categories.associate { it.uid to it.id }

        val oldSub = local.subTypes.associateBy { it.uid }
        val nextSub = nextIds(local.subTypes.map { it.id })
        val subTypes = merged.subTypes.mapNotNull { s ->
            val cat = categoryId[s.categoryUid] ?: return@mapNotNull null
            s.t.copy(id = oldSub[s.t.uid]?.id ?: nextSub(), categoryId = cat)
        }

        val oldSt = local.statements.associateBy { it.uid }
        val nextSt = nextIds(local.statements.map { it.id })
        val statements = merged.statements.mapNotNull { s ->
            val cl = clientId[s.clientUid] ?: return@mapNotNull null
            s.s.copy(id = oldSt[s.s.uid]?.id ?: nextSt(), clientId = cl)
        }
        val statementId = statements.associate { it.uid to it.id }

        val oldItem = local.items.associateBy { it.uid }
        val nextItem = nextIds(local.items.map { it.id })
        val items = merged.items.mapNotNull { i ->
            val st = statementId[i.statementUid] ?: return@mapNotNull null
            val cat = categoryId[i.categoryUid] ?: return@mapNotNull null
            i.i.copy(id = oldItem[i.i.uid]?.id ?: nextItem(), statementId = st, categoryId = cat)
        }

        val oldBill = local.bills.associateBy { it.uid }
        val nextBill = nextIds(local.bills.map { it.id })
        val bills = merged.bills.mapNotNull { b ->
            val cl = clientId[b.clientUid] ?: return@mapNotNull null
            b.b.copy(id = oldBill[b.b.uid]?.id ?: nextBill(), clientId = cl)
        }

        // Electricity companies are never deleted; keep local ones the merge doesn't know.
        val discoCodes = merged.discos.map { it.code }.toSet()
        val discos = merged.discos + local.discos.filter { it.code !in discoCodes }

        return BackupData(clients, categories, subTypes, statements, items, bills, discos)
    }

    // ---------------- Merge ----------------

    private fun <T> newer(a: T, b: T, updatedAt: (T) -> Long, tieBreak: (T) -> String): T {
        val ua = updatedAt(a)
        val ub = updatedAt(b)
        return when {
            ua > ub -> a
            ub > ua -> b
            else -> if (tieBreak(a) >= tieBreak(b)) a else b
        }
    }

    private fun <T> union(a: List<T>, b: List<T>, key: (T) -> String, updatedAt: (T) -> Long): LinkedHashMap<String, T> {
        val out = LinkedHashMap<String, T>()
        for (x in a + b) {
            val k = key(x)
            val old = out[k]
            out[k] = if (old == null) x else newer(old, x, updatedAt) { it.toString() }
        }
        return out
    }

    /** Joins two copies of the data. The result is the same whichever copy comes first. */
    fun merge(a: SyncSet, b: SyncSet): SyncSet {
        // Tombstones: the latest deletion of each uid.
        val tombs = LinkedHashMap<String, Tombstone>()
        for (t in a.deleted + b.deleted) {
            val old = tombs[t.uid]
            tombs[t.uid] = if (old == null) t else newer(old, t, { it.deletedAt }) { it.toString() }
        }
        fun alive(uid: String, updatedAt: Long) = tombs[uid].let { it == null || it.deletedAt < updatedAt }

        /** Follows "joined into" links to the record that was kept. */
        fun resolve(uid: String): String {
            var u = uid
            repeat(20) { u = tombs[u]?.replacedBy ?: return u }
            return u
        }

        fun tombstoneFor(uid: String, kind: String, at: Long, replacedBy: String?) {
            val old = tombs[uid]
            if (old == null || old.deletedAt < at) tombs[uid] = Tombstone(uid, kind, at, replacedBy)
        }

        // ---- Clients: newest wins; two clients with the same username are joined.
        val allClients = union(a.clients, b.clients, { it.uid }, { it.updatedAt })
        val clients = LinkedHashMap<String, Client>()
        allClients.values.filter { alive(it.uid, it.updatedAt) }
            .groupBy { it.cnic }
            .forEach { (_, same) ->
                val keep = same.sortedWith(compareByDescending<Client> { it.updatedAt }.thenByDescending { it.uid }).first()
                clients[keep.uid] = keep
                same.filter { it !== keep }.forEach { tombstoneFor(it.uid, Kind.CLIENT, keep.updatedAt.coerceAtLeast(it.updatedAt), keep.uid) }
            }

        // ---- Statements: one per client and tax year.
        val allStatements = union(a.statements, b.statements, { it.s.uid }, { it.s.updatedAt })
        val statements = LinkedHashMap<String, SStatement>()
        allStatements.values.filter { alive(it.s.uid, it.s.updatedAt) }
            .map { it.copy(clientUid = resolve(it.clientUid)) }
            .filter { it.clientUid in clients }
            .groupBy { it.clientUid to it.s.taxYear }
            .forEach { (_, same) ->
                val keep = same.sortedWith(compareByDescending<SStatement> { it.s.updatedAt }.thenByDescending { it.s.uid }).first()
                statements[keep.s.uid] = keep
                same.filter { it !== keep }.forEach { tombstoneFor(it.s.uid, Kind.STATEMENT, keep.s.updatedAt.coerceAtLeast(it.s.updatedAt), keep.s.uid) }
            }

        // ---- Categories (deleted ones still used by an item are kept).
        val allCategories = union(a.categories, b.categories, { it.uid }, { it.updatedAt })

        // ---- Items: kept when their statement is.
        val allItems = union(a.items, b.items, { it.i.uid }, { it.i.updatedAt })
        val items = allItems.values.filter { alive(it.i.uid, it.i.updatedAt) }
            .map { it.copy(statementUid = resolve(it.statementUid)) }
            .filter { it.statementUid in statements && it.categoryUid in allCategories }

        val usedCategories = items.map { it.categoryUid }.toSet()
        val categories = allCategories.values.filter { c ->
            if (alive(c.uid, c.updatedAt)) true
            else if (c.uid in usedCategories) { tombs.remove(c.uid); true }
            else false
        }.associateBy { it.uid }

        // ---- Sub-types: one per category and name.
        val allSubs = union(a.subTypes, b.subTypes, { it.t.uid }, { it.t.updatedAt })
        val subTypes = allSubs.values.filter { alive(it.t.uid, it.t.updatedAt) && it.categoryUid in categories }
            .groupBy { it.categoryUid to it.t.name.trim().lowercase() }
            .map { (_, same) ->
                val keep = same.sortedWith(compareByDescending<SSubType> { it.t.updatedAt }.thenByDescending { it.t.uid }).first()
                same.filter { it !== keep }.forEach { tombstoneFor(it.t.uid, Kind.SUB_TYPE, keep.t.updatedAt.coerceAtLeast(it.t.updatedAt), keep.t.uid) }
                keep
            }

        // ---- Bills: one per client, company and reference number.
        val allBills = union(a.bills, b.bills, { it.b.uid }, { it.b.updatedAt })
        val bills = allBills.values.filter { alive(it.b.uid, it.b.updatedAt) }
            .map { it.copy(clientUid = resolve(it.clientUid)) }
            .filter { it.clientUid in clients }
            .groupBy { Triple(it.clientUid, it.b.company, it.b.referenceNo) }
            .map { (_, same) ->
                val keep = same.sortedWith(compareByDescending<SBill> { it.b.updatedAt }.thenByDescending { it.b.uid }).first()
                same.filter { it !== keep }.forEach { tombstoneFor(it.b.uid, Kind.BILL, keep.b.updatedAt.coerceAtLeast(it.b.updatedAt), keep.b.uid) }
                keep
            }

        val discos = union(a.discos, b.discos, { it.code }, { it.updatedAt }).values.toList()

        return SyncSet(
            clients = clients.values.toList(),
            categories = categories.values.toList(),
            subTypes = subTypes,
            statements = statements.values.toList(),
            items = items,
            bills = bills,
            discos = discos,
            deleted = tombs.values.toList(),
        ).normalized()
    }

    // ---------------- What changed locally ----------------

    data class Changes<T>(val added: List<T>, val changed: List<T>, val removed: List<T>) {
        val isEmpty get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
        val size get() = added.size + changed.size + removed.size
    }

    data class LocalDiff(
        val clients: Changes<Client>,
        val categories: Changes<Category>,
        val subTypes: Changes<PropertySubType>,
        val statements: Changes<WealthStatement>,
        val items: Changes<WealthItem>,
        val bills: Changes<ElectricityBill>,
        val discos: Changes<Disco>,
    ) {
        val isEmpty get() = listOf(clients, categories, subTypes, statements, items, bills, discos).all { it.isEmpty }
        val size get() = listOf(clients, categories, subTypes, statements, items, bills, discos).sumOf { it.size }
    }

    private fun <T, K> changes(old: List<T>, new: List<T>, key: (T) -> K): Changes<T> {
        val o = old.associateBy(key)
        val n = new.associateBy(key)
        return Changes(
            added = new.filter { key(it) !in o },
            changed = new.filter { x -> o[key(x)].let { it != null && it != x } },
            removed = old.filter { key(it) !in n },
        )
    }

    fun diff(old: BackupData, new: BackupData) = LocalDiff(
        clients = changes(old.clients, new.clients) { it.id },
        categories = changes(old.categories, new.categories) { it.id },
        subTypes = changes(old.subTypes, new.subTypes) { it.id },
        statements = changes(old.statements, new.statements) { it.id },
        items = changes(old.items, new.items) { it.id },
        bills = changes(old.bills, new.bills) { it.id },
        discos = changes(old.discos, new.discos) { it.code },
    )
}

/**
 * The sync file: the same encryption as a backup (BackupFile), with JSON format 2.
 * A backup restore refuses it (format 1 only), so the two can't be mixed up.
 */
object SyncFile {
    const val FORMAT = 2
    const val EXTENSION = "fbrsync"
    const val DEFAULT_NAME = "FBR-Clients.fbrsync"

    class NotASyncFileException : Exception("This is not an FBR Clients sync file")

    fun write(s: SyncSet, password: CharArray, writtenBy: String, iterations: Int = BackupFile.ITERATIONS): ByteArray =
        BackupFile.encrypt(toJson(s, writtenBy), password, iterations)

    /** Throws BackupFile.WrongPasswordException for a wrong password. */
    fun read(file: ByteArray, password: CharArray): SyncSet {
        val plain = try {
            BackupFile.decrypt(file, password)
        } catch (_: BackupFile.NotABackupException) {
            throw NotASyncFileException()
        }
        return fromJson(plain)
    }

    fun toJson(s: SyncSet, writtenBy: String): ByteArray {
        val n = s.normalized()
        val root = JSONObject()
            .put("app", "FBR Clients")
            .put("format", FORMAT)
            .put("writtenBy", writtenBy)
            .put("clients", JSONArray(n.clients.map { RecordJson.client(it) }))
            .put("categories", JSONArray(n.categories.map { RecordJson.category(it) }))
            .put("subTypes", JSONArray(n.subTypes.map { RecordJson.subType(it.t).put("categoryUid", it.categoryUid) }))
            .put("statements", JSONArray(n.statements.map { RecordJson.statement(it.s).put("clientUid", it.clientUid) }))
            .put("items", JSONArray(n.items.map { RecordJson.item(it.i).put("statementUid", it.statementUid).put("categoryUid", it.categoryUid) }))
            .put("bills", JSONArray(n.bills.map { RecordJson.bill(it.b).put("clientUid", it.clientUid) }))
            .put("discos", JSONArray(n.discos.map { RecordJson.disco(it) }))
            .put("deleted", JSONArray(n.deleted.map { t ->
                JSONObject().put("uid", t.uid).put("kind", t.kind).put("deletedAt", t.deletedAt).put("replacedBy", t.replacedBy ?: JSONObject.NULL)
            }))
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun fromJson(bytes: ByteArray): SyncSet {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.optInt("format", -1) != FORMAT) throw NotASyncFileException()
        fun arr(k: String) = root.optJSONArray(k)?.objects().orEmpty()
        return SyncSet(
            clients = arr("clients").map { RecordJson.readClient(it) },
            categories = arr("categories").map { RecordJson.readCategory(it) },
            subTypes = arr("subTypes").map { SSubType(RecordJson.readSubType(it), it.getString("categoryUid")) },
            statements = arr("statements").map { SStatement(RecordJson.readStatement(it), it.getString("clientUid")) },
            items = arr("items").map { SItem(RecordJson.readItem(it), it.getString("statementUid"), it.getString("categoryUid")) },
            bills = arr("bills").map { SBill(RecordJson.readBill(it), it.getString("clientUid")) },
            discos = arr("discos").map { RecordJson.readDisco(it) },
            deleted = arr("deleted").map {
                Tombstone(it.getString("uid"), it.getString("kind"), it.getLong("deletedAt"), if (it.isNull("replacedBy")) null else it.getString("replacedBy"))
            },
        ).normalized()
    }
}

/** One sync run, the same on the phone and the PC. */
object SyncEngine {
    data class Result(
        /** What goes into the sync file (and what every device will end up with). */
        val merged: SyncSet,
        /** This device's data afterwards, with its own numbering. */
        val newLocal: BackupData,
        val diff: Sync.LocalDiff,
        /** False when the sync file already holds exactly [merged]. */
        val writeRemote: Boolean,
    )

    /** [remotes]: the sync file (and any copies Google Drive made of it); empty when there is none yet. */
    fun run(local: BackupData, localDeleted: List<Tombstone>, remotes: List<SyncSet>): Result {
        var merged = Sync.merge(Sync.toSync(local, localDeleted), SyncSet())
        for (r in remotes) merged = Sync.merge(merged, r)
        val newLocal = Sync.applyTo(local, merged)
        return Result(
            merged = merged,
            newLocal = newLocal,
            diff = Sync.diff(local, newLocal),
            writeRemote = remotes.size != 1 || remotes[0].normalized() != merged,
        )
    }
}
