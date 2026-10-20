package pk.fbrclients.desktop

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pk.fbrclients.desktop.store.DataStore
import pk.fbrclients.desktop.store.Settings
import pk.fbrclients.desktop.sync.DesktopSync

/** Pages of the main window. */
sealed interface Screen {
    data object Clients : Screen
    data class Statement(val id: Long) : Screen
    data object Settings : Screen
}

class AppState(val store: DataStore, val settings: Settings) {
    val sync = DesktopSync(store, settings)
    val snackbar = SnackbarHostState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var window: ComposeWindow? = null

    var hasPin by mutableStateOf(settings.hasPin)
    var locked by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    private var loaded = false

    var screen by mutableStateOf<Screen>(Screen.Clients)
    var selectedClientId by mutableStateOf<Long?>(null)
    /** null = not editing; 0 = new client. */
    var editingClientId by mutableStateOf<Long?>(null)

    @Volatile var lastActivity = System.currentTimeMillis()

    fun touch() {
        lastActivity = System.currentTimeMillis()
    }

    fun unlocked() {
        if (!loaded) {
            try {
                store.load()
                loaded = true
            } catch (e: Exception) {
                loadError = "Couldn't open the app's data on this PC (${e.message ?: e.javaClass.simpleName}). " +
                    "It can only be opened by the same Windows user. You can restore a backup in Settings."
                return
            }
        }
        touch()
        locked = false
        sync.start()
    }

    fun lock() {
        locked = true
        editingClientId = null
    }

    /** Short message at the bottom of the window. */
    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    val isLoaded get() = loaded

    /** After [loadError]: put the unreadable file aside and start with empty data. */
    fun startFresh() {
        val f = pk.fbrclients.desktop.platform.AppPaths.data
        if (f.exists()) f.renameTo(java.io.File(f.parentFile, f.name + ".unreadable-" + System.currentTimeMillis()))
        loadError = null
        unlocked()
    }
}
