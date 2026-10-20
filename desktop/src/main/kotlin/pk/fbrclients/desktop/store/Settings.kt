package pk.fbrclients.desktop.store

import org.json.JSONObject
import pk.fbrclients.app.security.LockoutPolicy
import pk.fbrclients.app.security.PinHasher
import pk.fbrclients.desktop.platform.AppPaths
import pk.fbrclients.desktop.platform.SecretBox
import pk.fbrclients.desktop.platform.writeAtomically
import java.io.File
import java.util.Base64

/**
 * Small settings of the PC app (settings.json). The PIN hash and the sync password
 * are locked to this Windows user (SecretBox) before they are written.
 */
class Settings(private val file: File = AppPaths.settings, private val clock: () -> Long = System::currentTimeMillis) {
    private var o: JSONObject = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }

    @Synchronized private fun save() = writeAtomically(file, o.toString(2).toByteArray(Charsets.UTF_8))

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)

    private fun putSecret(k: String, v: ByteArray?) {
        if (v == null) o.remove(k) else o.put(k, b64(SecretBox.protect(v)))
    }

    private fun secret(k: String): ByteArray? = o.optString(k).takeIf { it.isNotEmpty() }?.let { runCatching { SecretBox.unprotect(unb64(it)) }.getOrNull() }

    // ---------------- PIN ----------------

    val hasPin get() = o.has("pinHash")

    fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin))
        val salt = PinHasher.newSalt()
        o.put("pinSalt", b64(salt)).put("pinIter", PinHasher.ITERATIONS).put("pinFails", 0).put("pinPausedUntil", 0L)
        putSecret("pinHash", PinHasher.hash(pin, salt))
        save()
    }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeftBeforePause: Int) : PinResult
        data class Paused(val untilMillis: Long) : PinResult
    }

    fun checkPin(pin: String): PinResult {
        val now = clock()
        val until = o.optLong("pinPausedUntil", 0L)
        if (until > now) return PinResult.Paused(until)
        val salt = o.optString("pinSalt").takeIf { it.isNotEmpty() }?.let(::unb64) ?: return PinResult.Wrong(0)
        val expected = secret("pinHash") ?: return PinResult.Wrong(0)
        if (PinHasher.matches(pin, salt, expected, o.optInt("pinIter", PinHasher.ITERATIONS))) {
            o.put("pinFails", 0).put("pinPausedUntil", 0L)
            save()
            return PinResult.Ok
        }
        val fails = o.optInt("pinFails", 0) + 1
        val delay = LockoutPolicy.delayAfter(fails)
        o.put("pinFails", fails).put("pinPausedUntil", if (delay > 0) now + delay else 0L)
        save()
        return if (delay > 0) PinResult.Paused(now + delay) else PinResult.Wrong(LockoutPolicy.FREE_ATTEMPTS - fails)
    }

    /** Minutes without use before the app locks itself. */
    var autoLockMinutes: Int
        get() = o.optInt("autoLockMinutes", 5)
        set(v) { o.put("autoLockMinutes", v); save() }

    var lastBackupAt: Long
        get() = o.optLong("lastBackupAt", 0L)
        set(v) { o.put("lastBackupAt", v); save() }

    // ---------------- Sync ----------------

    val syncSetUp get() = o.optString("syncFile").isNotEmpty() && o.has("syncPassword")

    val syncFile: File? get() = o.optString("syncFile").takeIf { it.isNotEmpty() }?.let(::File)

    fun syncPassword(): CharArray? = secret("syncPassword")?.let { String(it, Charsets.UTF_8).toCharArray() }

    fun setSync(file: File, password: CharArray) {
        o.put("syncFile", file.absolutePath)
        putSecret("syncPassword", String(password).toByteArray(Charsets.UTF_8))
        o.remove("syncAt"); o.remove("syncMessage"); o.remove("syncFailed")
        save()
    }

    fun clearSync() {
        listOf("syncFile", "syncPassword", "syncAt", "syncMessage", "syncFailed").forEach(o::remove)
        save()
    }

    fun saveSyncStatus(at: Long, message: String, failed: Boolean) {
        o.put("syncAt", at).put("syncMessage", message).put("syncFailed", failed)
        save()
    }

    val syncAt get() = o.optLong("syncAt", 0L)
    val syncMessage: String? get() = o.optString("syncMessage").takeIf { it.isNotEmpty() }
    val syncFailed get() = o.optBoolean("syncFailed", false)
}
