package pk.fbrclients.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.fbrclients.app.ui.home.HomeScreen
import pk.fbrclients.app.ui.lock.LockScreen
import pk.fbrclients.app.ui.lock.SetupPinScreen
import pk.fbrclients.app.ui.theme.FbrTheme

/** FragmentActivity (not ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, no screen recording, blank preview in recent apps.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val app = application as FbrApp
        val pinStore = app.pinStore
        val appLock = app.appLock

        setContent {
            FbrTheme {
                val locked by appLock.locked.collectAsStateWithLifecycle()
                var hasPin by remember { mutableStateOf(pinStore.hasPin) }

                when {
                    !hasPin -> SetupPinScreen(
                        pinStore = pinStore,
                        onDone = {
                            hasPin = true
                            appLock.unlock()
                        },
                    )
                    locked -> LockScreen(
                        activity = this,
                        pinStore = pinStore,
                        onUnlocked = appLock::unlock,
                    )
                    else -> HomeScreen(onLock = appLock::lockNow)
                }
            }
        }
    }
}
