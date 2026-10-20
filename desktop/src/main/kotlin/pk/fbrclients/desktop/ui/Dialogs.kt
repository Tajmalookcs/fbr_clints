package pk.fbrclients.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Windows' own Open / Save dialogs. */
object FileDialogs {
    fun open(parent: Frame?, title: String, extension: String? = null, startDir: File? = null): File? {
        val d = FileDialog(parent, title, FileDialog.LOAD)
        startDir?.takeIf { it.isDirectory }?.let { d.directory = it.absolutePath }
        if (extension != null) {
            d.file = "*.$extension"
            d.setFilenameFilter { _, name -> name.lowercase().endsWith(".$extension") }
        }
        d.isVisible = true
        return d.file?.let { File(d.directory, it) }
    }

    fun save(parent: Frame?, title: String, defaultName: String, startDir: File? = null): File? {
        val d = FileDialog(parent, title, FileDialog.SAVE)
        startDir?.takeIf { it.isDirectory }?.let { d.directory = it.absolutePath }
        d.file = defaultName
        d.isVisible = true
        val name = d.file ?: return null
        val ext = defaultName.substringAfterLast('.', "")
        val fixed = if (ext.isNotEmpty() && !name.lowercase().endsWith(".$ext")) "$name.$ext" else name
        return File(d.directory, fixed)
    }

    /** The Google Drive folder of Google Drive for Desktop, if it can be found. */
    fun googleDrive(): File? {
        val home = System.getProperty("user.home")
        val candidates = ('D'..'Z').map { File("$it:\\My Drive") } + File("$home\\Google Drive\\My Drive") + File("$home\\My Drive") +
            File("$home\\Google Drive") + listOf('G').map { File("$it:\\") }
        return candidates.firstOrNull { it.isDirectory }
    }
}

/** Asks for a password (twice when [needConfirm]). */
@Composable
fun PasswordDialog(
    title: String,
    text: String,
    confirmLabel: String,
    needConfirm: Boolean,
    minLength: Int = 8,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val tooShort = needConfirm && pw.length < minLength
    val mismatch = needConfirm && pw2.isNotEmpty() && pw != pw2
    val ok = pw.isNotEmpty() && !tooShort && (!needConfirm || pw == pw2)
    fun go() { if (ok) onConfirm(pw.toCharArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                val t = if (show) VisualTransformation.None else PasswordVisualTransformation()
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text("Password") },
                    supportingText = if (needConfirm) ({ Text("At least $minLength characters") }) else null,
                    visualTransformation = t, singleLine = true, modifier = Modifier.onEnter(::go),
                    trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
                )
                if (needConfirm) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, label = { Text("Type it again") },
                        isError = mismatch, supportingText = if (mismatch) ({ Text("Passwords don't match") }) else null,
                        visualTransformation = t, singleLine = true, modifier = Modifier.onEnter(::go),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = ::go, enabled = ok) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BusyDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.size(16.dp))
                Text(text)
            }
        },
    )
}
