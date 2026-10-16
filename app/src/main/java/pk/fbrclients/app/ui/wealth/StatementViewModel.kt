package pk.fbrclients.app.ui.wealth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pk.fbrclients.app.data.Category
import pk.fbrclients.app.data.ClientDao
import pk.fbrclients.app.data.Reconciliation
import pk.fbrclients.app.data.WealthDao
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.WealthMath
import pk.fbrclients.app.data.WealthStatement
import pk.fbrclients.app.util.Formats

/** Net assets of the previous tax year's statement, when there is one. */
data class PrevYear(val year: Int, val netAssets: Long)

data class StatementUi(
    val statement: WealthStatement,
    val clientName: String,
    val categories: List<Category>,
    val items: List<WealthItem>,
    val prevYear: PrevYear?,
    val recon: Reconciliation,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatementViewModel(
    private val dao: WealthDao,
    clientDao: ClientDao,
    statementId: Long,
) : ViewModel() {

    private val statementFlow = dao.statement(statementId)

    private val prevFlow = statementFlow.flatMapLatest { s ->
        if (s == null) flowOf(null)
        else combine(dao.yearExists(s.clientId, s.taxYear - 1), dao.netAssetsOf(s.clientId, s.taxYear - 1)) { n, net ->
            if (n > 0) PrevYear(s.taxYear - 1, net ?: 0L) else null
        }
    }

    private val clientName = statementFlow.flatMapLatest { s ->
        if (s == null) flowOf("") else clientDao.byId(s.clientId).map { it?.name ?: "" }
    }

    val ui: StateFlow<StatementUi?> = combine(
        statementFlow, clientName, dao.categories(), dao.items(statementId), prevFlow,
    ) { s, name, cats, items, prev ->
        if (s == null) return@combine null
        val byId = cats.associateBy { it.id }
        val (assets, liabilities) = WealthMath.totals(items, byId)
        val previous = s.prevYearNetAssets ?: prev?.netAssets ?: 0L
        StatementUi(s, name, cats, items, prev, WealthMath.reconcile(assets, liabilities, previous, s))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- Editable reconciliation fields (raw digits) ----

    var incomeNormal by mutableStateOf("")
    var incomeExempt by mutableStateOf("")
    var incomeFinal by mutableStateOf("")
    var personalExpenses by mutableStateOf("")
    /** null = automatic from last year. */
    var prevOverride by mutableStateOf<String?>(null)
    var fieldsLoaded by mutableStateOf(false)
        private set

    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            val s = statementFlow.filterNotNull().first()
            incomeNormal = Formats.amountInput(s.incomeNormal)
            incomeExempt = Formats.amountInput(s.incomeExempt)
            incomeFinal = Formats.amountInput(s.incomeFinal)
            personalExpenses = Formats.amountInput(s.personalExpenses)
            prevOverride = s.prevYearNetAssets?.let { if (it < 0) "0" else Formats.amountInput(it) }
            fieldsLoaded = true
        }
    }

    /** Called after any field change; writes the fields straight to the database. */
    fun saveFields() {
        if (!fieldsLoaded) return
        val snapshot = ui.value?.statement ?: return
        val updated = snapshot.copy(
            incomeNormal = Formats.parseAmount(incomeNormal),
            incomeExempt = Formats.parseAmount(incomeExempt),
            incomeFinal = Formats.parseAmount(incomeFinal),
            personalExpenses = Formats.parseAmount(personalExpenses),
            prevYearNetAssets = prevOverride?.let(Formats::parseAmount),
        )
        viewModelScope.launch { saveLock.withLock { dao.updateStatement(updated) } }
    }

    fun overridePrevious(start: Long) {
        prevOverride = Formats.amountInput(start.coerceAtLeast(0))
        saveFields()
    }

    fun useAutomaticPrevious() {
        prevOverride = null
        saveFields()
    }

    fun delete(onDone: () -> Unit) {
        val s = ui.value?.statement ?: return
        viewModelScope.launch {
            dao.deleteStatement(s)
            onDone()
        }
    }
}
