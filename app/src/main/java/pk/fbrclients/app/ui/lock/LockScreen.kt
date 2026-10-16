package pk.fbrclients.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.fbrclients.app.security.PinStore

@Composable
fun LockScreen(activity: FragmentActivity, pinStore: PinStore, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pausedUntil by remember { mutableLongStateOf(pinStore.pausedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    val canUseBiometric = remember {
        pinStore.biometricEnabled &&
            BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showFingerprint() {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    pinStore.resetFailures()
                    onUnlocked()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock FBR Clients")
                .setSubtitle("Use your fingerprint")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build(),
        )
    }

    // Back on the lock screen leaves the app instead of reaching the screen behind it.
    BackHandler { activity.moveTaskToBack(true) }

    // Offer fingerprint straight away when the lock screen appears.
    LaunchedEffect(Unit) { if (canUseBiometric) showFingerprint() }

    // Tick once a second while the keypad is paused, so the countdown updates.
    LaunchedEffect(pausedUntil) {
        while (pausedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }

    val paused = pausedUntil > now
    val pauseText = if (paused) "Too many wrong PINs. Try again in ${formatWait(pausedUntil - now)}." else null

    PinPadScreen(
        title = "Enter your PIN",
        message = pauseText ?: message,
        isError = paused || isError,
        pin = pin,
        enabled = !busy && !paused,
        onPinChange = { pin = it; message = null; isError = false },
        onSubmit = {
            busy = true
            val entered = pin
            scope.launch {
                val result = withContext(Dispatchers.Default) { pinStore.check(entered) }
                busy = false
                pin = ""
                when (result) {
                    PinStore.Result.Ok -> onUnlocked()
                    is PinStore.Result.Wrong -> {
                        isError = true
                        message = "Wrong PIN. ${result.attemptsLeftBeforePause} tries left before a pause."
                    }
                    is PinStore.Result.Paused -> {
                        pausedUntil = result.untilMillis
                        now = System.currentTimeMillis()
                    }
                }
            }
        },
        extraButtonLabel = if (canUseBiometric) "Use fingerprint" else null,
        onExtraButton = ::showFingerprint,
    )
}

private fun formatWait(ms: Long): String {
    val s = (ms + 999) / 1000
    return if (s < 60) "$s s" else "${(s + 59) / 60} min"
}
