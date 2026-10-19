package pk.fbrclients.app.ui.clients

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.ClientDao
import pk.fbrclients.app.data.WealthDao
import pk.fbrclients.app.data.ElectricityDao
import pk.fbrclients.app.ui.bills.BillsSection
import pk.fbrclients.app.data.WealthMath
import kotlinx.coroutines.launch
import java.time.LocalDate
import pk.fbrclients.app.util.Formats
import pk.fbrclients.app.util.Actions
import pk.fbrclients.app.util.SecureClipboard

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    id: Long,
    dao: ClientDao,
    wealth: WealthDao,
    electricity: ElectricityDao,
    clipboard: SecureClipboard,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenStatement: (Long) -> Unit,
    onOpenBrowser: (url: String, reference: String?) -> Unit,
) {
    val flow = remember(id) { dao.byId(id) }
    val client by flow.collectAsStateWithLifecycle(initialValue = null)
    val statementsFlow = remember(id) { wealth.summaries(id) }
    val statements by statementsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var newYear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            FbrTopBar(title = client?.name ?: "", onBack = onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit client") }
            }
        },
    ) { padding ->
        val c = client ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Action buttons
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val hasMobile = c.mobile.isNotBlank()
                ActionButton("Call", Icons.Filled.Call, hasMobile) { Actions.call(ctx, c.mobile) }
                ActionButton("WhatsApp", Icons.AutoMirrored.Filled.Send, hasMobile) { Actions.whatsApp(ctx, c.mobile) }
                ActionButton("SMS", null, hasMobile) { Actions.sms(ctx, c.mobile) }
                ActionButton("Email", Icons.Filled.Email, c.email.isNotBlank()) { Actions.email(ctx, c.email) }
                ActionButton("Copy Username", null, true) { clipboard.copy("Username", c.cnic) }
                ActionButton("Copy Password", null, c.irisPassword.isNotEmpty()) {
                    clipboard.copy("Password", c.irisPassword, sensitive = true)
                }
                ActionButton("IRIS in app", null, true) { onOpenBrowser(Actions.IRIS_URL, null) }
                ActionButton("Open IRIS", null, true) { Actions.openIris(ctx) }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (c.businessName.isNotBlank()) Field("Business Name", c.businessName)
                    Field("IRIS username (" + (if (c.cnic.length == 15) "CNIC" else "NTN") + ")", c.cnic, mono = true)
                    PasswordField(c, showPassword, onToggle = { showPassword = !showPassword })
                    Field("NTN", c.ntn, mono = true)
                    Field("Mobile", c.mobile, mono = true)
                    Field("Email", c.email)
                    Field("Address", c.address)
                    Field("Notes", c.notes, last = true)
                }
            }

            BillsSection(clientId = id, dao = electricity, clipboard = clipboard, onOpenInApp = { url, ref -> onOpenBrowser(url, ref) })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wealth Statements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { newYear = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("New tax year")
                }
            }
            if (statements.isEmpty()) {
                Text(
                    "No Wealth Statement yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            statements.forEach { s ->
                Card(onClick = { onOpenStatement(s.statement.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tax Year ${s.statement.taxYear}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Net assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Rs " + Formats.amount(s.netAssets), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (newYear) {
        NewYearDialog(
            existingYears = statements.map { it.statement.taxYear },
            onDismiss = { newYear = false },
            onCreate = { year, copyFrom ->
                newYear = false
                scope.launch {
                    val fromId = copyFrom?.let { y -> statements.firstOrNull { it.statement.taxYear == y }?.statement?.id }
                    onOpenStatement(wealth.createYear(id, year, fromId))
                }
            },
        )
    }
}

/** Pick a tax year; by default copy all items from the latest earlier year. */
@Composable
private fun NewYearDialog(existingYears: List<Int>, onDismiss: () -> Unit, onCreate: (year: Int, copyFrom: Int?) -> Unit) {
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
        confirmButton = {
            TextButton(onClick = { onCreate(year!!, if (copy) source else null) }, enabled = error == null) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ActionButton(label: String, icon: ImageVector?, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        }
        Text(label)
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false, last: Boolean = false) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (mono && value.isNotBlank()) FontFamily.Monospace else null,
            )
        }
    }
    if (!last) HorizontalDivider()
}

@Composable
private fun PasswordField(c: Client, shown: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("IRIS Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    c.irisPassword.isEmpty() -> "—"
                    shown -> c.irisPassword
                    else -> "••••••••"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (c.irisPassword.isNotEmpty()) {
            TextButton(onClick = onToggle) { Text(if (shown) "Hide" else "Show") }
        }
    }
    HorizontalDivider()
}
