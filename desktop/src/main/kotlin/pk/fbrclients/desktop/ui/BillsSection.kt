package pk.fbrclients.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import pk.fbrclients.app.data.Disco
import pk.fbrclients.app.data.ElectricityBill
import pk.fbrclients.app.ui.components.Dropdown
import pk.fbrclients.app.util.Formats
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.platform.Clipboard
import pk.fbrclients.desktop.platform.Open

/** Electricity bills on the client page. Check bill / Tax certificate copy the reference and open the page in the browser. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillsSection(state: AppState, clientId: Long) {
    val data = state.store.data
    val bills = data.bills.filter { it.clientId == clientId }.sortedBy { it.id }
    val discos = data.discos.sortedWith(compareBy({ it.sortOrder }, { it.code }))
    val byCode = discos.associateBy { it.code }
    var editing by remember { mutableStateOf<ElectricityBill?>(null) }

    fun open(url: String?, what: String, ref: String) {
        if (url.isNullOrBlank()) {
            state.toast("No $what link yet. Add it in Settings → Electricity companies.")
            return
        }
        Clipboard.copy(ref)
        Open.url(url)
        state.toast("Reference number copied. Paste it with Ctrl+V.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Electricity bills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { editing = ElectricityBill(clientId = clientId, company = "", referenceNo = "") }) {
                Icon(Icons.Filled.Add, null, Modifier.padding(end = 6.dp)); Text("Add bill")
            }
        }
        if (bills.isEmpty()) Hint("No commercial bill saved.")
        bills.forEach { b ->
            val disco = byCode[b.company]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOf(disco?.code ?: b.company, b.label).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                            Text(b.referenceNo, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { editing = b }) { Icon(Icons.Filled.Edit, "Edit bill") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        FilledTonalButton(onClick = { open(disco?.billUrl, "bill", b.referenceNo) }) { Text("Check bill") }
                        FilledTonalButton(onClick = { open(disco?.certUrl, "tax certificate", b.referenceNo) }) { Text("Tax certificate") }
                        OutlinedButton(onClick = { Clipboard.copy(b.referenceNo); state.toast("Reference number copied") }) { Text("Copy ref") }
                    }
                }
            }
        }
    }

    editing?.let { b ->
        BillDialog(
            bill = b,
            discos = discos,
            onDismiss = { editing = null },
            onSave = { editing = null; state.store.saveBill(it) },
            onDelete = { editing = null; state.store.deleteBill(b.id) },
        )
    }
}

@Composable
private fun BillDialog(bill: ElectricityBill, discos: List<Disco>, onDismiss: () -> Unit, onSave: (ElectricityBill) -> Unit, onDelete: () -> Unit) {
    var company by remember { mutableStateOf(discos.firstOrNull { it.code == bill.company }) }
    var ref by remember { mutableStateOf(bill.referenceNo) }
    var label by remember { mutableStateOf(bill.label) }
    var refError by remember { mutableStateOf<String?>(null) }
    val isNew = bill.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add electricity bill" else "Edit electricity bill") },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown(label = "Company *", options = discos, selected = company, optionLabel = { it.name }, onSelect = { company = it })
                OutlinedTextField(
                    value = ref,
                    onValueChange = { v -> ref = Formats.digitsOnly(v).take(24); refError = null },
                    label = { Text("Reference number *") },
                    isError = refError != null,
                    supportingText = { Text(refError ?: "As printed on the bill (digits only)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label (optional)") }, placeholder = { Text("e.g. Shop, Main Bazar") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete this bill")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = company != null, onClick = {
                if (ref.length < 8) refError = "Enter the full reference number"
                else onSave(bill.copy(company = company!!.code, referenceNo = ref, label = label.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
