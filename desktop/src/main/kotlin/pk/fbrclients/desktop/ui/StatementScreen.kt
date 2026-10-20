package pk.fbrclients.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.fbrclients.app.data.AssetGroup
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.WealthMath
import pk.fbrclients.app.data.WealthStatement
import pk.fbrclients.app.ui.components.AmountField
import pk.fbrclients.app.ui.components.ConfirmDeleteDialog
import pk.fbrclients.app.util.Formats
import pk.fbrclients.desktop.AppState
import pk.fbrclients.desktop.Screen
import pk.fbrclients.desktop.platform.Clipboard

/** What the item dialog is editing: a new item in a category, or an existing item. */
private data class ItemTarget(val categoryId: Long, val item: WealthItem?)

@Composable
fun StatementScreen(state: AppState, statementId: Long) {
    val data = state.store.data
    val s = data.statements.firstOrNull { it.id == statementId }
    if (s == null) {
        // Deleted (here or by sync).
        LaunchedEffect(Unit) { state.screen = Screen.Clients }
        return
    }
    val client = data.clients.firstOrNull { it.id == s.clientId }
    val cats = data.categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val byId = cats.associateBy { it.id }
    val items = data.items.filter { it.statementId == s.id }.sortedBy { it.id }
    val (assets, liabilities) = WealthMath.totals(items, byId)
    val prevStatement = data.statements.firstOrNull { it.clientId == s.clientId && it.taxYear == s.taxYear - 1 }
    val prevNet = prevStatement?.let { p ->
        val (a, l) = WealthMath.totals(data.items.filter { it.statementId == p.id }, byId)
        a - l
    }
    val recon = WealthMath.reconcile(assets, liabilities, s.prevYearNetAssets ?: prevNet ?: 0L, s)

    var tab by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<ItemTarget?>(null) }

    fun copy(label: String, text: String) {
        Clipboard.copy(text)
        state.toast("$label copied")
    }

    Column(Modifier.fillMaxSize()) {
        PageBar(
            "TY ${s.taxYear} · ${client?.name.orEmpty()}",
            leading = {
                IconButton(onClick = { state.screen = Screen.Clients; state.selectedClientId = s.clientId }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete this statement")
                }
            },
        )
        TabRow(selectedTabIndex = tab) {
            listOf("Assets", "Liabilities", "Reconciliation").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 8.dp).widthIn(max = 1000.dp)) {
                when (tab) {
                    0 -> ItemsTab(AssetGroup.entries.filter { !it.isLiability }, cats, items, ::copy, { target = ItemTarget(it, null) }, { target = ItemTarget(it.categoryId, it) },
                        "7019  Total Assets", recon.totalAssets)
                    1 -> ItemsTab(listOf(AssetGroup.LIABILITY), cats, items, ::copy, { target = ItemTarget(it, null) }, { target = ItemTarget(it.categoryId, it) },
                        "7029  Total Liabilities", recon.totalLiabilities)
                    else -> ReconciliationTab(state, s, recon, prevStatement?.taxYear, ::copy)
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }

    target?.let { t ->
        ItemEditDialog(state, statementId = s.id, startCategoryId = t.categoryId, existing = t.item, onClose = { target = null })
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete Tax Year ${s.taxYear}?",
            message = "All items in this Wealth Statement will be deleted. This cannot be undone.",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                state.store.deleteStatement(s.id)
                state.selectedClientId = s.clientId
                state.screen = Screen.Clients
            },
        )
    }
}

