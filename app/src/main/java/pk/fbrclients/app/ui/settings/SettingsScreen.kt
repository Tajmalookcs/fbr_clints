package pk.fbrclients.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.fbrclients.app.BuildConfig
import pk.fbrclients.app.FbrApp
import pk.fbrclients.app.data.BackupFile
import pk.fbrclients.app.data.BackupJson
import pk.fbrclients.app.ui.clients.FbrTopBar
import pk.fbrclients.app.ui.components.Dropdown
import pk.fbrclients.app.ui.components.MessageDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private val AUTO_LOCK_CHOICES = listOf(15, 30, 60, 120, 300, 600)
private fun autoLockLabel(s: Int) = if (s < 60) "$s seconds" else if (s == 60) "1 minute" else "${s / 60} minutes"

@Composable
fun SettingsScreen(
    app: FbrApp,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onCategories: () -> Unit,
    onImport: () -> Unit,
    onDiscos: () -> Unit,
    onSync: () -> Unit,
    onRestored: () -> Unit,
) {
    val ctx = LocalContext.current
    val pinStore = app.pinStore
    val scope = rememberCoroutineScope()

    var biometric by remember { mutableStateOf(pinStore.biometricEnabled) }
    val canBiometric = remember {
        BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var autoLock by remember { mutableIntStateOf(pinStore.autoLockSeconds) }
    var lastBackup by remember { mutableLongStateOf(pinStore.lastBackupAt) }

    // Backup / restore state. Passwords are kept in memory only.
    var askBackupPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = backupPassword
        backupPassword = null
        if (uri == null || pw == null) {
            pw?.fill('\u0000')
            return@rememberLauncherForActivityResult
        }
        busy = "Encrypting backup…"
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { writeBackup(ctx, app, uri, pw) }
            }
            pw.fill('\u0000')
            busy = null
            result.onSuccess { count ->
                val now = System.currentTimeMillis()
                pinStore.lastBackupAt = now
                lastBackup = now
                message = "Backup saved" to "$count clients saved in the encrypted file. Keep the file and its password somewhere safe — the app can't recover a lost password."
            }.onFailure {
                message = "Backup failed" to (it.message ?: "Could not write the file.")
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    Scaffold(topBar = { FbrTopBar("Settings", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Section("Security")
            SettingRow("Change PIN", onClick = onChangePin)
            if (canBiometric) {
                Row(
                    Modifier.fillMaxWidth().clickable { biometric = !biometric; pinStore.biometricEnabled = biometric }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Unlock with fingerprint", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = biometric, onCheckedChange = { biometric = it; pinStore.biometricEnabled = it })
                }
            }
            Dropdown(
                label = "Auto-lock after the app is closed for",
                options = AUTO_LOCK_CHOICES,
                selected = autoLock,
                optionLabel = ::autoLockLabel,
                onSelect = { autoLock = it; pinStore.autoLockSeconds = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Wealth Statement")
            SettingRow("Categories and property sub-types", "Add missing IRIS codes, categories and sub-types", onClick = onCategories)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Clients")
            SettingRow("Electricity companies", "Links for checking bills and tax certificates", onClick = onDiscos)
            SettingRow("Import clients from Excel", "Usernames (CNIC / NTN), passwords and details from an Excel .xlsx file", onClick = onImport)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("PC")
            val syncStatus by app.sync.status.collectAsStateWithLifecycle()
            SettingRow("Sync with PC", syncSummary(syncStatus), onClick = onSync)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Backup")
            SettingRow(
                "Back up now",
                if (lastBackup == 0L) "Never backed up" else "Last backup: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastBackup)),
                onClick = { askBackupPassword = true },
            )
            SettingRow("Restore from backup", "Replaces all data on this phone", onClick = { openFile.launch(arrayOf("*/*")) })
            Text(
                "Backups are encrypted .fbrbak files with their own password. Save them to Google Drive, a USB drive or another phone. Use Restore on any phone with this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                "FBR Clients ${BuildConfig.VERSION_NAME} · works offline; sync with your PC is optional",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askBackupPassword) {
        PasswordDialog(
            title = "Backup password",
            text = "Choose a password for this backup file. You'll need it to restore. It is not saved anywhere.",
            confirmLabel = "Choose where to save",
            needConfirm = true,
            onDismiss = { askBackupPassword = false },
            onConfirm = { pw ->
                askBackupPassword = false
                backupPassword = pw
                createFile.launch("FBR-Clients-${LocalDate.now()}.fbrbak")
            },
        )
    }

    restoreUri?.let { uri ->
        PasswordDialog(
            title = "Restore backup",
            text = "Everything on this phone (clients, statements, categories) will be replaced by the backup." +
                (if (app.sync.isSetUp) " Sync is on, so the data on your PC will be replaced too." else "") +
                " Enter the backup's password.",
            confirmLabel = "Replace and restore",
            needConfirm = false,
            onDismiss = { restoreUri = null },
            onConfirm = { pw ->
                restoreUri = null
                busy = "Restoring…"
                scope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { restoreBackup(ctx, app, uri, pw) } }
                    pw.fill('\u0000')
                    busy = null
                    result.onSuccess { count ->
                        message = "Restored" to "$count clients restored from the backup."
                        app.sync.requestSync()
                        onRestored()
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

/** Returns the number of clients saved. */
private suspend fun writeBackup(ctx: Context, app: FbrApp, uri: Uri, password: CharArray): Int {
    val data = app.database.backup().exportAll()
    val json = BackupJson.write(data, LocalDate.now().toString())
    val file = BackupFile.encrypt(json, password)
    json.fill(0)
    ctx.contentResolver.openOutputStream(uri, "wt").use { out ->
        requireNotNull(out) { "Could not open the file" }.write(file)
    }
    return data.clients.size
}

/** Returns the number of clients restored. Nothing changes if anything fails. */
private suspend fun restoreBackup(ctx: Context, app: FbrApp, uri: Uri, password: CharArray): Int {
    val bytes = ctx.contentResolver.openInputStream(uri).use { requireNotNull(it).readBytes() }
    val data = BackupJson.read(BackupFile.decrypt(bytes, password))
    app.database.backup().replaceAll(data)
    return data.clients.size
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PasswordDialog(
    title: String,
    text: String,
    confirmLabel: String,
    needConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val tooShort = needConfirm && pw.length < BackupFile.MIN_PASSWORD
    val mismatch = needConfirm && pw2.isNotEmpty() && pw != pw2
    val ok = pw.isNotEmpty() && !tooShort && (!needConfirm || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                val t = if (show) VisualTransformation.None else PasswordVisualTransformation()
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it },
                    label = { Text("Password") },
                    supportingText = if (needConfirm) ({ Text("At least ${BackupFile.MIN_PASSWORD} characters") }) else null,
                    visualTransformation = t, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
                )
                if (needConfirm) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it },
                        label = { Text("Type it again") },
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text("Passwords don't match") }) else null,
                        visualTransformation = t, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw.toCharArray()) }, enabled = ok) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun BusyDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.size(16.dp))
                Text(text)
            }
        },
    )
}
