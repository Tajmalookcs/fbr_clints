package pk.fbrclients.app.ui.wealth

import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.fbrclients.app.data.AssetGroup
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.ui.clients.FbrTopBar
import pk.fbrclients.app.ui.components.AmountField
import pk.fbrclients.app.ui.components.ConfirmDeleteDialog
import pk.fbrclients.app.util.Formats
import pk.fbrclients.app.util.SecureClipboard

@Composable
fun StatementScreen(
    vm: StatementViewModel,
    clipboard: SecureClipboard,
    onBack: () -> Unit,
    onAddItem: (categoryId: Long) -> Unit,
    onEditItem: (item: WealthItem) -> Unit,
    onDeleted: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            val u = ui
            FbrTopBar(
                title = if (u == null) "" else "TY ${u.statement.taxYear} · ${u.clientName}",
                onBack = onBack,
            ) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete this statement")
                }
            }
        },
    ) { padding ->
        val u = ui ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Assets", "Liabilities", "Reconciliation").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> ItemsTab(
                    groups = AssetGroup.entries.filter { !it.isLiability },
                    ui = u, clipboard = clipboard, onAddItem = onAddItem, onEditItem = onEditItem,
                    totalLabel = "7019  Total Assets", total = u.recon.totalAssets,
                )
                1 -> ItemsTab(
                    groups = listOf(AssetGroup.LIABILITY),
                    ui = u, clipboard = clipboard, onAddItem = onAddItem, onEditItem = onEditItem,
                    totalLabel = "7029  Total Liabilities", total = u.recon.totalLiabilities,
                )
                else -> ReconciliationTab(vm, u, clipboard)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete Tax Year ${ui?.statement?.taxYear}?",
            message = "All items in this Wealth Statement will be deleted. This cannot be undone.",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete(onDeleted) },
        )
    }
}

@Composable
private fun ItemsTab(
    groups: List<AssetGroup>,
    ui: StatementUi,
    clipboard: SecureClipboard,
    onAddItem: (Long) -> Unit,
    onEditItem: (WealthItem) -> Unit,
    totalLabel: String,
    total: Long,
) {
    val itemsByCat = remember(ui.items) { ui.items.groupBy { it.categoryId } }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        for (g in groups) {
            val cats = ui.categories.filter { it.group == g }
            item(key = "g-${g.name}") { GroupHeader(g, cats, onAddItem) }
            // Only categories that have items are shown under the group.
            for (c in cats) {
                val catItems = itemsByCat[c.id].orEmpty()
                if (catItems.isNotEmpty()) categoryBlock(c, catItems, clipboard, onEditItem)
            }
            item(key = "d-${g.name}") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
        }
        item(key = "total") {
            TotalRow(totalLabel, total, Modifier.padding(16.dp))
        }
    }
}

/** Group title with one Add button. With several types, Add opens a list to pick one. */
@Composable
private fun GroupHeader(g: AssetGroup, cats: List<Category>, onAddItem: (Long) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            g.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { if (cats.size == 1) onAddItem(cats[0].id) else menu = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Add")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (cats.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No types yet. Add them in Settings → Categories.") },
                        onClick = { menu = false },
                        enabled = false,
                    )
                }
                cats.forEach { c ->
                    DropdownMenuItem(
                        text = { Text((c.code ?: "code ?") + "  " + c.name) },
                        onClick = { menu = false; onAddItem(c.id) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.categoryBlock(
    c: Category,
    catItems: List<WealthItem>,
    clipboard: SecureClipboard,
    onEditItem: (WealthItem) -> Unit,
) {
    item(key = "c-${c.id}") {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (c.code ?: "code ?") + "  " + c.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Rs " + Formats.amount(catItems.sumOf { it.amount }),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(catItems, key = { "i-${it.id}" }) { i ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onEditItem(i) }
                .padding(start = 28.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(i.description, style = MaterialTheme.typography.bodyMedium)
                i.holderCnic?.let {
                    Text(
                        "Held by CNIC $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { clipboard.copy("CNIC", it) },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(Formats.amount(i.amount), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            TextButton(onClick = { clipboard.copy("Description", i.description) }) { Text("Copy") }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: Long, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = color)
            Text(
                "Rs " + Formats.amount(value),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = color,
            )
        }
    }
}

@Composable
private fun ValueRow(code: String, label: String, value: Long, clipboard: SecureClipboard, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null, color = color)
        }
        Text(
            Formats.amount(value),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            color = color,
            modifier = Modifier.clickable { clipboard.copy("Amount", value.toString()) }.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ReconciliationTab(vm: StatementViewModel, ui: StatementUi, clipboard: SecureClipboard) {
    val r = ui.recon
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Tap any amount to copy it.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ValueRow("7019", "Total Assets", r.totalAssets, clipboard)
        ValueRow("7029", "Total Liabilities", r.totalLiabilities, clipboard)
        ValueRow("703001", "Net Assets Current Year", r.netAssetsCurrent, clipboard, bold = true)

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (vm.prevOverride == null) {
            ValueRow("703002", "Net Assets Previous Year", r.netAssetsPrevious, clipboard)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ui.prevYear?.let { "Taken from Tax Year ${it.year}." } ?: "No statement for Tax Year ${ui.statement.taxYear - 1}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.overridePrevious(r.netAssetsPrevious) }) { Text("Edit") }
            }
        } else {
            AmountField(
                "703002  Net Assets Previous Year",
                vm.prevOverride ?: "",
                { vm.prevOverride = it; vm.saveFields() },
            )
            TextButton(onClick = vm::useAutomaticPrevious) {
                Text(ui.prevYear?.let { "Use Tax Year ${it.year} amount instead" } ?: "Use automatic amount")
            }
        }
        ValueRow("703003", "Increase / Decrease in Assets", r.increase, clipboard, bold = true)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Inflows", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        AmountField("7031  Income declared subject to normal tax", vm.incomeNormal, { vm.incomeNormal = it; vm.saveFields() })
        AmountField("7032  Income declared exempt from tax", vm.incomeExempt, { vm.incomeExempt = it; vm.saveFields() })
        AmountField(
            "7033  Income attributable to receipts subject to final/fixed tax",
            vm.incomeFinal, { vm.incomeFinal = it; vm.saveFields() },
        )
        ValueRow("7049", "Inflows", r.inflows, clipboard, bold = true)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Outflows", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        AmountField(
            "7099 / 7089 / 7087  Personal / household expenses",
            vm.personalExpenses, { vm.personalExpenses = it; vm.saveFields() },
        )

        Spacer(Modifier.height(8.dp))
        val red = r.unreconciled != 0L
        TotalRow(
            "703000  Unreconciled Amount",
            r.unreconciled,
            color = if (red) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        Text(
            if (red) "Inflows − Outflows − Increase should be 0." else "Balanced.",
            style = MaterialTheme.typography.labelMedium,
            color = if (red) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }
}
