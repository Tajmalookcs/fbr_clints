package pk.fbrclients.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import pk.fbrclients.app.security.AppLock
import pk.fbrclients.app.security.PinStore

class FbrApp : Application() {
    lateinit var pinStore: PinStore
        private set
    lateinit var appLock: AppLock
        private set

    override fun onCreate() {
        super.onCreate()
        pinStore = PinStore(this)
        appLock = AppLock(pinStore)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLock)
    }
}
