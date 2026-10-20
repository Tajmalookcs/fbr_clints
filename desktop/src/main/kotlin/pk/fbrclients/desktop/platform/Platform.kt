package pk.fbrclients.desktop.platform

import com.sun.jna.platform.win32.Crypt32Util
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Timer
import java.util.TimerTask
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")

/** Where the PC app keeps its files: %APPDATA%\FBR Clients (Windows). */
object AppPaths {
    val dir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { isWindows } ?: (System.getProperty("user.home") + File.separator + ".config")
        File(base, "FBR Clients").also { it.mkdirs() }
    }
    val data get() = File(dir, "data.fbrpc")
    val dataPrevious get() = File(dir, "data.fbrpc.previous")
    val keys get() = File(dir, "keys.bin")
    val settings get() = File(dir, "settings.json")
}

/** Writes a file safely: a temporary file first, then moved over the old one. */
fun writeAtomically(target: File, bytes: ByteArray, keepPrevious: File? = null) {
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.writeBytes(bytes)
    if (keepPrevious != null && target.exists()) {
        runCatching { Files.copy(target.toPath(), keepPrevious.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
    try {
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: Exception) {
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Locks small secrets (the data key, the PIN hash, the sync password) to this
 * Windows user with Windows data protection (DPAPI): another user, or the file
 * copied to another PC, can't open them.
 * Outside Windows (only for development) a key file is used instead.
 */
object SecretBox {
    fun protect(plain: ByteArray): ByteArray =
        if (isWindows) Crypt32Util.cryptProtectData(plain) else devCipher(Cipher.ENCRYPT_MODE, plain)

    fun unprotect(blob: ByteArray): ByteArray =
        if (isWindows) Crypt32Util.cryptUnprotectData(blob) else devCipher(Cipher.DECRYPT_MODE, blob)

    private fun devCipher(mode: Int, input: ByteArray): ByteArray {
        val f = File(AppPaths.dir, "dev-only.key")
        if (!f.exists()) f.writeBytes(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val key = SecretKeySpec(f.readBytes(), "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        return if (mode == Cipher.ENCRYPT_MODE) {
            c.init(mode, key)
            c.iv + c.doFinal(input)
        } else {
            c.init(mode, key, GCMParameterSpec(128, input.copyOfRange(0, 12)))
            c.doFinal(input, 12, input.size - 12)
        }
    }
}

/** Opens web pages and email in the PC's default apps. */
object Open {
    fun url(url: String): Boolean = runCatching {
        Desktop.getDesktop().browse(URI(url.trim()))
        true
    }.getOrDefault(false)

    fun email(address: String): Boolean = runCatching {
        Desktop.getDesktop().mail(URI("mailto:" + address.trim().replace(" ", "%20")))
        true
    }.getOrDefault(false)

    fun folder(dir: File): Boolean = runCatching {
        Desktop.getDesktop().open(dir)
        true
    }.getOrDefault(false)
}

/**
 * Copies text. Passwords are cleared from the clipboard after [CLEAR_AFTER_MS]
 * (if nothing else was copied since).
 */
object Clipboard {
    const val CLEAR_AFTER_MS = 30_000L
    const val LOGIN_CLEAR_AFTER_MS = 60_000L
    private val timer = Timer("clipboard", true)
    private var pending: TimerTask? = null

    private val cb get() = Toolkit.getDefaultToolkit().systemClipboard

    @Synchronized
    fun copy(text: String, sensitive: Boolean = false) {
        pending?.cancel()
        pending = null
        cb.setContents(StringSelection(text), null)
        if (sensitive) clearLater(text, CLEAR_AFTER_MS)
    }

    /**
     * For logging in to IRIS: copies the password, then the username, so Windows
     * clipboard history (Windows key + V) shows the username first and the password second.
     */
    fun copyLogin(username: String, password: String) {
        if (password.isNotEmpty()) {
            copy(password)
            Thread.sleep(700)
        }
        copy(username)
        clearLater(username, LOGIN_CLEAR_AFTER_MS)
    }

    @Synchronized
    private fun clearLater(text: String, ms: Long) {
        pending?.cancel()
        pending = object : TimerTask() {
            override fun run() {
                val now = runCatching { cb.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
                if (now == null || now == text) runCatching { cb.setContents(StringSelection(""), null) }
            }
        }.also { timer.schedule(it, ms) }
    }
}
