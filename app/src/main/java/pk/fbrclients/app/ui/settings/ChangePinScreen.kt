package pk.fbrclients.app.ui.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.fbrclients.app.security.PinStore
import pk.fbrclients.app.ui.lock.PinPadScreen

/** Current PIN → new PIN → confirm. */
@Composable
fun ChangePinScreen(pinStore: PinStore, onDone: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    var pin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    PinPadScreen(
        title = when (stage) { 0 -> "Enter current PIN"; 1 -> "Enter new PIN"; else -> "Confirm new PIN" },
        message = error ?: when (stage) { 1 -> "4 to 8 digits."; 2 -> "Type the new PIN again."; else -> null },
        isError = error != null,
        pin = pin,
        enabled = !busy,
        onPinChange = { pin = it; error = null },
        onSubmit = {
            val entered = pin
            pin = ""
            when (stage) {
                0 -> {
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { pinStore.check(entered) }
                        busy = false
                        when (r) {
                            PinStore.Result.Ok -> stage = 1
                            is PinStore.Result.Wrong -> error = "Wrong PIN."
                            is PinStore.Result.Paused -> error = "Too many wrong PINs. Try again later."
                        }
                    }
                }
                1 -> { newPin = entered; stage = 2 }
                else -> if (entered != newPin) {
                    stage = 1
                    error = "PINs didn't match. Enter the new PIN again."
                } else {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { pinStore.setPin(entered) }
                        Toast.makeText(ctx, "PIN changed", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                }
            }
        },
        extraButtonLabel = "Cancel",
        onExtraButton = onDone,
    )
}
