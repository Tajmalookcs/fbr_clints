package pk.fbrclients.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.fbrclients.app.ui.AppNav
import pk.fbrclients.app.ui.lock.LockScreen
import pk.fbrclients.app.ui.lock.SetupPinScreen
import pk.fbrclients.app.ui.theme.FbrTheme

/** FragmentActivity (not ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshots are allowed on purpose (owner's choice, 2026-10-19).
        enableEdgeToEdge()

        val app = application as FbrApp
        val pinStore = app.pinStore
        val appLock = app.appLock

        setContent {
            FbrTheme {
                val locked by appLock.locked.collectAsStateWithLifecycle()
                var hasPin by remember { mutableStateOf(pinStore.hasPin) }
                // Nothing (not even the database) opens until the first unlock.
                var unlockedOnce by remember { mutableStateOf(false) }
                if (!locked) unlockedOnce = true

                if (!hasPin) {
                    SetupPinScreen(
                        pinStore = pinStore,
                        onDone = {
                            hasPin = true
                            appLock.unlock()
                        },
                    )
                } else {
                    // The lock screen sits on top, so you return to the same
                    // screen after unlocking.
                    Box(Modifier.fillMaxSize()) {
                        if (unlockedOnce) AppNav(app)
                        if (locked) {
                            LockScreen(activity = this@MainActivity, pinStore = pinStore, onUnlocked = appLock::unlock)
                        }
                    }
                }
            }
        }
    }
}
