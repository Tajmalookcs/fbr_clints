package pk.fbrclients.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import pk.fbrclients.app.util.AmountTransformation
import pk.fbrclients.app.util.Formats

/** Whole-rupee field: digits only, shown as 1,234,567. [digits] is the raw text. */
@Composable
fun AmountField(
    label: String,
    digits: String,
    onDigitsChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    supporting: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = digits,
        onValueChange = { onDigitsChange(Formats.digitsOnly(it).trimStart('0').take(Formats.AMOUNT_MAX_DIGITS)) },
        label = { Text(label) },
        placeholder = { Text("0") },
        prefix = { Text("Rs ") },
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = AmountTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.End),
        modifier = modifier,
    )
}

/** A read-only dropdown. [extraOption] adds a last entry such as "+ Add sub-type…". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    error: String? = null,
    supporting: String? = null,
    extraOption: String? = null,
    onExtraOption: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = (error ?: supporting)?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); expanded = false })
            }
            if (extraOption != null) {
                DropdownMenuItem(text = { Text(extraOption) }, onClick = { expanded = false; onExtraOption() })
            }
        }
    }
}
