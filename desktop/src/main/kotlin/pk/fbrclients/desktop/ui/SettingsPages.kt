package pk.fbrclients.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.fbrclients.app.data.AssetGroup
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.Disco
import pk.fbrclients.app.data.PropertySubType
import pk.fbrclients.app.data.importer.ClientImport
import pk.fbrclients.app.data.importer.ImportRules
import pk.fbrclients.app.data.importer.SheetReader
import pk.fbrclients.app.data.sync.SyncFile
import pk.fbrclients.app.ui.components.ConfirmDeleteDialog
import pk.fbrclients.app.ui.components.MessageDialog
import pk.fbrclients.app.ui.components.TextInputDialog
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.platform.Open
import pk.fbrclients.desktop.sync.DesktopSync
import java.io.File

fun syncSummary(s: DesktopSync.Status): String = when (s) {
    DesktopSync.Status.Off -> "Off · keep this PC and the phone the same through Google Drive"
    DesktopSync.Status.Running -> "Syncing…"
    is DesktopSync.Status.Done -> if (s.at == 0L) s.message else "On · last sync ${dateTime(s.at)}"
    is DesktopSync.Status.Failed -> "Problem: ${s.message}"
}

// ---------------- Sync ----------------

@Composable
fun SyncPage(state: AppState) {
    val sync = state.sync
    val status by sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    var setUp by remember { mutableStateOf(state.settings.syncSetUp) }
    var picked by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!setUp) {
            Text(
                "Keep this PC and the FBR Clients phone app the same. Both use one sync file in your Google Drive. " +
                    "The file is locked with a sync password, so Google only stores scrambled data.",
                style = MaterialTheme.typography.bodyLarge,
            )
            val drive = remember { FileDialogs.googleDrive() }
            Hint(
                "• You need Google Drive for Desktop on this PC, signed in with the same Google account as the phone" +
                    (if (drive != null) " (found: ${drive.absolutePath})." else ". It wasn't found yet — install it from google.com/drive/download, then come back.") + "\n" +
                    "• If the phone already made the sync file: “Use existing sync file”, then pick it in your Google Drive folder and enter the same sync password.\n" +
                    "• Otherwise: “Create sync file” here, then on the phone: Settings → Sync with PC → Use existing sync file.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    FileDialogs.open(state.window, "Choose the sync file (.fbrsync)", SyncFile.EXTENSION, drive)?.let { picked = it to false }
                }, modifier = Modifier.height(44.dp)) { Text("Use existing sync file") }
                OutlinedButton(onClick = {
                    FileDialogs.save(state.window, "Create the sync file in your Google Drive folder", SyncFile.DEFAULT_NAME, drive)?.let { picked = it to true }
                }, modifier = Modifier.height(44.dp)) { Text("Create sync file") }
            }
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sync is on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("File: " + (state.settings.syncFile?.absolutePath ?: ""))
                    when (val s = status) {
                        DesktopSync.Status.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)); Text("Syncing…")
                        }
                        is DesktopSync.Status.Done -> Text(if (s.at == 0L) s.message else "Last sync: ${dateTime(s.at)} · ${s.message}")
                        is DesktopSync.Status.Failed -> Text("${dateTime(s.at)}: ${s.message}", color = MaterialTheme.colorScheme.error)
                        DesktopSync.Status.Off -> Unit
                    }
                }
            }
            Hint("The app syncs when you unlock it, every 5 minutes while it is open, and when you close it. When the same client was changed on both, the newest change is kept.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { scope.launch { sync.syncNow() } }, enabled = status != DesktopSync.Status.Running, modifier = Modifier.height(44.dp)) { Text("Sync now") }
                OutlinedButton(onClick = { state.settings.syncFile?.parentFile?.let { Open.folder(it) } }, modifier = Modifier.height(44.dp)) { Text("Open the folder") }
            }
            if (status is DesktopSync.Status.Failed) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        FileDialogs.open(state.window, "Choose the sync file (.fbrsync)", SyncFile.EXTENSION, state.settings.syncFile?.parentFile)?.let { picked = it to false }
                    }) { Text("Choose the sync file again") }
                    OutlinedButton(onClick = { confirmReplace = true }) { Text("Replace the sync file with this PC's data") }
                }
            }
            TextButton(onClick = { confirmOff = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Turn off sync on this PC")
            }
        }
    }

    picked?.let { (file, isNew) ->
        PasswordDialog(
            title = if (isNew) "Choose a sync password" else "Sync password",
            text = if (isNew) "Use the same password on the phone. It is saved on this PC (locked to your Windows user), so you only type it once."
            else "Enter the sync password used for ${file.name}.",
            confirmLabel = if (isNew) "Start sync" else "Link file",
            needConfirm = isNew,
            minLength = DesktopSync.MIN_PASSWORD,
            onDismiss = { picked = null },
            onConfirm = { pw ->
                picked = null
                busy = "Linking the sync file…"
                scope.launch {
                    val r = sync.setUp(file, pw)
                    busy = null
                    setUp = state.settings.syncSetUp
                    if (r is DesktopSync.SetupResult.Error) message = "Couldn't link the file" to r.message
                }
            },
        )
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Turn off sync?") },
            text = { Text("Your data stays on this PC. The sync file in Google Drive is not deleted, and the phone keeps its data.") },
            confirmButton = { TextButton(onClick = { confirmOff = false; sync.turnOff(); setUp = false }) { Text("Turn off") } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } },
        )
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace the sync file?") },
            text = { Text("Only if the file can't be read. The sync file gets this PC's data. Changes made only on the phone since its last sync come back when the phone syncs next, if it uses this password.") },
            confirmButton = { TextButton(onClick = { confirmReplace = false; scope.launch { sync.syncNow(replaceFile = true) } }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
    busy?.let { BusyDialog(it) }
    message?.let { (t, m) -> MessageDialog(t, m, onDismiss = { message = null }) }
}

// ---------------- Categories ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesPage(state: AppState) {
    val data = state.store.data
    val categories = data.categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf<AssetGroup?>(null) }
    var addingSubTypeFor by remember { mutableStateOf<Category?>(null) }
    var deletingSubType by remember { mutableStateOf<PropertySubType?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp).widthIn(max = 900.dp)) {
        Hint("Click a category to add or change its IRIS code. Codes shown as “code ?” are still unknown.", Modifier.padding(20.dp))
        for (g in AssetGroup.entries) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(g.title, Modifier.weight(1f))
                if (g != AssetGroup.IMMOVABLE) TextButton(onClick = { adding = g }) { Icon(Icons.Filled.Add, null, Modifier.padding(end = 4.dp)); Text("Add") }
            }
            val cats = categories.filter { it.group == g }
            for (c in cats) {
                Column(Modifier.fillMaxWidth().clickable(enabled = c.editable || !c.builtIn) { editing = c }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text((c.code ?: "code ?") + "  " + c.name, style = MaterialTheme.typography.bodyLarge)
                    if (c.builtIn && !c.editable) Text("Built-in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.isProperty) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            data.subTypes.filter { it.categoryId == c.id }.sortedBy { it.name.lowercase() }.forEach { s ->
                                InputChip(selected = false, onClick = { deletingSubType = s }, label = { Text(s.name) },
                                    trailingIcon = { Icon(Icons.Filled.Close, "Remove ${s.name}") })
                            }
                            TextButton(onClick = { addingSubTypeFor = c }) { Text("+ Sub-type") }
                        }
                    }
                }
            }
            if (cats.isEmpty()) Hint("None yet.", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
    }

    adding?.let { g ->
        CategoryDialog("New category", g.title, "", "", nameEditable = true, codeEditable = true, canDelete = false,
            onDismiss = { adding = null },
            onSave = { code, name ->
                adding = null
                state.store.saveCategory(Category(code = code, name = name, group = g, sortOrder = (state.store.data.categories.maxOfOrNull { it.sortOrder } ?: 0) + 10))
            },
            onDelete = {})
    }
    editing?.let { c ->
        CategoryDialog("Edit category", c.group.title, c.code.orEmpty(), c.name, nameEditable = !c.builtIn, codeEditable = c.editable, canDelete = !c.builtIn,
            onDismiss = { editing = null },
            onSave = { code, name -> editing = null; state.store.saveCategory(c.copy(code = code, name = if (c.builtIn) c.name else name)) },
            onDelete = {
                editing = null
                if (!state.store.deleteCategory(c.id)) {
                    val used = state.store.data.items.count { it.categoryId == c.id }
                    message = "“${c.name}” is used by $used item(s). Move or delete those items first."
                }
            })
    }
    addingSubTypeFor?.let { c ->
        TextInputDialog(title = "New sub-type for ${c.propertyType}", label = "Sub-type name", onDismiss = { addingSubTypeFor = null },
            onConfirm = { name -> addingSubTypeFor = null; state.store.addSubType(c.id, name) })
    }
    deletingSubType?.let { s ->
        ConfirmDeleteDialog(
            title = "Remove “${s.name}”?",
            message = "It will no longer be offered in the list. Items that already use it keep their description.",
            onDismiss = { deletingSubType = null },
            onConfirm = { deletingSubType = null; state.store.deleteSubType(s.id) },
        )
    }
    message?.let { MessageDialog("Can't delete", it, onDismiss = { message = null }) }
}

