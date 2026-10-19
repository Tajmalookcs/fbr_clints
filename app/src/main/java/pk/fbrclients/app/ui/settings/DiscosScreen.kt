package pk.fbrclients.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pk.fbrclients.app.data.Disco
import pk.fbrclients.app.data.ElectricityDao
import pk.fbrclients.app.ui.clients.FbrTopBar

/** Settings → Electricity companies: the bill and tax-certificate link for each company. */
@Composable
fun DiscosScreen(dao: ElectricityDao, onBack: () -> Unit) {
    val discos by remember { dao.discos() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<Disco?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { FbrTopBar("Electricity companies", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Tap a company to add or change its links. Use the official pages only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(discos, key = { it.code }) { d ->
                Column(Modifier.fillMaxWidth().clickable { editing = d }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(d.name, style = MaterialTheme.typography.bodyLarge)
                    LinkLine("Bill", d.billUrl)
                    LinkLine("Certificate", d.certUrl)
                }
                HorizontalDivider()
            }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bill, onValueChange = { bill = it }, label = { Text("Bill page link") },
                        isError = !ok(bill), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cert, onValueChange = { cert = it }, label = { Text("Tax certificate page link") },
                        isError = !ok(cert), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = { Text("Links start with https://") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = ok(bill) && ok(cert), onClick = {
                    editing = null
                    scope.launch { dao.updateDisco(d.copy(billUrl = bill.trim(), certUrl = cert.trim())) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LinkLine(label: String, url: String) {
    Text(
        "$label: " + url.ifBlank { "not set" },
        style = MaterialTheme.typography.bodySmall,
        color = if (url.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
