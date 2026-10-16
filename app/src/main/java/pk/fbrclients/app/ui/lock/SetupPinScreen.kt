package pk.fbrclients.app.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.fbrclients.app.security.PinHasher
import pk.fbrclients.app.security.PinStore

/** First launch: choose a PIN, then type it again to confirm. */
@Composable
fun SetupPinScreen(pinStore: PinStore, onDone: () -> Unit) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val confirming = first != null
    PinPadScreen(
        title = if (confirming) "Confirm your PIN" else "Set a PIN",
        message = error ?: if (confirming) "Type the same PIN again."
        else "${PinHasher.MIN_LENGTH} to ${PinHasher.MAX_LENGTH} digits. You'll use it to open the app.",
        isError = error != null,
        pin = pin,
        enabled = !busy,
        onPinChange = { pin = it; error = null },
        onSubmit = {
            if (!confirming) {
                first = pin
                pin = ""
            } else if (pin != first) {
                first = null
                pin = ""
                error = "PINs didn't match. Start again."
            } else {
                busy = true
                val chosen = pin
                scope.launch {
                    withContext(Dispatchers.Default) { pinStore.setPin(chosen) }
                    onDone()
                }
            }
        },
        extraButtonLabel = if (confirming) "Start again" else null,
        onExtraButton = { first = null; pin = ""; error = null },
    )
}
