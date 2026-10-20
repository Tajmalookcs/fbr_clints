package pk.fbrclients.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.WealthMath
import pk.fbrclients.app.util.Formats
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.Screen
import pk.fbrclients.desktop.platform.Clipboard
import pk.fbrclients.desktop.platform.Open
import java.time.LocalDate

const val IRIS_URL = "https://iris.fbr.gov.pk"

@OptIn(ExperimentalLayoutApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailPane(state: AppState, c: Client) {
    var showPassword by remember(c.id) { mutableStateOf(false) }
    var newYear by remember { mutableStateOf(false) }
    val data = state.store.data
    val statements = remember(data, c.id) {
        val cats = data.categories.associateBy { it.id }
        data.statements.filter { it.clientId == c.id }.sortedByDescending { it.taxYear }.map { s ->
            val (a, l) = WealthMath.totals(data.items.filter { it.statementId == s.id }, cats)
            s to (a - l)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageBar(c.name, actions = {
            TextButton(onClick = { state.editingClientId = c.id }) {
                Icon(Icons.Filled.Edit, null, Modifier.padding(end = 6.dp)); Text("Edit")
            }
        })
        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp).widthIn(max = 900.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        GlobalScope.launch(Dispatchers.IO) { Clipboard.copyLogin(Formats.irisUsername(c.cnic), c.irisPassword) }
                        Open.url(IRIS_URL)
                        state.toast(if (c.irisPassword.isEmpty()) "Username copied" else "Username copied. Paste with Ctrl+V; for the password press Windows key + V and pick the 2nd item.")
                    }) { Text("Open IRIS") }
                    OutlinedButton(onClick = { Clipboard.copy(Formats.irisUsername(c.cnic)); state.toast("Username copied") }) { Text("Copy Username") }
                    OutlinedButton(enabled = c.irisPassword.isNotEmpty(), onClick = {
                        Clipboard.copy(c.irisPassword, sensitive = true); state.toast("Password copied (clears in 30 s)")
                    }) { Text("Copy Password") }
                    OutlinedButton(enabled = Formats.whatsAppNumber(c.mobile) != null, onClick = {
                        Formats.whatsAppNumber(c.mobile)?.let { Open.url("https://wa.me/$it") }
                    }) { Text("WhatsApp") }
                    OutlinedButton(enabled = c.email.isNotBlank(), onClick = { if (!Open.email(c.email)) state.toast("No email app found") }) { Text("Email") }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (c.businessName.isNotBlank()) Field(state, "Business Name", c.businessName)
                        Field(state, "IRIS username (" + (if (c.cnic.length == 15) "CNIC" else "NTN") + ")", c.cnic, mono = true)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("IRIS Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    when { c.irisPassword.isEmpty() -> "—"; showPassword -> c.irisPassword; else -> "••••••••" },
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            if (c.irisPassword.isNotEmpty()) TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
                        }
                        HorizontalDivider()
                        Field(state, "NTN", c.ntn, mono = true)
                        Field(state, "Mobile", c.mobile, mono = true)
                        Field(state, "Email", c.email)
                        Field(state, "Address", c.address)
                        Field(state, "Notes", c.notes, last = true)
                    }
                }

                BillsSection(state, c.id)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wealth Statements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { newYear = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.padding(end = 6.dp)); Text("New tax year")
                    }
                }
                if (statements.isEmpty()) Hint("No Wealth Statement yet.")
                statements.forEach { (s, net) ->
                    Card(onClick = { state.screen = Screen.Statement(s.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Tax Year ${s.taxYear}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Net assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs " + Formats.amount(net), fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }

    if (newYear) {
        NewYearDialog(
            existingYears = statements.map { it.first.taxYear },
            onDismiss = { newYear = false },
            onCreate = { year, copyFrom ->
                newYear = false
                val fromId = copyFrom?.let { y -> statements.firstOrNull { it.first.taxYear == y }?.first?.id }
                val id = state.store.createYear(c.id, year, fromId)
                state.screen = Screen.Statement(id)
            },
        )
    }
}

@Composable
private fun Field(state: AppState, label: String, value: String, mono: Boolean = false, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, fontFamily = if (mono && value.isNotBlank()) FontFamily.Monospace else null)
            }
        }
        if (value.isNotBlank()) TextButton(onClick = { Clipboard.copy(value); state.toast("$label copied") }) { Text("Copy") }
    }
    if (!last) HorizontalDivider()
}

/** Pick a tax year; by default copy all items from the latest earlier year. */
@Composable
fun NewYearDialog(existingYears: List<Int>, onDismiss: () -> Unit, onCreate: (year: Int, copyFrom: Int?) -> Unit) {
    val suggested = remember(existingYears) {
        val now = LocalDate.now()
        val current = WealthMath.currentTaxYear(now.year, now.monthValue)
        if (current !in existingYears) current else (existingYears.maxOrNull() ?: current) + 1
    }
    var text by remember { mutableStateOf(suggested.toString()) }
    var copy by remember { mutableStateOf(true) }
    val year = text.toIntOrNull()
    val source = year?.let { y -> existingYears.filter { it < y }.maxOrNull() }
    val error = when {
        year == null || year !in 2000..2100 -> "Enter a year like 2026"
        year in existingYears -> "Tax Year $year already exists"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Wealth Statement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Tax Year") },
                    isError = error != null,
                    supportingText = { Text(error ?: "Tax Year $year = 1 July ${(year ?: 1) - 1} – 30 June $year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (source != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copy, onCheckedChange = { copy = it })
                        Text("Copy all items from Tax Year $source")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(year!!, if (copy) source else null) }, enabled = error == null) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
