package pk.fbrclients.app.security

import android.content.Context
import android.util.Base64

/**
 * Stores the PIN as a salted PBKDF2 hash, which is itself encrypted with a
 * Keystore key. Also holds lock settings (fingerprint on/off, auto-lock time)
 * and the wrong-attempt counter.
 */
class PinStore(context: Context) {

    private val prefs = context.getSharedPreferences("lock", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("fbr_pin_key")

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin))
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, salt.b64())
            .putString(KEY_HASH, cipher.encrypt(hash).b64())
            .putInt(KEY_ITER, PinHasher.ITERATIONS)
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .apply()
    }

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val attemptsLeftBeforePause: Int) : Result
        data class Paused(val untilMillis: Long) : Result
    }

    fun check(pin: String, now: Long = System.currentTimeMillis()): Result {
        val until = pausedUntil
        if (until > now) return Result.Paused(until)

        val salt = prefs.getString(KEY_SALT, null)?.unb64() ?: return Result.Wrong(0)
        val stored = prefs.getString(KEY_HASH, null)?.unb64() ?: return Result.Wrong(0)
        val iter = prefs.getInt(KEY_ITER, PinHasher.ITERATIONS)
        val expected = cipher.decrypt(stored)

        if (PinHasher.matches(pin, salt, expected, iter)) {
            resetFailures()
            return Result.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val delay = LockoutPolicy.delayAfter(fails)
        prefs.edit()
            .putInt(KEY_FAILS, fails)
            .putLong(KEY_LOCKED_UNTIL, if (delay > 0) now + delay else 0L)
            .apply()
        return if (delay > 0) Result.Paused(now + delay)
        else Result.Wrong(LockoutPolicy.FREE_ATTEMPTS - fails)
    }

    fun resetFailures() {
        prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
    }

    val pausedUntil: Long get() = prefs.getLong(KEY_LOCKED_UNTIL, 0L)

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIO, true)
        set(v) = prefs.edit().putBoolean(KEY_BIO, v).apply()

    /** Auto-lock delay after the app goes to the background, in seconds. */
    var autoLockSeconds: Int
        get() = prefs.getInt(KEY_AUTOLOCK, 60)
        set(v) = prefs.edit().putInt(KEY_AUTOLOCK, v).apply()

    /** When the last backup file was saved (0 = never). */
    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_BACKUP, v).apply()

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_ITER = "pin_iter"
        const val KEY_FAILS = "pin_fails"
        const val KEY_LOCKED_UNTIL = "pin_locked_until"
        const val KEY_BIO = "biometric_enabled"
        const val KEY_AUTOLOCK = "auto_lock_seconds"
        const val KEY_LAST_BACKUP = "last_backup_at"
    }
}
