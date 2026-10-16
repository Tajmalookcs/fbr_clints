package pk.fbrclients.app.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.ClientDao
import pk.fbrclients.app.util.DashTransformation
import pk.fbrclients.app.util.Formats

/**
 * Form state lives in a ViewModel (memory only), so it survives screen rotation
 * but the IRIS password is never written to Android's saved-state bundle.
 */
class ClientEditViewModel(private val dao: ClientDao, private val id: Long?) : ViewModel() {
    var loaded by mutableStateOf(id == null)
        private set
    private var original: Client? = null

    var name by mutableStateOf("")
    var businessName by mutableStateOf("")
    /** Letters and digits only (dashes are shown, not stored here). */
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var ntn by mutableStateOf("")
    var mobileDigits by mutableStateOf("")
    var email by mutableStateOf("")
    var address by mutableStateOf("")
    var notes by mutableStateOf("")

    var nameError by mutableStateOf<String?>(null)
    var usernameError by mutableStateOf<String?>(null)
    var mobileError by mutableStateOf<String?>(null)
    var emailError by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set

    val isNew get() = id == null

    init {
        if (id != null) viewModelScope.launch {
            dao.get(id)?.let { c ->
                original = c
                name = c.name
                businessName = c.businessName
                username = c.cnic.filter { it.isLetterOrDigit() }
                password = c.irisPassword
                ntn = c.ntn
                mobileDigits = Formats.digitsOnly(c.mobile)
                email = c.email
                address = c.address
                notes = c.notes
            }
            loaded = true
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        if (saving) return
        nameError = if (name.isBlank()) "Name is required" else null
        val normalized = Formats.normalizeUsername(username)
        usernameError = if (normalized == null) "Enter a 13-digit CNIC, or a 7-character NTN for a company / AOP" else null
        mobileError = if (mobileDigits.isNotEmpty() && !Formats.isValidMobile(mobileDigits)) "Use 03XX-XXXXXXX" else null
        emailError = if (email.isNotBlank() && !Formats.isValidEmail(email)) "Not a valid email" else null
        if (listOf(nameError, usernameError, mobileError, emailError).any { it != null }) return

        saving = true
        viewModelScope.launch {
            val cnic = normalized!!
            val other = dao.idForCnic(cnic)
            if (other != null && other != id) {
                usernameError = "Another client already has this username"
                saving = false
                return@launch
            }
            val c = Client(
                id = id ?: 0,
                cnic = cnic,
                irisPassword = password,
                name = name.trim(),
                businessName = businessName.trim(),
                ntn = ntn.trim(),
                mobile = if (mobileDigits.isEmpty()) "" else Formats.formatMobile(mobileDigits),
                email = email.trim(),
                address = address.trim(),
                notes = notes.trim(),
            )
            val savedId = if (id == null) dao.insert(c) else { dao.update(c); id }
            saving = false
            onSaved(savedId)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val c = original ?: return
        viewModelScope.launch {
            dao.delete(c)
            onDeleted()
        }
    }
}

@Composable
fun ClientEditScreen(
    vm: ClientEditViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            FbrTopBar(title = if (vm.isNew) "Add client" else "Edit client", onBack = onBack) {
                if (!vm.isNew) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete client")
                    }
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                "Name *", vm.name, { vm.name = it; vm.nameError = null }, vm.nameError,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            FormField(
                "Business Name", vm.businessName, { vm.businessName = it }, null,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            FormField(
                "IRIS username * (CNIC or NTN)", vm.username,
                { v -> vm.username = v.filter { it.isLetterOrDigit() }.uppercase().take(Formats.CNIC_DIGITS); vm.usernameError = null },
                vm.usernameError,
                supporting = "CNIC 12345-1234567-1 · NTN 1234567 for companies / AOPs",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                // All digits and longer than an NTN: show CNIC dashes as you type.
                transform = if (vm.username.length > 7 && vm.username.all { it.isDigit() }) DashTransformation(Formats.CNIC_DASHES) else VisualTransformation.None,
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text("IRIS Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
                },
            )
            FormField("NTN", vm.ntn, { vm.ntn = it }, null, keyboard = KeyboardOptions(keyboardType = KeyboardType.Text))
            FormField(
                "Mobile", vm.mobileDigits,
                { vm.mobileDigits = Formats.digitsOnly(it).take(Formats.MOBILE_DIGITS); vm.mobileError = null },
                vm.mobileError,
                supporting = "03XX-XXXXXXX",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
                transform = DashTransformation(Formats.MOBILE_DASHES),
            )
            FormField(
                "Email", vm.email, { vm.email = it; vm.emailError = null }, vm.emailError,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            FormField("Address", vm.address, { vm.address = it }, null, singleLine = false)
            FormField("Notes", vm.notes, { vm.notes = it }, null, singleLine = false)

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.save(onSaved) },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${vm.name}?") },
            text = { Text("This removes the client and all their Wealth Statements. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; vm.delete(onDeleted) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    supporting: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    transform: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = keyboard,
        visualTransformation = transform,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
    )
}
