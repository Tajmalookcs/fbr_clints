package pk.fbrclients.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.LandArea
import pk.fbrclients.app.data.PropertySubType
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.propertyDescription
import pk.fbrclients.app.ui.components.AmountField
import pk.fbrclients.app.ui.components.ConfirmDeleteDialog
import pk.fbrclients.app.ui.components.Dropdown
import pk.fbrclients.app.ui.components.TextInputDialog
import pk.fbrclients.app.util.DashTransformation
import pk.fbrclients.app.util.Formats
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.platform.Clipboard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SHOW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/** Add or edit one Wealth Statement item; the same form and checks as the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditDialog(state: AppState, statementId: Long, startCategoryId: Long, existing: WealthItem?, onClose: () -> Unit) {
    val data = state.store.data
    val propertyCategories = data.categories.filter { it.isProperty }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    var category by remember { mutableStateOf(data.categories.firstOrNull { it.id == (existing?.categoryId ?: startCategoryId) }) }
    val c = category ?: return onClose()

    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.let { Formats.amountInput(it.amount) }.orEmpty()) }
    var subType by remember { mutableStateOf(existing?.subType) }
    var acquisitionDate by remember { mutableStateOf(existing?.acquisitionDate) }
    var acre by remember { mutableStateOf(existing?.areaAcre.orEmpty()) }
    var kanal by remember { mutableStateOf(existing?.areaKanal.orEmpty()) }
    var marla by remember { mutableStateOf(existing?.areaMarla.orEmpty()) }
    var sarsahi by remember { mutableStateOf(existing?.areaSarsahi.orEmpty()) }
    var sft by remember { mutableStateOf(existing?.areaSft.orEmpty()) }
    var sqm by remember { mutableStateOf(existing?.areaSqm.orEmpty()) }
    var address by remember { mutableStateOf(existing?.propertyAddress.orEmpty()) }
    var holderCnic by remember { mutableStateOf(Formats.digitsOnly(existing?.holderCnic.orEmpty())) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pickDate by remember { mutableStateOf(false) }
    var addSubType by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val parts = LandArea.Parts(acre, kanal, marla, sarsahi, sft, sqm)
    val total = LandArea.total(parts)
    val subs = data.subTypes.filter { it.categoryId == c.id }.sortedBy { it.name.lowercase() }
    val generated = propertyDescription(c.propertyType, subType.orEmpty(), total?.value.orEmpty(), total?.unit.orEmpty(), address)

    fun box(v: String): String? = if (!c.isProperty) null else LandArea.parse(v)?.takeIf { it.signum() > 0 }?.let { v.trim().trimEnd('.') }

    fun save() {
        val e = mutableMapOf<String, String>()
        if (c.isProperty) {
            if (subType.isNullOrBlank()) e["subType"] = "Choose a sub-type"
            if (total == null) e["area"] = "Enter the area in at least one box"
            if (address.isBlank()) e["address"] = "Property address is required"
        } else if (description.isBlank()) e["description"] = "Description is required"
        if (c.isHeldByOthers && holderCnic.isNotEmpty() && !Formats.isValidCnic(holderCnic)) e["holderCnic"] = "Enter all 13 digits, or leave it empty"
        errors = e
        if (e.isNotEmpty()) return
        val base = existing ?: WealthItem(statementId = statementId, categoryId = c.id, description = "", amount = 0)
        state.store.saveItem(
            base.copy(
                categoryId = c.id,
                description = if (c.isProperty) generated else description.trim(),
                amount = Formats.parseAmount(amount),
                subType = if (c.isProperty) subType else null,
                acquisitionDate = if (c.isProperty) acquisitionDate else null,
                areaUnit = if (c.isProperty) total?.unit else null,
                area = if (c.isProperty) total?.value else null,
                areaAcre = box(acre), areaKanal = box(kanal), areaMarla = box(marla),
                areaSarsahi = box(sarsahi), areaSft = box(sft), areaSqm = box(sqm),
                propertyAddress = if (c.isProperty) address.trim() else null,
                holderCnic = if (c.isHeldByOthers && holderCnic.isNotEmpty()) Formats.formatCnic(holderCnic) else null,
            ),
        )
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                when {
                    c.isProperty -> if (existing == null) "Add property" else "Edit property"
                    else -> (c.code?.let { "$it " } ?: "") + c.name
                },
            )
        },
        text = {
            Column(
                Modifier.width(640.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (c.isProperty) {
                    Dropdown(
                        label = "Property type *", options = propertyCategories, selected = c,
                        optionLabel = { it.propertyType + (it.code?.let { code -> "  ($code)" } ?: "") },
                        onSelect = { n: Category ->
                            if (n.id != c.id) {
                                category = n
                                if (data.subTypes.none { it.categoryId == n.id && it.name == subType }) subType = null
                            }
                        },
                    )
                    Dropdown(
                        label = "Property sub-type *", options = subs,
                        selected = subs.firstOrNull { it.name == subType } ?: subType?.let { PropertySubType(categoryId = c.id, name = it) },
                        optionLabel = { it.name },
                        onSelect = { subType = it.name; errors = errors - "subType" },
                        error = errors["subType"],
                        supporting = if (subs.isEmpty()) "None yet for ${c.propertyType}. Add one below." else null,
                        extraOption = "+ Add sub-type…",
                        onExtraOption = { addSubType = true },
                    )
                    OutlinedTextField(
                        value = acquisitionDate?.let { LocalDate.parse(it).format(SHOW_DATE) } ?: "",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Date of Acquisition") }, placeholder = { Text("Not set") },
                        trailingIcon = {
                            Row {
                                if (acquisitionDate != null) TextButton(onClick = { acquisitionDate = null }) { Text("Clear") }
                                TextButton(onClick = { pickDate = true }) { Text("Choose") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Land area *", style = MaterialTheme.typography.labelLarge)
                    val clear = { errors = errors - "area" }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AreaBox("Acre", acre, { acre = it; clear() }, Modifier.weight(1f))
                        AreaBox("Kanal", kanal, { kanal = it; clear() }, Modifier.weight(1f))
                        AreaBox("Marla", marla, { marla = it; clear() }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AreaBox("Sarsahi", sarsahi, { sarsahi = it; clear() }, Modifier.weight(1f))
                        AreaBox("Sft", sft, { sft = it; clear() }, Modifier.weight(1f))
                        AreaBox("Sq m", sqm, { sqm = it; clear() }, Modifier.weight(1f))
                    }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(total?.toString() ?: "—", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text(
                        errors["area"] ?: "1 Acre = 8 Kanal · 1 Kanal = 20 Marla · 1 Marla = 9 Sarsahi = 272 Sft",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errors["area"] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = address, onValueChange = { address = it; errors = errors - "address" },
                        label = { Text("Property Address *") },
                        supportingText = { Text(errors["address"] ?: "Plot / house / shop no., mohalla, city, share (e.g. 1/2 share), remarks") },
                        isError = errors["address"] != null, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    )
                    AmountField("Acquisition cost / value", amount, { amount = it }, supporting = "May be 0, e.g. inherited")
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Description for IRIS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(generated.ifBlank { "—" })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { Clipboard.copy(generated); state.toast("Description copied") }, enabled = generated.isNotBlank()) { Text("Copy") }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = description, onValueChange = { description = it; errors = errors - "description" },
                        label = { Text("Description *") },
                        isError = errors["description"] != null,
                        supportingText = errors["description"]?.let { { Text(it) } },
                        minLines = 2, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (description.isNotBlank()) TextButton(onClick = { Clipboard.copy(description.trim()); state.toast("Description copied") }) { Text("Copy") }
                        },
                    )
                    AmountField("Amount", amount, { amount = it }, supporting = "May be 0, e.g. inherited or dowry")
                    if (c.isHeldByOthers) {
                        OutlinedTextField(
                            value = holderCnic,
                            onValueChange = { v -> holderCnic = Formats.digitsOnly(v).take(Formats.CNIC_DIGITS); errors = errors - "holderCnic" },
                            label = { Text("CNIC of holder") },
                            isError = errors["holderCnic"] != null,
                            supportingText = { Text(errors["holderCnic"] ?: "Person the asset is held by (optional)") },
                            visualTransformation = DashTransformation(Formats.CNIC_DASHES),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete this item")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )

    if (pickDate) {
        val initial = acquisitionDate?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val ds = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { acquisitionDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = ds) }
    }

    if (addSubType) {
        TextInputDialog(
            title = "New sub-type for ${c.propertyType}",
            label = "Sub-type name",
            onDismiss = { addSubType = false },
            onConfirm = { name ->
                addSubType = false
                state.store.addSubType(c.id, name)
                subType = name
            },
        )
    }

    if (confirmDelete && existing != null) {
        ConfirmDeleteDialog(
            title = "Delete this item?",
            message = "It will be removed from this Wealth Statement.",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; state.store.deleteItem(existing.id); onClose() },
        )
    }
}

@Composable
private fun AreaBox(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (LandArea.isTypable(v)) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}
