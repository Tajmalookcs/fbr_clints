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
    }
}
