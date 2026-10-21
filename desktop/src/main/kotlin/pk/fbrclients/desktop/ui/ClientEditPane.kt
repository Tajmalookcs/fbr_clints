package pk.fbrclients.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.util.DashTransformation
import pk.fbrclients.app.util.Formats
import pk.fbrclients.desktop.AppState

/** Add ([id] 0) or edit a client, with the same checks as the phone. */
@Composable
fun ClientEditPane(state: AppState, id: Long) {
    val original = remember(id) { state.store.data.clients.firstOrNull { it.id == id } }
    val isNew = original == null
    var name by remember(id) { mutableStateOf(original?.name.orEmpty()) }
    var businessName by remember(id) { mutableStateOf(original?.businessName.orEmpty()) }
    var username by remember(id) { mutableStateOf(original?.cnic.orEmpty().filter { it.isLetterOrDigit() }) }
    var password by remember(id) { mutableStateOf(original?.irisPassword.orEmpty()) }
    var ntn by remember(id) { mutableStateOf(original?.ntn.orEmpty()) }
    var mobile by remember(id) { mutableStateOf(Formats.digitsOnly(original?.mobile.orEmpty())) }
    var email by remember(id) { mutableStateOf(original?.email.orEmpty()) }
    var address by remember(id) { mutableStateOf(original?.address.orEmpty()) }
    var notes by remember(id) { mutableStateOf(original?.notes.orEmpty()) }
    var reference by remember(id) { mutableStateOf(original?.reference.orEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var errors by remember(id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val e = mutableMapOf<String, String>()
        if (name.isBlank()) e["name"] = "Name is required"
        val normalized = Formats.normalizeUsername(username)
        if (normalized == null) e["username"] = "Enter a 13-digit CNIC, or a 7-character NTN for a company / AOP"
        else if (state.store.data.clients.any { it.cnic == normalized && it.id != original?.id }) e["username"] = "Another client already has this username"
        if (mobile.isNotEmpty() && !Formats.isValidMobile(mobile)) e["mobile"] = "Use 03XX-XXXXXXX"
        if (email.isNotBlank() && !Formats.isValidEmail(email)) e["email"] = "Not a valid email"
        errors = e
        if (e.isNotEmpty()) return
        val c = (original ?: Client(cnic = "", name = "")).copy(
            cnic = normalized!!,
            irisPassword = password,
            name = name.trim(),
            businessName = businessName.trim(),
            ntn = ntn.trim(),
            mobile = if (mobile.isEmpty()) "" else Formats.formatMobile(mobile),
            email = email.trim(),
            address = address.trim(),
            notes = notes.trim(),
            reference = reference.trim(),
        )
        val savedId = state.store.saveClient(c)
        state.editingClientId = null
        state.selectedClientId = savedId
        state.toast("Saved")
    }

    Column(Modifier.fillMaxSize()) {
        PageBar(if (isNew) "New client" else "Edit client", actions = {
            if (!isNew) TextButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete client") }
        })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Field("Name *", name, { name = it; errors = errors - "name" }, errors["name"])
            Field("Business Name", businessName, { businessName = it }, null)
            Field("Reference (who referred the client)", reference, { reference = it }, null)
            Field(
                "IRIS username * (CNIC or NTN)", username,
                { v -> username = v.filter { it.isLetterOrDigit() }.uppercase().take(Formats.CNIC_DIGITS); errors = errors - "username" },
                errors["username"],
                supporting = "CNIC 12345-1234567-1 · NTN 1234567 for companies / AOPs",
                transform = if (username.length > 7 && username.all { it.isDigit() }) DashTransformation(Formats.CNIC_DASHES) else VisualTransformation.None,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("IRIS Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } },
            )
            Field("NTN", ntn, { ntn = it }, null)
            Field(
                "Mobile", mobile, { mobile = Formats.digitsOnly(it).take(Formats.MOBILE_DIGITS); errors = errors - "mobile" }, errors["mobile"],
                supporting = "03XX-XXXXXXX", transform = DashTransformation(Formats.MOBILE_DASHES),
            )
            Field("Email", email, { email = it; errors = errors - "email" }, errors["email"])
            Field("Address", address, { address = it }, null, singleLine = false)
            Field("Notes", notes, { notes = it }, null, singleLine = false)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::save, modifier = Modifier.height(44.dp)) { Text("Save") }
                OutlinedButton(onClick = { state.editingClientId = null }, modifier = Modifier.height(44.dp)) { Text("Cancel") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${original.name}?") },
            text = { Text("This removes the client and all their Wealth Statements and bills. With sync on, they are removed from the phone too. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        state.store.deleteClient(original.id)
                        state.editingClientId = null
                        state.selectedClientId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    supporting: String? = null,
    transform: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        visualTransformation = transform,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
    )
}
