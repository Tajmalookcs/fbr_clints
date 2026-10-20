package pk.fbrclients.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pk.fbrclients.app.security.PinHasher
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.store.Settings

/** Modifier: run [action] when Enter is pressed. */
fun Modifier.onEnter(action: () -> Unit) = onPreviewKeyEvent {
    if ((it.key == Key.Enter || it.key == Key.NumPadEnter) && it.type == KeyEventType.KeyDown) { action(); true } else false
}

@Composable
fun LockScreen(state: AppState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource("app.png"), contentDescription = null, modifier = Modifier.size(88.dp))
            Text("FBR Clients", style = MaterialTheme.typography.headlineMedium)
            val err = state.loadError
            when {
                err != null -> LoadError(state, err)
                !state.hasPin -> SetupPin(state)
                else -> Unlock(state)
            }
        }
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, onEnter: () -> Unit, focus: FocusRequester? = null, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(PinHasher.MAX_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().onEnter(onEnter).let { if (focus != null) it.focusRequester(focus) else it },
    )
}

@Composable
private fun SetupPin(state: AppState) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun done() {
        error = when {
            !PinHasher.isValidPin(pin) -> "Use ${PinHasher.MIN_LENGTH} to ${PinHasher.MAX_LENGTH} digits"
            pin != pin2 -> "The two PINs are different"
            else -> null
        }
        if (error != null) return
        state.settings.setPin(pin)
        state.hasPin = true
        state.unlocked()
    }
    Text("Choose a PIN for this PC. It locks the app; you'll need it every time you open it.", textAlign = TextAlign.Center)
    PinField(pin, { pin = it; error = null }, "New PIN (${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits)", ::done, focus)
    PinField(pin2, { pin2 = it; error = null }, "Type it again", ::done, error = error)
    Button(onClick = ::done, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Save PIN") }
}

@Composable
private fun Unlock(state: AppState) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pausedUntil by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(pausedUntil) {
        while (pausedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val paused = pausedUntil > now
    fun go() {
        if (paused || pin.isEmpty()) return
        when (val r = state.settings.checkPin(pin)) {
            Settings.PinResult.Ok -> { pin = ""; error = null; state.unlocked() }
            is Settings.PinResult.Wrong -> {
                pin = ""
                error = if (r.attemptsLeftBeforePause > 0) "Wrong PIN. ${r.attemptsLeftBeforePause} more tries before a pause." else "Wrong PIN."
            }
            is Settings.PinResult.Paused -> { pin = ""; pausedUntil = r.untilMillis; error = null }
        }
    }
    Text("Enter your PIN")
    PinField(pin, { pin = it; error = null }, "PIN", ::go, focus, error = error)
    if (paused) {
        Text("Too many wrong PINs. Try again in ${((pausedUntil - now) / 1000) + 1} seconds.", color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = ::go, enabled = !paused, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Unlock") }
}

@Composable
private fun LoadError(state: AppState, message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    OutlinedButton(onClick = state::startFresh, modifier = Modifier.fillMaxWidth()) {
        Text("Start with empty data (the old file is kept)")
    }
}
