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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.ClientDao
import pk.fbrclients.app.util.Actions
import pk.fbrclients.app.util.SecureClipboard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientDetailScreen(
    id: Long,
    dao: ClientDao,
    clipboard: SecureClipboard,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val flow = remember(id) { dao.byId(id) }
    val client by flow.collectAsStateWithLifecycle(initialValue = null)
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
                ActionButton("Copy CNIC", null, true) { clipboard.copy("CNIC", c.cnic) }
                ActionButton("Copy Password", null, c.irisPassword.isNotEmpty()) {
                    clipboard.copy("Password", c.irisPassword, sensitive = true)
                }
                ActionButton("Open IRIS", null, true) { Actions.openIris(ctx) }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Field("CNIC (IRIS username)", c.cnic, mono = true)
                    PasswordField(c, showPassword, onToggle = { showPassword = !showPassword })
                    Field("NTN", c.ntn, mono = true)
                    Field("Mobile", c.mobile, mono = true)
                    Field("Email", c.email)
                    Field("Address", c.address)
                    Field("Notes", c.notes, last = true)
                }
            }

            Text("Wealth Statements", style = MaterialTheme.typography.titleMedium)
            Text(
                "Coming in the next update.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
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
