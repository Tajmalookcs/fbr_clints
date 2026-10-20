package pk.fbrclients.app.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pk.fbrclients.app.FbrApp
import pk.fbrclients.app.data.BackupFile
import pk.fbrclients.app.data.MigrationSql
import pk.fbrclients.app.data.sync.SyncEngine
import pk.fbrclients.app.data.sync.SyncFile
import pk.fbrclients.app.data.sync.SyncSet
import pk.fbrclients.app.security.KeystoreCipher
import java.io.FileNotFoundException

/**
 * Sync with the PC through one encrypted file in Google Drive (docs/DESIGN.md §9).
 * The phone opens the file through Android's file picker, so no Google account
 * setup is needed in the app. Runs after unlocking, every 5 minutes while the app
 * is open, and when it is closed.
 */
class SyncManager(private val app: FbrApp) : DefaultLifecycleObserver {

    sealed interface Status {
        data object Off : Status
        data object Running : Status
        data class Done(val at: Long, val changesHere: Int, val message: String) : Status
        data class Failed(val at: Long, val message: String) : Status
    }

    private val prefs = app.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("fbr_sync_key")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var periodic: Job? = null

    /** Set after the first unlock; nothing syncs before that. */
    @Volatile var enabledAfterUnlock = false
        private set

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        _status.value = if (!isSetUp) Status.Off else savedStatus()
    }

    val isSetUp: Boolean get() = prefs.contains(KEY_URI) && prefs.contains(KEY_PASSWORD)
    val fileUri: Uri? get() = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    fun fileName(): String = fileUri?.let { uri ->
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    } ?: SyncFile.DEFAULT_NAME

    private fun password(): CharArray? = prefs.getString(KEY_PASSWORD, null)?.let {
        String(cipher.decrypt(Base64.decode(it, Base64.NO_WRAP)), Charsets.UTF_8).toCharArray()
    }

    // ---------------- Setup ----------------

    sealed interface SetupResult {
        data object Ok : SetupResult
        data class Error(val message: String) : SetupResult
    }

    /**
     * Links the chosen file. For an existing file the password is checked first.
     * [newFile]: the file was just created (it is empty).
     */
    suspend fun setUp(uri: Uri, password: CharArray, newFile: Boolean): SetupResult = withContext(Dispatchers.IO) {
        if (password.size < MIN_PASSWORD) return@withContext SetupResult.Error("The sync password needs at least $MIN_PASSWORD characters.")
        if (!newFile) {
            val bytes = runCatching { readBytes(uri) }.getOrElse { return@withContext SetupResult.Error("Couldn't open the file.") }
            if (bytes.isNotEmpty()) {
                try {
                    SyncFile.read(bytes, password)
                } catch (_: BackupFile.WrongPasswordException) {
                    return@withContext SetupResult.Error("Wrong sync password for this file (or the file is damaged).")
                } catch (_: SyncFile.NotASyncFileException) {
                    return@withContext SetupResult.Error("This is not an FBR Clients sync file. Choose the .fbrsync file.")
                } catch (_: Exception) {
                    return@withContext SetupResult.Error("This is not an FBR Clients sync file. Choose the .fbrsync file.")
                }
            }
        }
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.onFailure { return@withContext SetupResult.Error("The phone didn't allow the app to keep using this file. Try choosing it again.") }
        prefs.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_PASSWORD, Base64.encodeToString(cipher.encrypt(String(password).toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .remove(KEY_LAST_AT).remove(KEY_LAST_MSG).remove(KEY_LAST_FAILED)
            .commit()
        password.fill('\u0000')
        syncNow()
        SetupResult.Ok
    }

    fun turnOff() {
        fileUri?.let { uri ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        prefs.edit().clear().apply()
        _status.value = Status.Off
    }

    // ---------------- Running ----------------

    fun onUnlocked() {
        if (enabledAfterUnlock) return
        enabledAfterUnlock = true
        startPeriodic()
        requestSync()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!enabledAfterUnlock) return
        startPeriodic()
        requestSync()
    }

    override fun onStop(owner: LifecycleOwner) {
        periodic?.cancel()
        periodic = null
        if (enabledAfterUnlock) requestSync()
    }

    private fun startPeriodic() {
        if (periodic?.isActive == true) return
        periodic = scope.launch {
            while (isActive) {
                delay(PERIOD_MS)
                syncNow()
            }
        }
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    /**
     * One sync run. [replaceFile]: write this phone's data over a sync file that can't be
     * read (only after the owner confirms).
     */
    suspend fun syncNow(replaceFile: Boolean = false) = withContext(Dispatchers.IO) {
        if (!isSetUp) return@withContext
        mutex.withLock {
            val uri = fileUri ?: return@withLock
            val pw = password() ?: return@withLock
            _status.value = Status.Running
            try {
                val remote: SyncSet? = if (replaceFile) null else {
                    val bytes = readBytes(uri)
                    if (bytes.isEmpty()) null else SyncFile.read(bytes, pw)
                }
                val db = app.database
                val dao = db.sync()
                val result = db.withTransaction {
                    val r = SyncEngine.run(dao.exportAll(), dao.tombstones(), listOfNotNull(remote))
                    val w = db.openHelper.writableDatabase
                    w.execSQL(MigrationSql.APPLYING_ON)
                    try {
                        dao.apply(r.diff, r.merged.deleted)
                    } finally {
                        w.execSQL(MigrationSql.APPLYING_OFF)
                    }
                    r
                }
                if (result.writeRemote || replaceFile) writeBytes(uri, SyncFile.write(result.merged, pw, "phone"))
                val n = result.diff.size
                done(if (n == 0) "Up to date" else "$n change${if (n == 1) "" else "s"} from the PC", n)
            } catch (_: BackupFile.WrongPasswordException) {
                failed("Couldn't read the sync file: the sync password is wrong, or the file is damaged.")
            } catch (_: SyncFile.NotASyncFileException) {
                failed("The linked file is not an FBR Clients sync file.")
            } catch (_: FileNotFoundException) {
                failed("The sync file can't be found. Was it moved or deleted in Google Drive? Choose it again.")
            } catch (_: SecurityException) {
                failed("The app lost access to the sync file. Choose it again.")
            } catch (e: Exception) {
                failed("Sync failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                pw.fill('\u0000')
            }
        }
    }

    private fun readBytes(uri: Uri): ByteArray =
        app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw FileNotFoundException()

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        // "wt" (truncate) is not supported by every provider; "w" is.
        val out = runCatching { app.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: app.contentResolver.openOutputStream(uri, "w")
            ?: throw FileNotFoundException()
        out.use { it.write(bytes) }
    }

    private fun done(message: String, changes: Int) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_AT, now).putString(KEY_LAST_MSG, message).putBoolean(KEY_LAST_FAILED, false).apply()
        _status.value = Status.Done(now, changes, message)
    }

    private fun failed(message: String) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_AT, now).putString(KEY_LAST_MSG, message).putBoolean(KEY_LAST_FAILED, true).apply()
        _status.value = Status.Failed(now, message)
    }

    private fun savedStatus(): Status {
        val at = prefs.getLong(KEY_LAST_AT, 0L)
        val msg = prefs.getString(KEY_LAST_MSG, null) ?: return Status.Done(0, 0, "Not synced yet")
        return if (prefs.getBoolean(KEY_LAST_FAILED, false)) Status.Failed(at, msg) else Status.Done(at, 0, msg)
    }

    companion object {
        const val MIN_PASSWORD = 8
        private const val PERIOD_MS = 5 * 60 * 1000L
        private const val KEY_URI = "file_uri"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_AT = "last_at"
        private const val KEY_LAST_MSG = "last_message"
        private const val KEY_LAST_FAILED = "last_failed"
    }
}
