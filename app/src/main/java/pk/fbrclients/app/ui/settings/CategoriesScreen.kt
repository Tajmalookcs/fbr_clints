package pk.fbrclients.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pk.fbrclients.app.data.AssetGroup
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.PropertySubType
import pk.fbrclients.app.data.WealthDao
import pk.fbrclients.app.ui.clients.FbrTopBar
import pk.fbrclients.app.ui.components.ConfirmDeleteDialog
import pk.fbrclients.app.ui.components.MessageDialog
import pk.fbrclients.app.ui.components.TextInputDialog

/**
 * Manage categories and property sub-types. Built-in categories keep their
 * name and code, except ones whose code is still unknown (e.g. Industrial
 * Property). New categories can be added to any group except properties.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(dao: WealthDao, onBack: () -> Unit) {
    val categories by remember { dao.categories() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val subTypes by remember { dao.subTypes() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf<AssetGroup?>(null) }
    var addingSubTypeFor by remember { mutableStateOf<Category?>(null) }
    var deletingSubType by remember { mutableStateOf<PropertySubType?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { FbrTopBar("Categories", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Tap a category to add or change its IRIS code. Codes shown as “code ?” are still unknown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            for (g in AssetGroup.entries) {
                item(key = "g-${g.name}") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(g.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (g != AssetGroup.IMMOVABLE) {
                            TextButton(onClick = { adding = g }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Add")
                            }
                        }
                    }
                }
                val cats = categories.filter { it.group == g }
                for (c in cats) {
                    item(key = "c-${c.id}") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = c.editable || !c.builtIn) { editing = c }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text((c.code ?: "code ?") + "  " + c.name, style = MaterialTheme.typography.bodyLarge)
                            if (c.builtIn && !c.editable) {
                                Text("Built-in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (c.isProperty) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    subTypes.filter { it.categoryId == c.id }.forEach { s ->
                                        InputChip(
                                            selected = false,
                                            onClick = { deletingSubType = s },
                                            label = { Text(s.name) },
                                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove ${s.name}") },
                                        )
                                    }
                                    TextButton(onClick = { addingSubTypeFor = c }) { Text("+ Sub-type") }
                                }
                            }
                        }
                    }
                }
                if (cats.isEmpty()) {
                    item(key = "e-${g.name}") {
                        Text(
                            "None yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                item(key = "d-${g.name}") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            }
        }
    }

    adding?.let { g ->
        CategoryDialog(
            title = "New category",
            subtitle = g.title,
            initialCode = "", initialName = "",
            nameEditable = true, codeEditable = true, canDelete = false,
            onDismiss = { adding = null },
            onSave = { code, name ->
                adding = null
                scope.launch {
                    dao.insertCategory(Category(code = code, name = name, group = g, sortOrder = dao.maxSortOrder() + 10))
                }
            },
            onDelete = {},
        )
    }

    editing?.let { c ->
        CategoryDialog(
            title = "Edit category",
            subtitle = c.group.title,
            initialCode = c.code.orEmpty(), initialName = c.name,
            nameEditable = !c.builtIn, codeEditable = c.editable, canDelete = !c.builtIn,
            onDismiss = { editing = null },
            onSave = { code, name ->
                editing = null
                scope.launch { dao.updateCategory(c.copy(code = code, name = if (c.builtIn) c.name else name)) }
            },
            onDelete = {
                editing = null
                scope.launch {
                    val used = dao.itemsUsing(c.id)
                    if (used > 0) message = "“${c.name}” is used by $used item(s). Move or delete those items first."
                    else dao.deleteCategory(c)
                }
            },
        )
    }

    addingSubTypeFor?.let { c ->
        TextInputDialog(
            title = "New sub-type for ${c.propertyType}",
            label = "Sub-type name",
            onDismiss = { addingSubTypeFor = null },
            onConfirm = { name ->
                addingSubTypeFor = null
                scope.launch { dao.insertSubType(PropertySubType(categoryId = c.id, name = name)) }
            },
        )
    }

    deletingSubType?.let { s ->
        ConfirmDeleteDialog(
            title = "Remove “${s.name}”?",
            message = "It will no longer be offered in the list. Items that already use it keep their description.",
            onDismiss = { deletingSubType = null },
            onConfirm = {
                deletingSubType = null
                scope.launch { dao.deleteSubType(s) }
            },
        )
    }

    message?.let { MessageDialog("Can't delete", it, onDismiss = { message = null }) }
}

@Composable
private fun CategoryDialog(
    title: String,
    subtitle: String,
    initialCode: String,
    initialName: String,
    nameEditable: Boolean,
    codeEditable: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (code: String?, name: String) -> Unit,
    onDelete: () -> Unit,
) {
    var code by remember { mutableStateOf(initialCode) }
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(10) },
                    label = { Text("IRIS code") },
                    supportingText = { Text("Leave empty if you don't know it yet") },
                    enabled = codeEditable,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    enabled = nameEditable,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete category", fontWeight = FontWeight.Medium) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(code.ifBlank { null }, name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
