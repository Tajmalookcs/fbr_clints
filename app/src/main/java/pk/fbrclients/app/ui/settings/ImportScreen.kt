package pk.fbrclients.app.ui.settings

import android.app.Application
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.fbrclients.app.FbrApp
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.importer.ClientImport
import pk.fbrclients.app.data.importer.SheetReader
import pk.fbrclients.app.ui.clients.FbrTopBar

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val fbr = app as FbrApp

    sealed interface State {
        data object Pick : State
        data object Working : State
        data class Preview(val fileName: String, val parsed: ClientImport.Parsed, val existing: List<Client>) : State
        data class Done(val added: Int, val updated: Int) : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Pick)
        private set
    var updatePasswords by mutableStateOf(true)

    fun load(uri: Uri) {
        state = State.Working
        viewModelScope.launch {
            state = runCatching {
                withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"
                    if (!ImportRules.isXlsxName(name)) throw SheetReader.UnsupportedFileException(ImportRules.wrongFileMessage(name))
                    val bytes = ctx.contentResolver.openInputStream(uri).use { requireNotNull(it) { "Could not open the file" }.readBytes() }
                    if (!ImportRules.isZip(bytes)) throw SheetReader.UnsupportedFileException(ImportRules.wrongFileMessage(name))
                    val parsed = ClientImport.parse(SheetReader.read(bytes))
                    bytes.fill(0)
                    State.Preview(name, parsed, fbr.database.clients().allOnce())
                }
            }.getOrElse { e ->
                State.Failed(
                    when (e) {
                        is SheetReader.UnsupportedFileException, is ClientImport.NoColumnsException -> e.message!!
                        else -> "Couldn't read this file. Please choose an Excel .xlsx file."
                    },
                )
            }
        }
    }

    fun plan(p: State.Preview) = ClientImport.plan(p.parsed.rows, p.existing, updatePasswords)

    fun import(p: State.Preview) {
        val plan = plan(p)
        state = State.Working
        viewModelScope.launch {
            state = runCatching {
                val db = fbr.database
                db.withTransaction {
                    db.clients().insertAll(plan.toAdd)
                    db.clients().updateAll(plan.toUpdate)
                }
                State.Done(plan.toAdd.size, plan.toUpdate.size)
            }.getOrElse { State.Failed("Import failed. Nothing was changed.") }
        }
    }

    fun reset() {
        state = State.Pick
    }
}

@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.load(uri) }
    val launchPicker = {
        // Phones label Excel files inconsistently, so a type filter can grey them out.
        // Show every file; the app then accepts only real .xlsx files.
        pick.launch(arrayOf("*/*"))
    }

    Scaffold(topBar = { FbrTopBar("Import clients", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = vm.state) {
                ImportViewModel.State.Pick -> {
                    Text("Add many clients at once from an Excel file (.xlsx only).", style = MaterialTheme.typography.bodyLarge)
                    Hint(
                        "• Column A: IRIS username (CNIC, or NTN for companies / AOPs), Column B: password — or use headers like “Username” and “Password”.\n" +
                            "• Optional columns with headers: Name, Business Name, NTN, Cell No / Mobile, Email, Address.\n" +
                            "• Passwords are imported exactly as written.\n" +
                            "• The file is read on this phone only. Nothing is uploaded.",
                    )
                    Button(onClick = launchPicker, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Choose file") }
                }

                ImportViewModel.State.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.size(16.dp))
                    Text("Working…")
                }

                is ImportViewModel.State.Failed -> {
                    Text("Couldn't import", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(s.message)
                    Button(onClick = launchPicker, modifier = Modifier.fillMaxWidth()) { Text("Choose another file") }
                }

                is ImportViewModel.State.Preview -> {
                    val plan = vm.plan(s)
                    Text(s.fileName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Count("New clients", plan.toAdd.size, bold = true)
                            Count(if (vm.updatePasswords) "Existing clients to update" else "Existing clients (kept as they are)", if (vm.updatePasswords) plan.toUpdate.size else plan.toUpdate.size + plan.unchanged)
                            if (vm.updatePasswords) Count("Already the same", plan.unchanged)
                            Count("Rows skipped / with notes", s.parsed.problems.size)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vm.updatePasswords, onCheckedChange = { vm.updatePasswords = it })
                        Text("Update the password of clients already in the app")
                    }
                    if (plan.toAdd.any { it.name == it.cnic }) {
                        Hint("Clients without a name are saved with their business name or username as the name. Open a client and tap Edit to add the name.")
                    }
                    if (plan.toAdd.isNotEmpty()) {
                        Text("First new clients", style = MaterialTheme.typography.titleSmall)
                        plan.toAdd.take(5).forEach { c ->
                            Text("${c.cnic}   ${if (c.name != c.cnic) c.name else ""}   password: ${if (c.irisPassword.isEmpty()) "(empty)" else "••••"}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        if (plan.toAdd.size > 5) Text("… and ${plan.toAdd.size - 5} more", style = MaterialTheme.typography.bodySmall)
                    }
                    if (s.parsed.problems.isNotEmpty()) {
                        Text("Notes", style = MaterialTheme.typography.titleSmall)
                        s.parsed.problems.take(50).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (s.parsed.problems.size > 50) Text("… and ${s.parsed.problems.size - 50} more", style = MaterialTheme.typography.bodySmall)
                    }
                    val total = plan.toAdd.size + plan.toUpdate.size
                    Button(onClick = { vm.import(s) }, enabled = total > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (total == 0) "Nothing to import" else "Import $total client" + if (total == 1) "" else "s")
                    }
                    OutlinedButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }

                is ImportViewModel.State.Done -> {
                    Text("Done", style = MaterialTheme.typography.titleLarge)
                    Text("${s.added} new clients added, ${s.updated} updated.")
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "Your Excel file holds passwords without any protection. Now that they're safely in the app, delete the file from your phone (and from WhatsApp / Downloads, if you received it there).",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Hint("Tip: make a backup now — Settings → Back up now.")
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
                }
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

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Import accepts only Excel .xlsx files (owner's choice, 2026-10-20). Plain JVM code, tested. */
object ImportRules {
    fun isXlsxName(name: String) = name.trim().lowercase().endsWith(".xlsx")

    /** .xlsx files are zip archives: they start with "PK". */
    fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun wrongFileMessage(name: String): String = when {
        name.lowercase().endsWith(".fbrbak") -> "This is a backup file. To use it, go to Settings → Restore from backup."
        name.lowercase().endsWith(".xls") -> "This is an old Excel file (.xls). In Excel, use Save As → “Excel Workbook (.xlsx)”, then import that file."
        else -> "Please choose an Excel .xlsx file."
    }
}