@Composable
private fun ItemsTab(
    groups: List<AssetGroup>,
    cats: List<Category>,
    items: List<WealthItem>,
    copy: (String, String) -> Unit,
    onAdd: (categoryId: Long) -> Unit,
    onEdit: (WealthItem) -> Unit,
    totalLabel: String,
    total: Long,
) {
    val byCat = items.groupBy { it.categoryId }
    for (g in groups) {
        val gc = cats.filter { it.group == g }
        GroupHeader(g, gc, onAdd)
        for (c in gc) {
            val ci = byCat[c.id].orEmpty()
            if (ci.isEmpty()) continue
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((c.code ?: "code ?") + "  " + c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Rs " + Formats.amount(ci.sumOf { it.amount }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (i in ci) {
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(i) }.padding(start = 36.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(i.description, style = MaterialTheme.typography.bodyMedium)
                        i.holderCnic?.let {
                            Text("Held by CNIC $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { copy("CNIC", it) })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(Formats.amount(i.amount), fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { copy("Amount", i.amount.toString()) })
                    TextButton(onClick = { copy("Description", i.description) }) { Text("Copy") }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
    TotalRow(totalLabel, total, Modifier.padding(16.dp))
}

@Composable
private fun GroupHeader(g: AssetGroup, cats: List<Category>, onAdd: (Long) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(g.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { if (cats.size == 1) onAdd(cats[0].id) else menu = true }) {
                Icon(Icons.Filled.Add, null, Modifier.padding(end = 4.dp)); Text("Add")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (cats.isEmpty()) DropdownMenuItem(text = { Text("No types yet. Add them in Settings → Categories.") }, onClick = { menu = false }, enabled = false)
                cats.forEach { c ->
                    DropdownMenuItem(text = { Text((c.code ?: "code ?") + "  " + c.name) }, onClick = { menu = false; onAdd(c.id) })
                }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: Long, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = color)
            Text("Rs " + Formats.amount(value), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = color)
        }
    }
}

@Composable
private fun ValueRow(code: String, label: String, value: Long, copy: (String, String) -> Unit, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null, color = color)
        }
        Text(
            Formats.amount(value), fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.SemiBold else null, color = color,
            modifier = Modifier.clickable { copy("Amount", value.toString()) }.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ReconciliationTab(
    state: AppState,
    s: WealthStatement,
    r: pk.fbrclients.app.data.Reconciliation,
    prevYear: Int?,
    copy: (String, String) -> Unit,
) {
    // Fields as typed (digits); saved on every change.
    var incomeNormal by remember(s.id) { mutableStateOf(Formats.amountInput(s.incomeNormal)) }
    var incomeExempt by remember(s.id) { mutableStateOf(Formats.amountInput(s.incomeExempt)) }
    var incomeFinal by remember(s.id) { mutableStateOf(Formats.amountInput(s.incomeFinal)) }
    var expenses by remember(s.id) { mutableStateOf(Formats.amountInput(s.personalExpenses)) }
    var prevOverride by remember(s.id) { mutableStateOf(s.prevYearNetAssets?.let { if (it < 0) "0" else Formats.amountInput(it) }) }

    fun save() {
        val cur = state.store.data.statements.firstOrNull { it.id == s.id } ?: return
        state.store.saveStatement(
            cur.copy(
                incomeNormal = Formats.parseAmount(incomeNormal),
                incomeExempt = Formats.parseAmount(incomeExempt),
                incomeFinal = Formats.parseAmount(incomeFinal),
                personalExpenses = Formats.parseAmount(expenses),
                prevYearNetAssets = prevOverride?.let(Formats::parseAmount),
            ),
        )
    }

    Column(Modifier.padding(horizontal = 20.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Hint("Click any amount to copy it.")
        ValueRow("7019", "Total Assets", r.totalAssets, copy)
        ValueRow("7029", "Total Liabilities", r.totalLiabilities, copy)
        ValueRow("703001", "Net Assets Current Year", r.netAssetsCurrent, copy, bold = true)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (prevOverride == null) {
            ValueRow("703002", "Net Assets Previous Year", r.netAssetsPrevious, copy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint(prevYear?.let { "Taken from Tax Year $it." } ?: "No statement for Tax Year ${s.taxYear - 1}.", Modifier.weight(1f))
                TextButton(onClick = { prevOverride = Formats.amountInput(r.netAssetsPrevious.coerceAtLeast(0)); save() }) { Text("Edit") }
            }
        } else {
            AmountField("703002  Net Assets Previous Year", prevOverride ?: "", { prevOverride = it; save() })
            TextButton(onClick = { prevOverride = null; save() }) {
                Text(prevYear?.let { "Use Tax Year $it amount instead" } ?: "Use automatic amount")
            }
        }
        ValueRow("703003", "Increase / Decrease in Assets", r.increase, copy, bold = true)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("Inflows")
        AmountField("7031  Income declared subject to normal tax", incomeNormal, { incomeNormal = it; save() })
        AmountField("7032  Income declared exempt from tax", incomeExempt, { incomeExempt = it; save() })
        AmountField("7033  Income attributable to receipts subject to final/fixed tax", incomeFinal, { incomeFinal = it; save() })
        ValueRow("7049", "Inflows", r.inflows, copy, bold = true)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("Outflows")
        AmountField("7099 / 7089 / 7087  Personal / household expenses", expenses, { expenses = it; save() })
        Spacer(Modifier.height(8.dp))
        val red = r.unreconciled != 0L
        TotalRow("703000  Unreconciled Amount", r.unreconciled, color = if (red) MaterialTheme.colorScheme.error else Color.Unspecified)
        Text(
            if (red) "Inflows − Outflows − Increase should be 0." else "Balanced.",
            style = MaterialTheme.typography.labelMedium,
            color = if (red) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }
}
