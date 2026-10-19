package pk.fbrclients.app.ui.bills

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pk.fbrclients.app.data.Disco
import pk.fbrclients.app.data.ElectricityBill
import pk.fbrclients.app.data.ElectricityDao
import pk.fbrclients.app.ui.components.Dropdown
import pk.fbrclients.app.util.Actions
import pk.fbrclients.app.util.Formats
import pk.fbrclients.app.util.SecureClipboard

/** "Electricity bills" on the client page: list, add / edit, and the bill / certificate buttons. */
@Composable
fun BillsSection(clientId: Long, dao: ElectricityDao, clipboard: SecureClipboard) {
    val billsFlow = remember(clientId) { dao.bills(clientId) }
    val bills by billsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val discosFlow = remember { dao.discos() }
    val discos by discosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val byCode = discos.associateBy { it.code }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // null = closed; id 0 = new bill
    var editing by remember { mutableStateOf<ElectricityBill?>(null) }

    fun openLink(url: String?, what: String, ref: String) {
        if (url.isNullOrBlank()) {
            Toast.makeText(ctx, "No $what link yet. Add it in Settings → Electricity companies.", Toast.LENGTH_LONG).show()
            return
        }
        // The sites need the reference typed in, so put it on the clipboard first.
        clipboard.copy("Reference no.", ref)
        Actions.openUrl(ctx, url)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Electricity bills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { editing = ElectricityBill(clientId = clientId, company = "", referenceNo = "") }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Add bill")
            }
        }
        if (bills.isEmpty()) {
            Text("No commercial bill saved.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        bills.forEach { b -> BillCard(b, byCode[b.company], onEdit = { editing = b }, onOpen = ::openLink, onCopy = { clipboard.copy("Reference no.", b.referenceNo) }) }
    }

    editing?.let { b ->
        BillDialog(
            bill = b,
            discos = discos,
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                scope.launch { if (saved.id == 0L) dao.insert(saved) else dao.update(saved) }
            },
            onDelete = {
                editing = null
                scope.launch { dao.delete(b) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillCard(
    b: ElectricityBill,
    disco: Disco?,
    onEdit: () -> Unit,
    onOpen: (url: String?, what: String, ref: String) -> Unit,
    onCopy: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOf(disco?.code ?: b.company, b.label).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(b.referenceNo, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit bill") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                FilledTonalButton(onClick = { onOpen(disco?.billUrl, "bill", b.referenceNo) }) { Text("Check bill") }
                FilledTonalButton(onClick = { onOpen(disco?.certUrl, "tax certificate", b.referenceNo) }) { Text("Tax certificate") }
                OutlinedButton(onClick = onCopy) { Text("Copy ref") }
            }
        }
    }
}

@Composable
private fun BillDialog(
    bill: ElectricityBill,
    discos: List<Disco>,
    onDismiss: () -> Unit,
    onSave: (ElectricityBill) -> Unit,
    onDelete: () -> Unit,
) {
    var company by remember { mutableStateOf(discos.firstOrNull { it.code == bill.company }) }
    var ref by remember { mutableStateOf(bill.referenceNo) }
    var label by remember { mutableStateOf(bill.label) }
    var refError by remember { mutableStateOf<String?>(null) }
    val isNew = bill.id == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add electricity bill" else "Edit electricity bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown(
                    label = "Company *",
                    options = discos,
                    selected = company,
                    optionLabel = { it.name },
                    onSelect = { company = it },
                )
                OutlinedTextField(
                    value = ref,
                    onValueChange = { v -> ref = Formats.digitsOnly(v).take(24); refError = null },
                    label = { Text("Reference number *") },
                    isError = refError != null,
                    supportingText = { Text(refError ?: "As printed on the bill (digits only)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g. Shop, Main Bazar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete this bill") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = company != null,
                onClick = {
                    if (ref.length < 8) {
                        refError = "Enter the full reference number"
                    } else {
                        onSave(bill.copy(company = company!!.code, referenceNo = ref, label = label.trim()))
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
