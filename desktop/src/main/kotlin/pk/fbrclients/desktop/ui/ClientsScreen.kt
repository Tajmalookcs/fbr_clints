package pk.fbrclients.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.util.Formats
import pk.fbrclients.desktop.AppState

/** Search like the phone: name, business, username, NTN, mobile; digits also match without dashes. */
object ClientSearch {
    fun filter(all: List<Client>, query: String): List<Client> {
        val q = query.trim()
        val sorted = all.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        if (q.isEmpty()) return sorted
        val digits = Formats.digitsOnly(q).takeIf { it.isNotEmpty() }
        return sorted.filter { c ->
            listOf(c.name, c.businessName, c.cnic, c.ntn, c.mobile).any { it.contains(q, ignoreCase = true) } ||
                (digits != null && listOf(c.cnic, c.mobile, c.ntn).any { it.replace("-", "").contains(digits) })
        }
    }
}

@Composable
fun ClientsScreen(state: AppState) {
    var query by remember { mutableStateOf("") }
    val clients = state.store.data.clients
    val shown = remember(clients, query) { ClientSearch.filter(clients, query) }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(380.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name, CNIC, NTN, mobile") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (query.isBlank()) "${clients.size} clients" else "${shown.size} of ${clients.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { state.selectedClientId = null; state.editingClientId = 0 }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 6.dp))
                    Text("New client")
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Box(Modifier.weight(1f)) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { c ->
                        ClientRow(c, selected = c.id == state.selectedClientId) {
                            state.editingClientId = null
                            state.selectedClientId = c.id
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val editing = state.editingClientId
            val selected = state.selectedClientId?.let { id -> clients.firstOrNull { it.id == id } }
            when {
                editing != null -> ClientEditPane(state, editing)
                selected != null -> ClientDetailPane(state, selected)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Hint(if (clients.isEmpty()) "No clients yet. Add one, import an Excel file (Settings), or turn on sync with your phone (Settings → Sync)." else "Choose a client on the left.")
                }
            }
        }
    }
}

@Composable
private fun ClientRow(c: Client, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (c.businessName.isNotBlank() && c.businessName != c.name) {
            Text(c.businessName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val sub = listOf(c.cnic, c.mobile).filter { it.isNotBlank() }.joinToString("   ·   ")
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
