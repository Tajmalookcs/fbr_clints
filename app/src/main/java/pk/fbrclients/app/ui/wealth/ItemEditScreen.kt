package pk.fbrclients.app.ui.wealth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.fbrclients.app.data.LandArea
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.PropertySubType
import pk.fbrclients.app.data.WealthDao
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.newUid
import pk.fbrclients.app.data.propertyDescription
import pk.fbrclients.app.ui.clients.FbrTopBar
import pk.fbrclients.app.ui.components.AmountField
import pk.fbrclients.app.ui.components.ConfirmDeleteDialog
import pk.fbrclients.app.ui.components.Dropdown
import pk.fbrclients.app.ui.components.TextInputDialog
import pk.fbrclients.app.util.DashTransformation
import pk.fbrclients.app.util.Formats
import pk.fbrclients.app.util.SecureClipboard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ItemEditViewModel(
    private val dao: WealthDao,
    private val statementId: Long,
    private val startCategoryId: Long,
    private val itemId: Long?,
) : ViewModel() {

    var loaded by mutableStateOf(false)
        private set
    var category by mutableStateOf<Category?>(null)
        private set
    var propertyCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    val allSubTypes = dao.subTypes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var original: WealthItem? = null

    var description by mutableStateOf("")
    var amount by mutableStateOf("")
    // Property form
    var subType by mutableStateOf<String?>(null)
    var acquisitionDate by mutableStateOf<String?>(null)
    // Land area boxes (text as typed); the Total is calculated from them.
    var areaAcre by mutableStateOf("")
    var areaKanal by mutableStateOf("")
    var areaMarla by mutableStateOf("")
    var areaSarsahi by mutableStateOf("")
    var areaSft by mutableStateOf("")
    var areaSqm by mutableStateOf("")
    var address by mutableStateOf("")
    /** 7014 only; digits, shown with dashes. */
    var holderCnic by mutableStateOf("")

    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    val isNew get() = itemId == null
    val isProperty get() = category?.isProperty == true

    init {
        viewModelScope.launch {
            val existing = itemId?.let { dao.item(it) }
            original = existing
            category = dao.category(existing?.categoryId ?: startCategoryId)
            propertyCategories = dao.propertyCategories()
            existing?.let {
                description = it.description
                amount = Formats.amountInput(it.amount)
                subType = it.subType
                acquisitionDate = it.acquisitionDate
                areaAcre = it.areaAcre.orEmpty()
                areaKanal = it.areaKanal.orEmpty()
                areaMarla = it.areaMarla.orEmpty()
                areaSarsahi = it.areaSarsahi.orEmpty()
                areaSft = it.areaSft.orEmpty()
                areaSqm = it.areaSqm.orEmpty()
                address = it.propertyAddress.orEmpty()
                holderCnic = Formats.digitsOnly(it.holderCnic.orEmpty())
            }
            loaded = true
        }
    }

    fun areaParts() = LandArea.Parts(areaAcre, areaKanal, areaMarla, areaSarsahi, areaSft, areaSqm)

    fun areaTotal(): LandArea.Total? = LandArea.total(areaParts())

    /** A box's text to save: null when empty or 0, or not a property. */
    private fun box(c: Category, v: String): String? =
        if (!c.isProperty) null else LandArea.parse(v)?.takeIf { it.signum() > 0 }?.let { v.trim().trimEnd('.') }

    fun clearError(key: String) {
        errors = errors - key
    }

    fun subTypesFor(c: Category?): List<PropertySubType> =
        if (c == null) emptyList() else allSubTypes.value.filter { it.categoryId == c.id }

    fun selectPropertyType(c: Category) {
        if (c.id == category?.id) return
        category = c
        // Sub-types belong to one property type, so clear it unless the name also exists there.
        if (subTypesFor(c).none { it.name == subType }) subType = null
        errors = errors - "type"
    }

    fun addSubType(name: String) {
        val c = category ?: return
        viewModelScope.launch {
            dao.insertSubType(PropertySubType(categoryId = c.id, name = name))
            subType = name
        }
    }

    /** The IRIS-style description for the property form. */
    fun generatedDescription(): String = propertyDescription(
        category?.propertyType.orEmpty(), subType.orEmpty(), areaTotal()?.value.orEmpty(), areaTotal()?.unit.orEmpty(), address,
    )

    fun save(onDone: () -> Unit) {
        val c = category ?: return
        val e = mutableMapOf<String, String>()
        if (c.isProperty) {
            if (subType.isNullOrBlank()) e["subType"] = "Choose a sub-type"
            if (areaTotal() == null) e["area"] = "Enter the area in at least one box"
            if (address.isBlank()) e["address"] = "Property address is required"
        } else if (description.isBlank()) {
            e["description"] = "Description is required"
        }
        if (c.isHeldByOthers && holderCnic.isNotEmpty() && !Formats.isValidCnic(holderCnic)) {
            e["holderCnic"] = "Enter all 13 digits, or leave it empty"
        }
        errors = e
        if (e.isNotEmpty()) return

        val total = areaTotal()
        val item = WealthItem(
            id = itemId ?: 0,
            statementId = statementId,
            categoryId = c.id,
            description = if (c.isProperty) generatedDescription() else description.trim(),
            amount = Formats.parseAmount(amount),
            subType = if (c.isProperty) subType else null,
            acquisitionDate = if (c.isProperty) acquisitionDate else null,
            areaUnit = if (c.isProperty) total?.unit else null,
            area = if (c.isProperty) total?.value else null,
            areaAcre = box(c, areaAcre), areaKanal = box(c, areaKanal), areaMarla = box(c, areaMarla),
            areaSarsahi = box(c, areaSarsahi), areaSft = box(c, areaSft), areaSqm = box(c, areaSqm),
            propertyAddress = if (c.isProperty) address.trim() else null,
            holderCnic = if (c.isHeldByOthers && holderCnic.isNotEmpty()) Formats.formatCnic(holderCnic) else null,
            uid = original?.uid ?: newUid(),
            updatedAt = original?.updatedAt ?: 0,
        )
        viewModelScope.launch {
            if (itemId == null) dao.insertItem(item) else dao.updateItem(item)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val i = original ?: return
        viewModelScope.launch {
            dao.deleteItem(i)
            onDone()
        }
    }
}

private val SHOW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(vm: ItemEditViewModel, clipboard: SecureClipboard, onBack: () -> Unit, onDone: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var addSubType by remember { mutableStateOf(false) }
    val allSubTypes by vm.allSubTypes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            val c = vm.category
            FbrTopBar(
                title = when {
                    c == null -> ""
                    c.isProperty -> if (vm.isNew) "Add property" else "Edit property"
                    else -> (c.code?.let { "$it " } ?: "") + c.name
                },
                onBack = onBack,
            ) {
                if (!vm.isNew) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete item") }
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        val c = vm.category ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (c.isProperty) {
                Dropdown(
                    label = "Property type *",
                    options = vm.propertyCategories,
                    selected = c,
                    optionLabel = { it.propertyType + (it.code?.let { code -> "  ($code)" } ?: "") },
                    onSelect = vm::selectPropertyType,
                )
                val subs = remember(allSubTypes, c.id) { allSubTypes.filter { it.categoryId == c.id } }
                Dropdown(
                    label = "Property sub-type *",
                    options = subs,
                    selected = subs.firstOrNull { it.name == vm.subType } ?: vm.subType?.let { PropertySubType(categoryId = c.id, name = it) },
                    optionLabel = { it.name },
                    onSelect = { vm.subType = it.name },
                    error = vm.errors["subType"],
                    supporting = if (subs.isEmpty()) "None yet for ${c.propertyType}. Add one below." else null,
                    extraOption = "+ Add sub-type…",
                    onExtraOption = { addSubType = true },
                )
                OutlinedTextField(
                    value = vm.acquisitionDate?.let { LocalDate.parse(it).format(SHOW_DATE) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date of Acquisition") },
                    placeholder = { Text("Not set") },
                    trailingIcon = {
                        Row {
                            if (vm.acquisitionDate != null) TextButton(onClick = { vm.acquisitionDate = null }) { Text("Clear") }
                            TextButton(onClick = { pickDate = true }) { Text("Choose") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AreaBoxes(vm)
                OutlinedTextField(
                    value = vm.address,
                    onValueChange = { vm.address = it },
                    label = { Text("Property Address *") },
                    supportingText = {
                        Text(vm.errors["address"] ?: "Plot / house / shop no., mohalla, city, share (e.g. 1/2 share), remarks")
                    },
                    isError = vm.errors["address"] != null,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                AmountField("Acquisition cost / value", vm.amount, { vm.amount = it }, supporting = "May be 0, e.g. inherited")

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Description for IRIS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val d = vm.generatedDescription()
                        Text(d.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { clipboard.copy("Description", d) }, enabled = d.isNotBlank()) { Text("Copy") }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = vm.description,
                    onValueChange = { vm.description = it },
                    label = { Text("Description *") },
                    isError = vm.errors["description"] != null,
                    supportingText = vm.errors["description"]?.let { { Text(it) } },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (vm.description.isNotBlank()) {
                            TextButton(onClick = { clipboard.copy("Description", vm.description.trim()) }) { Text("Copy") }
                        }
                    },
                )
                AmountField("Amount", vm.amount, { vm.amount = it }, supporting = "May be 0, e.g. inherited or dowry")
                if (c.isHeldByOthers) {
                    OutlinedTextField(
                        value = vm.holderCnic,
                        onValueChange = { v -> vm.holderCnic = Formats.digitsOnly(v).take(Formats.CNIC_DIGITS); vm.clearError("holderCnic") },
                        label = { Text("CNIC of holder") },
                        isError = vm.errors["holderCnic"] != null,
                        supportingText = { Text(vm.errors["holderCnic"] ?: "Person the asset is held by (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DashTransformation(Formats.CNIC_DASHES),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (Formats.isValidCnic(vm.holderCnic)) {
                                TextButton(onClick = { clipboard.copy("CNIC", Formats.formatCnic(vm.holderCnic)) }) { Text("Copy") }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = { vm.save(onDone) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        val initial = vm.acquisitionDate?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        vm.acquisitionDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (addSubType) {
        TextInputDialog(
            title = "New sub-type for ${vm.category?.propertyType}",
            label = "Sub-type name",
            onDismiss = { addSubType = false },
            onConfirm = { addSubType = false; vm.addSubType(it) },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete this item?",
            message = "It will be removed from this Wealth Statement.",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete(onDone) },
        )
    }
}

/**
 * | Acre    | Kanal | Marla |
 * | Sarsahi | Sft   | Sq m  |
 * Total (biggest unit that has a value; 2 decimals)
 */
@Composable
private fun AreaBoxes(vm: ItemEditViewModel) {
    val error = vm.errors["area"]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Land area *", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AreaBox("Acre", vm.areaAcre, { vm.areaAcre = it; vm.clearError("area") }, Modifier.weight(1f))
            AreaBox("Kanal", vm.areaKanal, { vm.areaKanal = it; vm.clearError("area") }, Modifier.weight(1f))
            AreaBox("Marla", vm.areaMarla, { vm.areaMarla = it; vm.clearError("area") }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AreaBox("Sarsahi", vm.areaSarsahi, { vm.areaSarsahi = it; vm.clearError("area") }, Modifier.weight(1f))
            AreaBox("Sft", vm.areaSft, { vm.areaSft = it; vm.clearError("area") }, Modifier.weight(1f))
            AreaBox("Sq m", vm.areaSqm, { vm.areaSqm = it; vm.clearError("area") }, Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(vm.areaTotal()?.toString() ?: "—", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            error ?: "1 Acre = 8 Kanal · 1 Kanal = 20 Marla · 1 Marla = 9 Sarsahi = 272 Sft",
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AreaBox(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (LandArea.isTypable(v)) onChange(v) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}
