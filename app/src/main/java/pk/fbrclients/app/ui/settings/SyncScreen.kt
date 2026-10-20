package pk.fbrclients.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pk.fbrclients.app.FbrApp
import pk.fbrclients.app.data.sync.SyncFile
import pk.fbrclients.app.sync.SyncManager
import pk.fbrclients.app.ui.clients.FbrTopBar
import pk.fbrclients.app.ui.components.MessageDialog
import java.text.DateFormat
import java.util.Date

/** One line for the Settings list. */
fun syncSummary(s: SyncManager.Status): String = when (s) {
    SyncManager.Status.Off -> "Off · keep the phone and PC the same through Google Drive"
    SyncManager.Status.Running -> "Syncing…"
    is SyncManager.Status.Done -> if (s.at == 0L) s.message else "On · last sync ${time(s.at)}"
    is SyncManager.Status.Failed -> "Problem: ${s.message}"
}

private fun time(at: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))

@Composable
fun SyncScreen(app: FbrApp, onBack: () -> Unit) {
    val sync = app.sync
    val status by sync.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // A file chosen in the picker, waiting for its password.
    var picked by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    // Re-read after setup / turning off.
    var setUp by remember { mutableStateOf(sync.isSetUp) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) picked = uri to true
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = uri to false
    }

    Scaffold(topBar = { FbrTopBar("Sync with PC", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!setUp) {
                Text(
                    "Keep this phone and the FBR Clients PC app the same. Both use one sync file in your Google Drive. " +
                        "The file is locked with a sync password, so Google only stores scrambled data.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Hint(
                    "• First time: tap “Create sync file”, choose Google Drive and a folder, and set a sync password.\n" +
                        "• Then on the PC: FBR Clients → Settings → Sync → choose the same file in your Google Drive folder, with the same password.\n" +
                        "• If the PC made the file first, tap “Use existing sync file” here instead.",
                )
                Button(onClick = { create.launch(SyncFile.DEFAULT_NAME) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Create sync file")
                }
                OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use existing sync file")
                }
            } else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sync is on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("File: " + remember(setUp) { sync.fileName() })
                        when (val s = status) {
                            SyncManager.Status.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Syncing…")
                            }
                            is SyncManager.Status.Done -> Text(if (s.at == 0L) s.message else "Last sync: ${time(s.at)} · ${s.message}")
                            is SyncManager.Status.Failed -> Text("${time(s.at)}: ${s.message}", color = MaterialTheme.colorScheme.error)
                            SyncManager.Status.Off -> Unit
                        }
                    }
                }
                Hint("The app syncs when you open it, every 5 minutes while it is open, and when you close it. When the same client was changed on both, the newest change is kept.")
                Button(
                    onClick = { scope.launch { sync.syncNow() } },
                    enabled = status != SyncManager.Status.Running,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Sync now") }
                if (status is SyncManager.Status.Failed) {
                    OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose the sync file again") }
                    OutlinedButton(onClick = { confirmReplace = true }, modifier = Modifier.fillMaxWidth()) { Text("Replace the sync file with this phone's data") }
                }
                TextButton(
                    onClick = { confirmOff = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Turn off sync on this phone") }
            }
        }
    }

    picked?.let { (uri, isNew) ->
        PasswordDialog(
            title = if (isNew) "Choose a sync password" else "Sync password",
            text = if (isNew) "Use the same password on the PC. It is saved on this phone (locked by the phone), so you only type it once."
            else "Enter the sync password used for this file.",
            confirmLabel = if (isNew) "Start sync" else "Link file",
            needConfirm = isNew,
            onDismiss = { picked = null },
            onConfirm = { pw ->
                picked = null
                busy = "Linking the sync file…"
                scope.launch {
                    val r = sync.setUp(uri, pw, newFile = isNew)
                    busy = null
                    setUp = sync.isSetUp
                    if (r is SyncManager.SetupResult.Error) message = "Couldn't link the file" to r.message
                }
            },
        )
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Turn off sync?") },
            text = { Text("Your data stays on this phone. The sync file in Google Drive is not deleted, and the PC keeps its data.") },
            confirmButton = { TextButton(onClick = { confirmOff = false; sync.turnOff(); setUp = false }) { Text("Turn off") } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } },
        )
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace the sync file?") },
            text = { Text("Only if the file can't be read. The sync file gets this phone's data. Changes made only on the PC since its last sync come back when the PC syncs next, if the PC uses this password.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    scope.launch { sync.syncNow(replaceFile = true) }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }

    busy?.let { BusyDialog(it) }
    message?.let { (t, m) -> MessageDialog(t, m, onDismiss = { message = null }) }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
