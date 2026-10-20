package pk.fbrclients.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The .fbrbak file:
 *   "FBRBAK" | version (1 byte) | PBKDF2 iterations (4) | salt (16) | IV (12) | AES-256-GCM(gzip(JSON))
 * The key comes from the backup password (PBKDF2-HMAC-SHA256). The header is
 * authenticated too, so any change to the file is detected.
 */
object BackupFile {
    private val MAGIC = "FBRBAK".toByteArray()
    private const val VERSION: Byte = 1
    const val ITERATIONS = 310_000
    const val MIN_PASSWORD = 8

    class WrongPasswordException : Exception("Wrong password, or the file is damaged")
    class NotABackupException : Exception("This is not an FBR Clients backup file")

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 4 + 16 + 12)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(gzip(plain))
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val headerSize = MAGIC.size + 1 + 4 + 16 + 12
        if (file.size < headerSize + 16 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackupException()
        val buf = ByteBuffer.wrap(file)
        buf.position(MAGIC.size)
        if (buf.get() != VERSION) throw NotABackupException()
        val iterations = buf.int
        if (iterations !in 10_000..10_000_000) throw NotABackupException()
        val salt = ByteArray(16).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(file.copyOfRange(0, headerSize))
        val plain = try {
            cipher.doFinal(file, headerSize, file.size - headerSize)
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return gunzip(plain)
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(b: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()

    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}

/** BackupData <-> JSON. Field names are written out in full so the format stays readable. */
object BackupJson {
    const val FORMAT = 1

    fun write(d: BackupData, createdAt: String): ByteArray {
        val root = JSONObject()
            .put("app", "FBR Clients")
            .put("format", FORMAT)
            .put("createdAt", createdAt)
            .put("clients", JSONArray(d.clients.map { RecordJson.client(it).put("id", it.id) }))
            .put("categories", JSONArray(d.categories.map { RecordJson.category(it).put("id", it.id) }))
            .put("subTypes", JSONArray(d.subTypes.map { RecordJson.subType(it).put("id", it.id).put("categoryId", it.categoryId) }))
            .put("statements", JSONArray(d.statements.map { RecordJson.statement(it).put("id", it.id).put("clientId", it.clientId) }))
            .put("bills", JSONArray(d.bills.map { RecordJson.bill(it).put("id", it.id).put("clientId", it.clientId) }))
            .put("discos", JSONArray(d.discos.map { RecordJson.disco(it) }))
            .put("items", JSONArray(d.items.map { RecordJson.item(it).put("id", it.id).put("statementId", it.statementId).put("categoryId", it.categoryId) }))
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun read(bytes: ByteArray): BackupData {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.optInt("format", -1) != FORMAT) throw BackupFile.NotABackupException()
        return BackupData(
            clients = root.getJSONArray("clients").objects().map { RecordJson.readClient(it).copy(id = it.getLong("id")) },
            categories = root.getJSONArray("categories").objects().map { RecordJson.readCategory(it).copy(id = it.getLong("id")) },
            subTypes = root.getJSONArray("subTypes").objects().map {
                RecordJson.readSubType(it).copy(id = it.getLong("id"), categoryId = it.getLong("categoryId"))
            },
            statements = root.getJSONArray("statements").objects().map {
                RecordJson.readStatement(it).copy(id = it.getLong("id"), clientId = it.getLong("clientId"))
            },
            items = root.getJSONArray("items").objects().map {
                RecordJson.readItem(it).copy(id = it.getLong("id"), statementId = it.getLong("statementId"), categoryId = it.getLong("categoryId"))
            },
            bills = root.optJSONArray("bills")?.objects().orEmpty().map {
                RecordJson.readBill(it).copy(id = it.getLong("id"), clientId = it.getLong("clientId"))
            },
            discos = root.optJSONArray("discos")?.objects().orEmpty().map { RecordJson.readDisco(it) },
        )
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/**
 * The fields of each record, shared by the backup file and the sync file.
 * Ids and links to other records are added by the caller (numbers in a backup, uids in sync).
 * Records from backups made before sync existed get a new uid and updatedAt 0.
 */
object RecordJson {
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)

    /** Optional field (older files may not have it). */
    private fun JSONObject.optText(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONObject.uid(): String = optText("uid")?.takeIf { it.isNotEmpty() } ?: newUid()
    private fun JSONObject.updated(): Long = optLong("updatedAt", 0)
    private fun JSONObject.stamp(uid: String, updatedAt: Long) = put("uid", uid).put("updatedAt", updatedAt)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    fun client(c: Client): JSONObject = JSONObject().put("cnic", c.cnic).put("irisPassword", c.irisPassword).put("name", c.name)
        .put("businessName", c.businessName)
        .put("ntn", c.ntn).put("mobile", c.mobile).put("email", c.email).put("address", c.address).put("notes", c.notes)
        .stamp(c.uid, c.updatedAt)

    fun readClient(o: JSONObject) = Client(
        cnic = o.getString("cnic"), irisPassword = o.getString("irisPassword"),
        name = o.getString("name"), businessName = o.optString("businessName", ""), ntn = o.getString("ntn"), mobile = o.getString("mobile"),
        email = o.getString("email"), address = o.getString("address"), notes = o.getString("notes"),
        uid = o.uid(), updatedAt = o.updated(),
    )

    fun category(c: Category): JSONObject = JSONObject().put("code", c.code.orNull()).put("name", c.name).put("group", c.group.name)
        .put("isProperty", c.isProperty).put("sortOrder", c.sortOrder).put("builtIn", c.builtIn).put("editable", c.editable)
        .stamp(c.uid, c.updatedAt)

    fun readCategory(o: JSONObject) = Category(
        code = o.str("code"), name = o.getString("name"),
        group = AssetGroup.valueOf(o.getString("group")), isProperty = o.getBoolean("isProperty"),
        sortOrder = o.getInt("sortOrder"), builtIn = o.getBoolean("builtIn"), editable = o.getBoolean("editable"),
        uid = o.uid(), updatedAt = o.updated(),
    )

    fun subType(s: PropertySubType): JSONObject = JSONObject().put("name", s.name).stamp(s.uid, s.updatedAt)

    fun readSubType(o: JSONObject) = PropertySubType(categoryId = 0, name = o.getString("name"), uid = o.uid(), updatedAt = o.updated())

    fun statement(s: WealthStatement): JSONObject = JSONObject().put("taxYear", s.taxYear)
        .put("prevYearNetAssets", s.prevYearNetAssets.orNull())
        .put("incomeNormal", s.incomeNormal).put("incomeExempt", s.incomeExempt)
        .put("incomeFinal", s.incomeFinal).put("personalExpenses", s.personalExpenses)
        .stamp(s.uid, s.updatedAt)

    fun readStatement(o: JSONObject) = WealthStatement(
        clientId = 0, taxYear = o.getInt("taxYear"),
        prevYearNetAssets = if (o.isNull("prevYearNetAssets")) null else o.getLong("prevYearNetAssets"),
        incomeNormal = o.getLong("incomeNormal"), incomeExempt = o.getLong("incomeExempt"),
        incomeFinal = o.getLong("incomeFinal"), personalExpenses = o.getLong("personalExpenses"),
        uid = o.uid(), updatedAt = o.updated(),
    )

    fun item(i: WealthItem): JSONObject = JSONObject()
        .put("description", i.description).put("amount", i.amount)
        .put("subType", i.subType.orNull()).put("acquisitionDate", i.acquisitionDate.orNull())
        .put("areaUnit", i.areaUnit.orNull()).put("area", i.area.orNull())
        .put("propertyAddress", i.propertyAddress.orNull())
        .put("holderCnic", i.holderCnic.orNull())
        .put("areaAcre", i.areaAcre.orNull()).put("areaKanal", i.areaKanal.orNull())
        .put("areaMarla", i.areaMarla.orNull()).put("areaSarsahi", i.areaSarsahi.orNull())
        .put("areaSft", i.areaSft.orNull()).put("areaSqm", i.areaSqm.orNull())
        .stamp(i.uid, i.updatedAt)

    fun readItem(o: JSONObject) = WealthItem(
        statementId = 0, categoryId = 0,
        description = o.getString("description"), amount = o.getLong("amount"),
        subType = o.str("subType"), acquisitionDate = o.str("acquisitionDate"), areaUnit = o.str("areaUnit"),
        area = o.str("area"), propertyAddress = o.str("propertyAddress"),
        holderCnic = o.optText("holderCnic"),
        areaAcre = o.optText("areaAcre"), areaKanal = o.optText("areaKanal"), areaMarla = o.optText("areaMarla"),
        areaSarsahi = o.optText("areaSarsahi"), areaSft = o.optText("areaSft"), areaSqm = o.optText("areaSqm"),
        uid = o.uid(), updatedAt = o.updated(),
    )

    fun bill(b: ElectricityBill): JSONObject = JSONObject().put("company", b.company)
        .put("referenceNo", b.referenceNo).put("label", b.label).stamp(b.uid, b.updatedAt)

    fun readBill(o: JSONObject) = ElectricityBill(
        clientId = 0, company = o.getString("company"), referenceNo = o.getString("referenceNo"), label = o.getString("label"),
        uid = o.uid(), updatedAt = o.updated(),
    )

    fun disco(x: Disco): JSONObject = JSONObject().put("code", x.code).put("name", x.name).put("billUrl", x.billUrl)
        .put("certUrl", x.certUrl).put("sortOrder", x.sortOrder).put("updatedAt", x.updatedAt)

    fun readDisco(o: JSONObject) = Disco(
        code = o.getString("code"), name = o.getString("name"), billUrl = o.getString("billUrl"),
        certUrl = o.getString("certUrl"), sortOrder = o.getInt("sortOrder"), updatedAt = o.updated(),
    )
}
