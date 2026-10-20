package pk.fbrclients.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import pk.fbrclients.desktop.store.DataStore
import pk.fbrclients.desktop.store.Settings
import pk.fbrclients.desktop.ui.FbrTheme
import pk.fbrclients.desktop.ui.LockScreen
import pk.fbrclients.desktop.ui.MainScreen

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val state = remember { AppState(DataStore(), Settings()) }
    Window(
        onCloseRequest = {
            // Last sync before closing (at most 20 seconds).
            if (state.isLoaded && !state.locked) runBlocking { withTimeoutOrNull(20_000) { state.sync.syncNow() } }
            pk.fbrclients.desktop.browser.AppBrowser.shutdown()
            exitApplication()
        },
        title = "FBR Clients",
        icon = painterResource("app.png"),
        state = rememberWindowState(size = DpSize(1280.dp, 840.dp)),
    ) {
        LaunchedEffect(Unit) {
            state.window = window
            window.minimumSize = java.awt.Dimension(960, 640)
        }
        // Auto-lock after some minutes without use.
        LaunchedEffect(Unit) {
            while (true) {
                delay(15_000)
                val idle = System.currentTimeMillis() - state.lastActivity
                if (!state.locked && idle > state.settings.autoLockMinutes * 60_000L) state.lock()
            }
        }
        FbrTheme {
            Box(
                Modifier.fillMaxSize()
                    .onPointerEvent(PointerEventType.Move) { state.touch() }
                    .onPointerEvent(PointerEventType.Press) { state.touch() }
                    .onPreviewKeyEvent { state.touch(); false },
            ) {
                if (state.locked) LockScreen(state) else MainScreen(state)
                SnackbarHost(state.snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}
