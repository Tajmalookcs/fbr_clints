package pk.fbrclients.app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide locked/unlocked state. Starts locked. Locks again when the app
 * comes back after being in the background longer than the auto-lock time.
 */
class AppLock(private val pinStore: PinStore) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun unlock() {
        _locked.value = false
    }

    fun lockNow() {
        _locked.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val elapsedMs = SystemClock.elapsedRealtime() - since
        if (elapsedMs >= pinStore.autoLockSeconds * 1000L) lockNow()
    }
}
