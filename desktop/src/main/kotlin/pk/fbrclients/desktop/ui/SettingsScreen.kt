package pk.fbrclients.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.fbrclients.app.data.BackupFile
import pk.fbrclients.app.data.BackupJson
import pk.fbrclients.app.security.PinHasher
import pk.fbrclients.app.ui.components.Dropdown
import pk.fbrclients.app.ui.components.MessageDialog
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.platform.AppPaths
import pk.fbrclients.desktop.platform.Open
import pk.fbrclients.desktop.store.Settings
import java.io.File
import java.time.LocalDate

private enum class Page(val title: String) { Main("Settings"), Sync("Sync with phone"), Categories("Categories"), Discos("Electricity companies"), Import("Import clients from Excel") }

private val AUTO_LOCK = listOf(1, 5, 10, 15, 30, 60)

/** The app version from the Windows installer (or "dev" when run from the code). */
val appVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

@Composable
fun SettingsScreen(state: AppState) {
    var page by remember { mutableStateOf(Page.Main) }
    Column(Modifier.fillMaxSize()) {
        PageBar(page.title, leading = {
            if (page != Page.Main) IconButton(onClick = { page = Page.Main }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
        Box(Modifier.weight(1f)) {
            when (page) {
                Page.Main -> MainSettings(state) { page = it }
                Page.Sync -> SyncPage(state)
                Page.Categories -> CategoriesPage(state)
                Page.Discos -> DiscosPage(state)
                Page.Import -> ImportPage(state, onDone = { page = Page.Main })
            }
        }
    }
}

@Composable
private fun MainSettings(state: AppState, open: (Page) -> Unit) {
    val scope = rememberCoroutineScope()
    var autoLock by remember { mutableStateOf(state.settings.autoLockMinutes) }
    var changePin by remember { mutableStateOf(false) }
    var askBackupPassword by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastBackup by remember { mutableStateOf(state.settings.lastBackupAt) }
    val syncStatus by state.sync.status.collectAsState()

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 8.dp).widthIn(max = 800.dp)) {
            Section("Security")
            Row_("Change PIN") { changePin = true }
            Dropdown(
                label = "Lock the app after this long without use",
                options = AUTO_LOCK, selected = autoLock,
                optionLabel = { if (it == 1) "1 minute" else "$it minutes" },
                onSelect = { autoLock = it; state.settings.autoLockMinutes = it },
                modifier = Modifier.width(420.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Phone")
            Row_("Sync with phone", syncSummary(syncStatus)) { open(Page.Sync) }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Wealth Statement")
            Row_("Categories and property sub-types", "Add missing IRIS codes, categories and sub-types") { open(Page.Categories) }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Clients")
            Row_("Electricity companies", "Links for checking bills and tax certificates") { open(Page.Discos) }
            Row_("Import clients from Excel", "Usernames (CNIC / NTN), passwords and details from an Excel .xlsx file") { open(Page.Import) }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Backup")
            Row_("Back up now", if (lastBackup == 0L) "Never backed up on this PC" else "Last backup: " + dateTime(lastBackup)) { askBackupPassword = true }
            Row_("Restore from backup", "Replaces all data on this PC" + if (state.settings.syncSetUp) " (and, through sync, on the phone)" else "") {
                restoreFile = FileDialogs.open(state.window, "Choose a backup file", "fbrbak")
            }
            Hint("Backups are encrypted .fbrbak files with their own password — the same files as on the phone.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row_("FBR Clients for Windows $appVersion", "Data folder: ${AppPaths.dir.absolutePath} (encrypted)") { Open.folder(AppPaths.dir) }
            Spacer(Modifier.height(24.dp))
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }

    if (changePin) ChangePinDialog(state.settings, onDone = { changePin = false; if (it) state.toast("PIN changed") })

    if (askBackupPassword) {
        PasswordDialog(
            title = "Backup password",
            text = "Choose a password for this backup file. You'll need it to restore. It is not saved anywhere.",
            confirmLabel = "Choose where to save",
            needConfirm = true,
            minLength = BackupFile.MIN_PASSWORD,
            onDismiss = { askBackupPassword = false },
            onConfirm = { pw ->
                askBackupPassword = false
                val f = FileDialogs.save(state.window, "Save backup", "FBR-Clients-${LocalDate.now()}.fbrbak", FileDialogs.googleDrive())
                if (f == null) { pw.fill('\u0000'); return@PasswordDialog }
                busy = "Encrypting backup…"
                scope.launch {
                    val r = runCatching {
                        withContext(Dispatchers.IO) {
                            val d = state.store.data
                            f.writeBytes(BackupFile.encrypt(BackupJson.write(d, LocalDate.now().toString()), pw))
                            d.clients.size
                        }
                    }
                    pw.fill('\u0000')
                    busy = null
                    r.onSuccess { n ->
                        val now = System.currentTimeMillis()
                        state.settings.lastBackupAt = now
                        lastBackup = now
                        message = "Backup saved" to "$n clients saved in ${f.name}. Keep the file and its password somewhere safe — the app can't recover a lost password."
                    }.onFailure { message = "Backup failed" to (it.message ?: "Could not write the file.") }
                }
            },
        )
    }

    restoreFile?.let { f ->
        PasswordDialog(
            title = "Restore backup",
            text = "Everything on this PC (clients, statements, categories) will be replaced by the backup " + f.name + "." +
                (if (state.settings.syncSetUp) " Sync is on, so the data on your phone will be replaced too." else "") + " Enter the backup's password.",
            confirmLabel = "Replace and restore",
            needConfirm = false,
            onDismiss = { restoreFile = null },
            onConfirm = { pw ->
                restoreFile = null
                busy = "Restoring…"
                scope.launch {
                    val r = runCatching {
                        withContext(Dispatchers.IO) {
                            val d = BackupJson.read(BackupFile.decrypt(f.readBytes(), pw))
                            state.store.replaceAll(d)
                            d.clients.size
                        }
                    }
                    pw.fill('\u0000')
                    busy = null
                    r.onSuccess { n ->
                        state.selectedClientId = null
                        state.sync.requestSync()
                        message = "Restored" to "$n clients restored from the backup."
                    }.onFailure {
                        message = "Restore failed" to when (it) {
                            is BackupFile.WrongPasswordException, is BackupFile.NotABackupException -> it.message!!
                            else -> "Could not read the backup file. Nothing was changed."
                        }
                    }
                }
            },
        )
    }

    busy?.let { BusyDialog(it) }
    message?.let { (t, m) -> MessageDialog(t, m, onDismiss = { message = null }) }
}

@Composable
private fun ChangePinDialog(settings: Settings, onDone: (Boolean) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun digits(v: String) = v.filter { it.isDigit() }.take(PinHasher.MAX_LENGTH)
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text("Change PIN") },
        text = {
            Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current, { current = digits(it); error = null }, label = { Text("Current PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(new1, { new1 = digits(it); error = null }, label = { Text("New PIN (${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(new2, { new2 = digits(it); error = null }, label = { Text("New PIN again") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    isError = error != null, supportingText = error?.let { { Text(it) } })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    !PinHasher.isValidPin(new1) -> "Use ${PinHasher.MIN_LENGTH} to ${PinHasher.MAX_LENGTH} digits"
                    new1 != new2 -> "The new PINs are different"
                    else -> when (val r = settings.checkPin(current)) {
                        Settings.PinResult.Ok -> null
                        is Settings.PinResult.Paused -> "Too many wrong PINs. Wait a moment."
                        is Settings.PinResult.Wrong -> "The current PIN is wrong"
                    }
                }
                if (error == null) { settings.setPin(new1); onDone(true) }
            }) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }) { Text("Cancel") } },
    )
}

@Composable
internal fun Section(title: String) {
    SectionTitle(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
internal fun Row_(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
