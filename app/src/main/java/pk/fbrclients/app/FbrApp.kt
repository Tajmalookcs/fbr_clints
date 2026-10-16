package pk.fbrclients.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import pk.fbrclients.app.data.AppDatabase
import pk.fbrclients.app.security.AppLock
import pk.fbrclients.app.security.PinStore
import pk.fbrclients.app.util.SecureClipboard

class FbrApp : Application() {
    lateinit var pinStore: PinStore
        private set
    lateinit var appLock: AppLock
        private set
    lateinit var clipboard: SecureClipboard
        private set

    /** Opened on first use (after unlock), not at app start. */
    val database: AppDatabase by lazy { AppDatabase.open(this) }

    override fun onCreate() {
        super.onCreate()
        pinStore = PinStore(this)
        appLock = AppLock(pinStore)
        clipboard = SecureClipboard(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLock)
    }
}
