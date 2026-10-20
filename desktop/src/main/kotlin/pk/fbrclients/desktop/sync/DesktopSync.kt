package pk.fbrclients.desktop.sync

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
import pk.fbrclients.app.data.BackupFile
import pk.fbrclients.app.data.sync.SyncEngine
import pk.fbrclients.app.data.sync.SyncFile
import pk.fbrclients.app.data.sync.SyncSet
import pk.fbrclients.desktop.platform.writeAtomically
import pk.fbrclients.desktop.store.DataStore
import pk.fbrclients.desktop.store.Settings
import java.io.File

/**
 * Sync with the phone through the sync file in the Google Drive folder
 * (Google Drive for Desktop keeps that folder in step with Drive).
 * Copies Google Drive may make when both write at once ("FBR-Clients (1).fbrsync")
 * are read too, so nothing is lost.
 */
class DesktopSync(private val store: DataStore, private val settings: Settings) {

    sealed interface Status {
        data object Off : Status
        data object Running : Status
        data class Done(val at: Long, val message: String) : Status
        data class Failed(val at: Long, val message: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var periodic: Job? = null

    private val _status = MutableStateFlow(initial())
    val status: StateFlow<Status> = _status.asStateFlow()

    private fun initial(): Status = when {
        !settings.syncSetUp -> Status.Off
        settings.syncFailed -> Status.Failed(settings.syncAt, settings.syncMessage ?: "")
        else -> Status.Done(settings.syncAt, settings.syncMessage ?: "Not synced yet")
    }

    sealed interface SetupResult {
        data object Ok : SetupResult
        data class Error(val message: String) : SetupResult
    }

    /** Links [file] (existing or new) with [password]. */
    suspend fun setUp(file: File, password: CharArray): SetupResult = withContext(Dispatchers.IO) {
        if (password.size < MIN_PASSWORD) return@withContext SetupResult.Error("The sync password needs at least $MIN_PASSWORD characters.")
        if (file.exists() && file.length() > 0) {
            try {
                SyncFile.read(file.readBytes(), password)
            } catch (_: BackupFile.WrongPasswordException) {
                return@withContext SetupResult.Error("Wrong sync password for this file (or the file is damaged).")
            } catch (_: Exception) {
                return@withContext SetupResult.Error("This is not an FBR Clients sync file. Choose the .fbrsync file.")
            }
        }
        settings.setSync(file, password)
        password.fill('\u0000')
        syncNow()
        SetupResult.Ok
    }

    fun turnOff() {
        settings.clearSync()
        _status.value = Status.Off
    }

    /** Starts the automatic sync (after unlocking). */
    fun start() {
        if (periodic?.isActive == true) return
        periodic = scope.launch {
            syncNow()
            while (isActive) {
                delay(PERIOD_MS)
                syncNow()
            }
        }
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    /** The other files in the folder that Google Drive made from the sync file. */
    private fun copiesOf(f: File): List<File> {
        val base = f.nameWithoutExtension
        val dir = f.absoluteFile.parentFile ?: return emptyList()
        val pattern = Regex("^" + Regex.escape(base) + """\s*\(\d+\)\.""" + Regex.escape(f.extension) + "$", RegexOption.IGNORE_CASE)
        return dir.listFiles { x -> x.isFile && pattern.matches(x.name) }.orEmpty().toList()
    }

    suspend fun syncNow(replaceFile: Boolean = false) = withContext(Dispatchers.IO) {
        if (!settings.syncSetUp) return@withContext
        mutex.withLock {
            val f = settings.syncFile ?: return@withLock
            val pw = settings.syncPassword() ?: return@withLock
            _status.value = Status.Running
            try {
                if (f.absoluteFile.parentFile?.exists() != true) {
                    failed("The sync folder can't be found: ${f.parent}. Is Google Drive running?")
                    return@withLock
                }
                val remotes = mutableListOf<SyncSet>()
                var unreadable = 0
                if (!replaceFile) {
                    if (f.exists() && f.length() > 0) remotes += SyncFile.read(f.readBytes(), pw)
                    for (copy in copiesOf(f)) {
                        runCatching { SyncFile.read(copy.readBytes(), pw) }.onSuccess { remotes += it }.onFailure { unreadable++ }
                    }
                }
                val result = store.syncWith { local, deleted ->
                    val r = SyncEngine.run(local, deleted, remotes)
                    Triple(r.newLocal, r.merged.deleted, r)
                }
                if (result.writeRemote || replaceFile) {
                    writeAtomically(f, SyncFile.write(result.merged, pw, "pc"))
                }
                val n = result.diff.size
                done(if (n == 0) "Up to date" else "$n change${if (n == 1) "" else "s"} from the phone")
            } catch (_: BackupFile.WrongPasswordException) {
                failed("Couldn't read the sync file: the sync password is wrong, or the file is damaged.")
            } catch (_: SyncFile.NotASyncFileException) {
                failed("The linked file is not an FBR Clients sync file.")
            } catch (e: Exception) {
                failed("Sync failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                pw.fill('\u0000')
            }
        }
    }

    private fun done(message: String) {
        val now = System.currentTimeMillis()
        settings.saveSyncStatus(now, message, false)
        _status.value = Status.Done(now, message)
    }

    private fun failed(message: String) {
        val now = System.currentTimeMillis()
        settings.saveSyncStatus(now, message, true)
        _status.value = Status.Failed(now, message)
    }

    companion object {
        const val MIN_PASSWORD = 8
        private const val PERIOD_MS = 5 * 60 * 1000L
    }
}
