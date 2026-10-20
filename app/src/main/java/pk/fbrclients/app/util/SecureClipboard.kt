package pk.fbrclients.app.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast

/**
 * Copies text. Sensitive text (passwords) is hidden from the clipboard preview
 * and cleared after [CLEAR_AFTER_MS], even if the app is in the background.
 */
class SecureClipboard(private val context: Context) {
    private val cm = context.getSystemService(ClipboardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copy(label: String, text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                // Android 13+ hides the copied text in the paste preview.
                putBoolean(
                    if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE
                    else "android.content.extra.IS_SENSITIVE",
                    true,
                )
            }
        }
        cm.setPrimaryClip(clip)

        pendingClear?.let(handler::removeCallbacks)
        pendingClear = null
        if (sensitive) {
            val r = Runnable { clearIfStillOurs(text) }
            pendingClear = r
            handler.postDelayed(r, CLEAR_AFTER_MS)
        }

        // Android 13+ shows its own "Copied" bubble.
        if (Build.VERSION.SDK_INT < 33) {
            val msg = if (sensitive) "$label copied (clears in 30 s)" else "$label copied"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * For logging in to IRIS in Chrome via the keyboard's clipboard history (owner's idea):
     * copies the password first, then the username, so the newest item is the username and
     * the one before it is the password. Both are plain copies (a "sensitive" copy would be
     * left out of the keyboard history). The phone's clipboard is cleared after
     * [LOGIN_CLEAR_AFTER_MS]; the keyboard's own history must be cleared by the user.
     */
    fun copyLoginForKeyboard(username: String, password: String, then: () -> Unit) {
        pendingClear?.let(handler::removeCallbacks)
        pendingClear = null
        if (password.isNotEmpty()) cm.setPrimaryClip(ClipData.newPlainText("Password", password))
        handler.postDelayed({
            cm.setPrimaryClip(ClipData.newPlainText("Username", username))
            val r = Runnable { clearIfStillOurs(username) }
            pendingClear = r
            handler.postDelayed(r, LOGIN_CLEAR_AFTER_MS)
            Toast.makeText(
                context,
                if (password.isNotEmpty()) "Keyboard clipboard: 1st = username, 2nd = password. Clear it when done."
                else "Username copied (no password saved)",
                Toast.LENGTH_LONG,
            ).show()
            handler.postDelayed(then, 300)
        }, if (password.isNotEmpty()) 600 else 0)
    }

    private fun clearIfStillOurs(text: String) {
        pendingClear = null
        // In the background Android hides the clipboard from us (returns null);
        // then we clear it anyway, to be safe.
        val current = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current != null && current != text) return
        if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
        else cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    companion object {
        const val CLEAR_AFTER_MS = 30_000L
        const val LOGIN_CLEAR_AFTER_MS = 60_000L
    }
}
