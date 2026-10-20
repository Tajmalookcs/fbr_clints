package pk.fbrclients.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.Screen
import pk.fbrclients.desktop.sync.DesktopSync

@Composable
fun MainScreen(state: AppState) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Spacer(Modifier.size(8.dp))
            NavigationRailItem(
                selected = state.screen is Screen.Clients || state.screen is Screen.Statement,
                onClick = { state.screen = Screen.Clients },
                icon = { Icon(Icons.Filled.People, null) },
                label = { Text("Clients") },
            )
            NavigationRailItem(
                selected = state.screen is Screen.Settings,
                onClick = { state.screen = Screen.Settings },
                icon = { Icon(Icons.Filled.Settings, null) },
                label = { Text("Settings") },
            )
            Spacer(Modifier.weight(1f))
            SyncBadge(state)
            NavigationRailItem(
                selected = false,
                onClick = state::lock,
                icon = { Icon(Icons.Filled.Lock, null) },
                label = { Text("Lock") },
            )
            Spacer(Modifier.size(8.dp))
        }
        VerticalDivider()
        Column(Modifier.weight(1f)) {
            when (val s = state.screen) {
                Screen.Clients -> ClientsScreen(state)
                is Screen.Statement -> StatementScreen(state, s.id)
                Screen.Settings -> SettingsScreen(state)
            }
        }
    }
}

@Composable
private fun SyncBadge(state: AppState) {
    val status by state.sync.status.collectAsState()
    when (val s = status) {
        DesktopSync.Status.Off -> Unit
        DesktopSync.Status.Running -> Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Syncing", style = MaterialTheme.typography.labelSmall)
        }
        is DesktopSync.Status.Done -> TextButton(onClick = { state.sync.requestSync() }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Sync, "Sync now")
                Text("Sync", style = MaterialTheme.typography.labelSmall)
            }
        }
        is DesktopSync.Status.Failed -> TextButton(onClick = { state.screen = Screen.Settings }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.SyncProblem, "Sync problem", tint = MaterialTheme.colorScheme.error)
                Text("Problem", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
