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
import pk.fbrclients.app.data.AreaUnits
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.PropertySubType
import pk.fbrclients.app.data.WealthDao
import pk.fbrclients.app.data.WealthItem
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
    var areaUnit by mutableStateOf<String?>(null)
    var area by mutableStateOf("")
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
                areaUnit = it.areaUnit
                area = it.area.orEmpty()
                address = it.propertyAddress.orEmpty()
                holderCnic = Formats.digitsOnly(it.holderCnic.orEmpty())
            }
            loaded = true
        }
    }

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
        category?.propertyType.orEmpty(), subType.orEmpty(), area, areaUnit.orEmpty(), address,
    )

    fun save(onDone: () -> Unit) {
        val c = category ?: return
        val e = mutableMapOf<String, String>()
        if (c.isProperty) {
            if (subType.isNullOrBlank()) e["subType"] = "Choose a sub-type"
            if (areaUnit == null) e["unit"] = "Choose a unit"
            if (!Formats.isValidArea(area)) e["area"] = "A number, e.g. 1.38"
            if (address.isBlank()) e["address"] = "Property address is required"
        } else if (description.isBlank()) {
            e["description"] = "Description is required"
        }
        if (c.isHeldByOthers && holderCnic.isNotEmpty() && !Formats.isValidCnic(holderCnic)) {
            e["holderCnic"] = "Enter all 13 digits, or leave it empty"
        }
        errors = e
        if (e.isNotEmpty()) return

        val item = WealthItem(
            id = itemId ?: 0,
            statementId = statementId,
            categoryId = c.id,
            description = if (c.isProperty) generatedDescription() else description.trim(),
            amount = Formats.parseAmount(amount),
            subType = if (c.isProperty) subType else null,
            acquisitionDate = if (c.isProperty) acquisitionDate else null,
            areaUnit = if (c.isProperty) areaUnit else null,
            area = if (c.isProperty) area.trim() else null,
            propertyAddress = if (c.isProperty) address.trim() else null,
            holderCnic = if (c.isHeldByOthers && holderCnic.isNotEmpty()) Formats.formatCnic(holderCnic) else null,
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = vm.area,
                        onValueChange = { v -> vm.area = v.filter { it.isDigit() || it == '.' }.take(14) },
                        label = { Text("Land area *") },
                        isError = vm.errors["area"] != null,
                        supportingText = vm.errors["area"]?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Dropdown(
                        label = "Unit *",
                        options = AreaUnits.ALL,
                        selected = vm.areaUnit,
                        optionLabel = { it },
                        onSelect = { vm.areaUnit = it },
                        error = vm.errors["unit"],
                        modifier = Modifier.weight(1.2f),
                    )
                }
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