@Composable
private fun CategoryDialog(
    title: String, subtitle: String, initialCode: String, initialName: String,
    nameEditable: Boolean, codeEditable: Boolean, canDelete: Boolean,
    onDismiss: () -> Unit, onSave: (code: String?, name: String) -> Unit, onDelete: () -> Unit,
) {
    var code by remember { mutableStateOf(initialCode) }
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(code, { v -> code = v.filter { it.isDigit() }.take(10) }, label = { Text("IRIS code") },
                    supportingText = { Text("Leave empty if you don't know it yet") }, enabled = codeEditable, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, enabled = nameEditable, modifier = Modifier.fillMaxWidth())
                if (canDelete) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete category", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(code.ifBlank { null }, name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- Electricity companies ----------------

@Composable
fun DiscosPage(state: AppState) {
    val discos = state.store.data.discos.sortedWith(compareBy({ it.sortOrder }, { it.code }))
    var editing by remember { mutableStateOf<Disco?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 900.dp)) {
        Hint("Click a company to add or change its links. Use the official pages only.", Modifier.padding(20.dp))
        discos.forEach { d ->
            Column(Modifier.fillMaxWidth().clickable { editing = d }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                for ((label, url) in listOf("Bill" to d.billUrl, "Certificate" to d.certUrl)) {
                    Text("$label: " + url.ifBlank { "not set" }, style = MaterialTheme.typography.bodySmall,
                        color = if (url.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider()
        }
    }
    editing?.let { d ->
        var bill by remember(d.code) { mutableStateOf(d.billUrl) }
        var cert by remember(d.code) { mutableStateOf(d.certUrl) }
        fun ok(u: String) = u.isBlank() || u.trim().startsWith("https://") || u.trim().startsWith("http://")
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(d.name) },
            text = {
                Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(bill, { bill = it }, label = { Text("Bill page link") }, isError = !ok(bill), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cert, { cert = it }, label = { Text("Tax certificate page link") }, isError = !ok(cert),
                        supportingText = { Text("Links start with https://") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = ok(bill) && ok(cert), onClick = {
                    editing = null
                    state.store.saveDisco(d.copy(billUrl = bill.trim(), certUrl = cert.trim()))
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ---------------- Excel import ----------------

@Composable
fun ImportPage(state: AppState, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var parsed by remember { mutableStateOf<Pair<String, ClientImport.Parsed>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var updatePasswords by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun choose() {
        val f = FileDialogs.open(state.window, "Choose the Excel file (.xlsx)", "xlsx") ?: return
        working = true
        error = null
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    if (ImportRules.isOtherKnownFile(f.name)) throw SheetReader.UnsupportedFileException(ImportRules.wrongFileMessage(f.name))
                    val bytes = f.readBytes()
                    if (!ImportRules.isXlsxWorkbook(bytes)) throw SheetReader.UnsupportedFileException(ImportRules.wrongFileMessage(f.name, bytes))
                    ClientImport.parse(SheetReader.read(bytes)).also { bytes.fill(0) }
                }
            }
            working = false
            r.onSuccess { parsed = f.name to it }.onFailure { e ->
                error = when (e) {
                    is SheetReader.UnsupportedFileException, is ClientImport.NoColumnsException -> e.message
                    else -> "Couldn't read this file. Please choose an Excel .xlsx file."
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 800.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val p = parsed
        val d = done
        when {
            working -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.size(16.dp)); Text("Working…") }
            d != null -> {
                Text("Done", style = MaterialTheme.typography.titleLarge)
                Text("${d.first} new clients added, ${d.second} updated.")
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Your Excel file holds passwords without any protection. Now that they're safely in the app, delete the file (and empty the Recycle Bin).",
                        modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Button(onClick = onDone) { Text("Finish") }
            }
            p != null -> {
                val plan = ClientImport.plan(p.second.rows, state.store.data.clients, updatePasswords)
                Text(p.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Count("New clients", plan.toAdd.size, bold = true)
                        Count(if (updatePasswords) "Existing clients to update" else "Existing clients (kept as they are)",
                            if (updatePasswords) plan.toUpdate.size else plan.toUpdate.size + plan.unchanged)
                        if (updatePasswords) Count("Already the same", plan.unchanged)
                        Count("Rows skipped / with notes", p.second.problems.size)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = updatePasswords, onCheckedChange = { updatePasswords = it })
                    Text("Update the password of clients already in the app")
                }
                if (plan.toAdd.isNotEmpty()) {
                    SectionTitle("First new clients")
                    plan.toAdd.take(5).forEach { c ->
                        Text("${c.cnic}   ${if (c.name != c.cnic) c.name else ""}   password: ${if (c.irisPassword.isEmpty()) "(empty)" else "••••"}",
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (plan.toAdd.size > 5) Text("… and ${plan.toAdd.size - 5} more", style = MaterialTheme.typography.bodySmall)
                }
                if (p.second.problems.isNotEmpty()) {
                    SectionTitle("Notes")
                    p.second.problems.take(50).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (p.second.problems.size > 50) Text("… and ${p.second.problems.size - 50} more", style = MaterialTheme.typography.bodySmall)
                }
                val total = plan.toAdd.size + plan.toUpdate.size
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = total > 0, onClick = {
                        state.store.importClients(plan.toAdd, plan.toUpdate)
                        done = plan.toAdd.size to plan.toUpdate.size
                        parsed = null
                        state.sync.requestSync()
                    }) { Text(if (total == 0) "Nothing to import" else "Import $total client" + if (total == 1) "" else "s") }
                    OutlinedButton(onClick = { parsed = null }) { Text("Cancel") }
                }
            }
            else -> {
                Text("Add many clients at once from an Excel file (.xlsx only).", style = MaterialTheme.typography.bodyLarge)
                Hint(
                    "• Column A: IRIS username (CNIC, or NTN for companies / AOPs), Column B: password — or use headers like “Username” and “Password”.\n" +
                        "• Optional columns with headers: Name, Business Name, NTN, Cell No / Mobile, Email, Address.\n" +
                        "• Passwords are imported exactly as written.\n" +
                        "• The file is read on this PC only. Nothing is uploaded.",
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = ::choose, modifier = Modifier.height(44.dp)) { Text("Choose file") }
            }
        }
    }
}

@Composable
private fun Count(label: String, n: Int, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else null)
        Text(n.toString(), fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.SemiBold else null)
    }
}
