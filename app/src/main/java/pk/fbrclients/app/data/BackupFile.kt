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
            .put("clients", JSONArray(d.clients.map { c ->
                JSONObject().put("id", c.id).put("cnic", c.cnic).put("irisPassword", c.irisPassword).put("name", c.name)
                    .put("businessName", c.businessName)
                    .put("ntn", c.ntn).put("mobile", c.mobile).put("email", c.email).put("address", c.address).put("notes", c.notes)
            }))
            .put("categories", JSONArray(d.categories.map { c ->
                JSONObject().put("id", c.id).put("code", c.code ?: JSONObject.NULL).put("name", c.name).put("group", c.group.name)
                    .put("isProperty", c.isProperty).put("sortOrder", c.sortOrder).put("builtIn", c.builtIn).put("editable", c.editable)
            }))
            .put("subTypes", JSONArray(d.subTypes.map { s ->
                JSONObject().put("id", s.id).put("categoryId", s.categoryId).put("name", s.name)
            }))
            .put("statements", JSONArray(d.statements.map { s ->
                JSONObject().put("id", s.id).put("clientId", s.clientId).put("taxYear", s.taxYear)
                    .put("prevYearNetAssets", s.prevYearNetAssets ?: JSONObject.NULL)
                    .put("incomeNormal", s.incomeNormal).put("incomeExempt", s.incomeExempt)
                    .put("incomeFinal", s.incomeFinal).put("personalExpenses", s.personalExpenses)
            }))
            .put("items", JSONArray(d.items.map { i ->
                JSONObject().put("id", i.id).put("statementId", i.statementId).put("categoryId", i.categoryId)
                    .put("description", i.description).put("amount", i.amount)
                    .put("subType", i.subType ?: JSONObject.NULL).put("acquisitionDate", i.acquisitionDate ?: JSONObject.NULL)
                    .put("areaUnit", i.areaUnit ?: JSONObject.NULL).put("area", i.area ?: JSONObject.NULL)
                    .put("propertyAddress", i.propertyAddress ?: JSONObject.NULL)
                    .put("holderCnic", i.holderCnic ?: JSONObject.NULL)
                    .put("areaAcre", i.areaAcre ?: JSONObject.NULL).put("areaKanal", i.areaKanal ?: JSONObject.NULL)
                    .put("areaMarla", i.areaMarla ?: JSONObject.NULL).put("areaSarsahi", i.areaSarsahi ?: JSONObject.NULL)
                    .put("areaSft", i.areaSft ?: JSONObject.NULL).put("areaSqm", i.areaSqm ?: JSONObject.NULL)
            }))
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun read(bytes: ByteArray): BackupData {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.optInt("format", -1) != FORMAT) throw BackupFile.NotABackupException()
        return BackupData(
            clients = root.getJSONArray("clients").objects().map { o ->
                Client(
                    id = o.getLong("id"), cnic = o.getString("cnic"), irisPassword = o.getString("irisPassword"),
                    name = o.getString("name"), businessName = o.optString("businessName", ""), ntn = o.getString("ntn"), mobile = o.getString("mobile"),
                    email = o.getString("email"), address = o.getString("address"), notes = o.getString("notes"),
                )
            },
            categories = root.getJSONArray("categories").objects().map { o ->
                Category(
                    id = o.getLong("id"), code = o.str("code"), name = o.getString("name"),
                    group = AssetGroup.valueOf(o.getString("group")), isProperty = o.getBoolean("isProperty"),
                    sortOrder = o.getInt("sortOrder"), builtIn = o.getBoolean("builtIn"), editable = o.getBoolean("editable"),
                )
            },
            subTypes = root.getJSONArray("subTypes").objects().map { o ->
                PropertySubType(id = o.getLong("id"), categoryId = o.getLong("categoryId"), name = o.getString("name"))
            },
            statements = root.getJSONArray("statements").objects().map { o ->
                WealthStatement(
                    id = o.getLong("id"), clientId = o.getLong("clientId"), taxYear = o.getInt("taxYear"),
                    prevYearNetAssets = if (o.isNull("prevYearNetAssets")) null else o.getLong("prevYearNetAssets"),
                    incomeNormal = o.getLong("incomeNormal"), incomeExempt = o.getLong("incomeExempt"),
                    incomeFinal = o.getLong("incomeFinal"), personalExpenses = o.getLong("personalExpenses"),
                )
            },
            items = root.getJSONArray("items").objects().map { o ->
                WealthItem(
                    id = o.getLong("id"), statementId = o.getLong("statementId"), categoryId = o.getLong("categoryId"),
                    description = o.getString("description"), amount = o.getLong("amount"),
                    subType = o.str("subType"), acquisitionDate = o.str("acquisitionDate"), areaUnit = o.str("areaUnit"),
                    area = o.str("area"), propertyAddress = o.str("propertyAddress"),
                    holderCnic = o.optText("holderCnic"),
                    areaAcre = o.optText("areaAcre"), areaKanal = o.optText("areaKanal"), areaMarla = o.optText("areaMarla"),
                    areaSarsahi = o.optText("areaSarsahi"), areaSft = o.optText("areaSft"), areaSqm = o.optText("areaSqm"),
                )
            },
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)

    /** Optional field (older backups may not have it). */
    private fun JSONObject.optText(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
}
