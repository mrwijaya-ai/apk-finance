package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CoupleCashRepository
import com.example.model.ActivityLog
import com.example.model.CashTransaction
import com.example.model.GoalStatus
import com.example.model.Household
import com.example.model.HouseholdMember
import com.example.model.InviteCode
import com.example.model.InviteCodePreview
import com.example.model.MonthlyBudget
import com.example.model.SavingsGoal
import com.example.model.SavingsTransaction
import com.example.model.SyncStatus
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.example.util.BudgetEvaluation
import com.example.util.CurrencyFormatter
import com.example.util.DashboardSummary
import com.example.util.FinanceCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface InvitePreviewState {
    data object Idle : InvitePreviewState
    data object Loading : InvitePreviewState
    data class Loaded(val preview: InviteCodePreview) : InvitePreviewState
    data class Error(val message: String) : InvitePreviewState
}

data class NotificationEvent(val id: Int, val title: String, val body: String)

@OptIn(ExperimentalCoroutinesApi::class)
class CoupleCashViewModel(
    private val repository: CoupleCashRepository,
    val currentUserId: String
) : ViewModel() {

    private companion object {
        const val TAG = "CoupleCashVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    private val _selectedMonthKey = MutableStateFlow(CurrencyFormatter.currentMonthKey())
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 8)
    val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()

    private val _invitePreviewState = MutableStateFlow<InvitePreviewState>(InvitePreviewState.Idle)
    val invitePreviewState: StateFlow<InvitePreviewState> = _invitePreviewState.asStateFlow()

    private val _generatedInvite = MutableStateFlow<InviteCode?>(null)
    val generatedInvite: StateFlow<InviteCode?> = _generatedInvite.asStateFlow()

    private var seenTransactionIds: Set<String>? = null
    private var seenCompletedGoalIds: Set<String>? = null
    private var alertedBudgetIds: MutableSet<String> = mutableSetOf()

    private fun <T> Flow<T>.toSafeUiState(initialValue: UiState<T> = UiState.Loading): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Stream error", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Gagal memuat data"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = initialValue
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toSafeUiState()

    val householdsState: StateFlow<UiState<List<Household>>> =
        repository.observeHouseholds(currentUserId).toSafeUiState()

    val activeHousehold: StateFlow<Household?> = combine(
        userProfileState,
        householdsState
    ) { profileState, hhState ->
        val households = (hhState as? UiState.Success)?.data ?: emptyList()
        val activeId = (profileState as? UiState.Success)?.data?.activeHouseholdId
        households.firstOrNull { it.id == activeId } ?: households.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val activeHouseholdIdFlow: Flow<String> = activeHousehold
        .map { it?.id ?: "" }
        .distinctUntilChanged()

    val members: StateFlow<List<HouseholdMember>> = activeHouseholdIdFlow
        .flatMapLatest { hhId ->
            if (hhId.isBlank()) flowOf(emptyList())
            else repository.observeHouseholdMembers(hhId, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val transactions: StateFlow<List<CashTransaction>> = activeHouseholdIdFlow
        .flatMapLatest { hhId ->
            if (hhId.isBlank()) flowOf(emptyList())
            else repository.observeTransactions(hhId, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val budgets: StateFlow<List<MonthlyBudget>> = activeHouseholdIdFlow
        .flatMapLatest { hhId ->
            if (hhId.isBlank()) flowOf(emptyList())
            else repository.observeBudgets(hhId, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val savingsGoals: StateFlow<List<SavingsGoal>> = activeHouseholdIdFlow
        .flatMapLatest { hhId ->
            if (hhId.isBlank()) flowOf(emptyList())
            else repository.observeSavingsGoals(hhId, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val savingsTransactions: StateFlow<List<SavingsTransaction>> = activeHouseholdIdFlow
        .flatMapLatest { hhId ->
            if (hhId.isBlank()) flowOf(emptyList())
            else repository.observeSavingsTransactions(hhId, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val activityLogs: StateFlow<List<ActivityLog>> = activeHouseholdIdFlow
        .flatMapLatest { hhId ->
            if (hhId.isBlank()) flowOf(emptyList())
            else repository.observeActivityLogs(hhId, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val syncStatus: StateFlow<SyncStatus> = activeHouseholdIdFlow
        .flatMapLatest { hhId ->
            repository.observeSyncStatus(hhId, currentUserId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SyncStatus.SYNCING)

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        transactions,
        savingsGoals,
        budgets,
        selectedMonthKey
    ) { txs, goals, bgs, mk ->
        FinanceCalculator.computeDashboardSummary(txs, goals, bgs, mk)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        FinanceCalculator.computeDashboardSummary(emptyList(), emptyList(), emptyList(), CurrencyFormatter.currentMonthKey())
    )

    val budgetEvaluations: StateFlow<List<BudgetEvaluation>> = combine(
        transactions,
        budgets,
        selectedMonthKey
    ) { txs, bgs, mk ->
        FinanceCalculator.evaluateBudgets(txs, bgs, mk)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureUserProfile()
                .onSuccess { repository.syncFcmToken() }
                .onFailure { e ->
                    _uiMessages.tryEmit(e.localizedMessage ?: "Gagal memuat profil pengguna")
                }
        }
        observeHouseholdMembershipSync()
        observeRealTimeNotificationTriggers()
    }

    private fun observeHouseholdMembershipSync() {
        viewModelScope.launch {
            activeHousehold
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.memberUids == b?.memberUids }
                .collect { hh ->
                    if (hh != null && hh.ownerId == currentUserId) {
                        repository.syncSubcollectionMemberUids(hh)
                    }
                }
        }
    }

    private fun observeRealTimeNotificationTriggers() {
        viewModelScope.launch {
            combine(userProfileState, transactions) { pState, txs ->
                Pair((pState as? UiState.Success)?.data, txs)
            }.collect { (profile, txs) ->
                if (profile == null) return@collect
                val ids = txs.map { it.id }.toSet()
                val prev = seenTransactionIds
                if (prev == null) {
                    seenTransactionIds = ids
                    return@collect
                }
                val newlyArrived = txs.filter { it.id !in prev && it.recordedByUid != currentUserId }
                seenTransactionIds = ids
                if (!profile.notifPartnerTx) return@collect
                for (tx in newlyArrived) {
                    val allow = when (tx.parsedType) {
                        TransactionType.INCOME -> profile.notifIncome
                        TransactionType.EXPENSE -> profile.notifExpense
                        TransactionType.SAVINGS_DEPOSIT, TransactionType.SAVINGS_WITHDRAWAL -> profile.notifSavings
                    }
                    if (allow) {
                        val title = when (tx.parsedType) {
                            TransactionType.INCOME -> "Pemasukan Baru dari Pasangan"
                            TransactionType.EXPENSE -> "Pengeluaran Baru dari Pasangan"
                            TransactionType.SAVINGS_DEPOSIT -> "Setoran Tabungan Baru"
                            TransactionType.SAVINGS_WITHDRAWAL -> "Penarikan Tabungan"
                        }
                        val body = "${tx.recordedByName} (${tx.recordedByRoleLabel}) mencatat ${tx.category}: ${CurrencyFormatter.formatRupiah(tx.amount)}"
                        _notificationEvents.tryEmit(NotificationEvent(tx.id.hashCode(), title, body))
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(userProfileState, budgetEvaluations) { pState, evals ->
                Pair((pState as? UiState.Success)?.data, evals)
            }.collect { (profile, evals) ->
                if (profile == null || !profile.notifBudget) return@collect
                for (eval in evals) {
                    val key = "${eval.budget.id}_${if (eval.isExceeded) "OVER" else "WARN"}"
                    if ((eval.isWarning || eval.isExceeded) && key !in alertedBudgetIds) {
                        alertedBudgetIds.add(key)
                        val title = if (eval.isExceeded) "Anggaran Terlampaui!" else "Anggaran Hampir Habis"
                        val cat = if (eval.budget.category == "TOTAL_BULANAN") "Total Bulanan" else eval.budget.category
                        val body = "Anggaran $cat terpakai ${eval.percentUsed}% (${CurrencyFormatter.formatRupiah(eval.spentAmount)})."
                        _notificationEvents.tryEmit(NotificationEvent(key.hashCode(), title, body))
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(userProfileState, savingsGoals) { pState, goals ->
                Pair((pState as? UiState.Success)?.data, goals)
            }.collect { (profile, goals) ->
                if (profile == null || !profile.notifGoal) return@collect
                val completed = goals.filter { it.parsedStatus == GoalStatus.COMPLETED }.map { it.id }.toSet()
                val prev = seenCompletedGoalIds
                if (prev == null) {
                    seenCompletedGoalIds = completed
                    return@collect
                }
                val newlyCompleted = goals.filter { it.id in completed && it.id !in prev }
                seenCompletedGoalIds = completed
                for (goal in newlyCompleted) {
                    _notificationEvents.tryEmit(
                        NotificationEvent(
                            goal.id.hashCode(),
                            "Target Tabungan Tercapai!",
                            "Target \"${goal.name}\" sebesar ${CurrencyFormatter.formatRupiah(goal.targetAmount)} telah tercapai."
                        )
                    )
                }
            }
        }
    }

    fun shiftMonth(delta: Int) {
        _selectedMonthKey.value = CurrencyFormatter.shiftMonthKey(_selectedMonthKey.value, delta)
    }

    fun currentRoleLabel(): String {
        val fromMember = members.value.firstOrNull { it.uid == currentUserId }?.partnerLabel
        val fromProfile = (userProfileState.value as? UiState.Success)?.data?.partnerRoleLabel
        return fromMember ?: fromProfile ?: "Suami"
    }

    fun emitMessage(message: String) {
        _uiMessages.tryEmit(message)
    }

    // --- Actions ---

    fun createHousehold(name: String, partnerLabel: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.createHousehold(name, partnerLabel)
                .onSuccess { _uiMessages.tryEmit("Buku Kas Bersama berhasil dibuat!") }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal membuat Buku Kas") }
            _isBusy.value = false
        }
    }

    fun generateInviteCode() {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.generateInviteCode(hh)
                .onSuccess {
                    _generatedInvite.value = it
                    _uiMessages.tryEmit("Kode undangan baru siap dibagikan: ${it.code}")
                }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal membuat kode undangan") }
            _isBusy.value = false
        }
    }

    fun revokeInviteCode(code: String) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.revokeInviteCode(hh.id, code)
                .onSuccess {
                    _generatedInvite.value = null
                    _uiMessages.tryEmit("Kode undangan dibatalkan")
                }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal membatalkan kode") }
            _isBusy.value = false
        }
    }

    fun previewInviteCode(code: String) {
        viewModelScope.launch {
            _invitePreviewState.value = InvitePreviewState.Loading
            repository.previewInviteCode(code)
                .onSuccess { _invitePreviewState.value = InvitePreviewState.Loaded(it) }
                .onFailure {
                    _invitePreviewState.value = InvitePreviewState.Error(
                        it.localizedMessage ?: "Kode undangan tidak valid"
                    )
                }
        }
    }

    fun resetInvitePreview() {
        _invitePreviewState.value = InvitePreviewState.Idle
    }

    fun joinHouseholdByCode(code: String, partnerLabel: String, onJoined: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.joinHouseholdByCode(code, partnerLabel)
                .onSuccess {
                    _invitePreviewState.value = InvitePreviewState.Idle
                    _uiMessages.tryEmit("Berhasil bergabung dengan Buku Kas Bersama!")
                    onJoined()
                }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal bergabung ke Buku Kas") }
            _isBusy.value = false
        }
    }

    fun removeOrLeavePartner(targetUid: String) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.removeOrLeavePartner(hh, targetUid)
                .onSuccess { _uiMessages.tryEmit("Keanggotaan Buku Kas diperbarui") }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal memperbarui anggota") }
            _isBusy.value = false
        }
    }

    fun updatePartnerRoleLabel(label: String) {
        viewModelScope.launch {
            repository.updateUserPartnerLabel(label, activeHousehold.value?.id)
                .onSuccess { _uiMessages.tryEmit("Peran diperbarui ke $label") }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal memperbarui peran") }
        }
    }

    fun updateNotificationPrefs(
        notifPartnerTx: Boolean,
        notifIncome: Boolean,
        notifExpense: Boolean,
        notifSavings: Boolean,
        notifBudget: Boolean,
        notifGoal: Boolean
    ) {
        viewModelScope.launch {
            repository.updateNotificationPreferences(
                notifPartnerTx, notifIncome, notifExpense, notifSavings, notifBudget, notifGoal
            ).onSuccess {
                _uiMessages.tryEmit("Pengaturan notifikasi disimpan")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Gagal menyimpan pengaturan")
            }
        }
    }

    fun addTransaction(
        type: TransactionType,
        amount: Long,
        category: String,
        source: String?,
        paymentMethod: String?,
        note: String?,
        receiptUri: Uri?,
        roleLabel: String,
        dateMillis: Long,
        clientMutationId: String,
        onDone: () -> Unit
    ) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.addTransaction(
                household = hh,
                type = type,
                amount = amount,
                category = category,
                source = source,
                paymentMethod = paymentMethod,
                note = note,
                receiptUri = receiptUri,
                roleLabel = roleLabel,
                transactionDateMillis = dateMillis,
                clientMutationId = clientMutationId
            ).onSuccess {
                _uiMessages.tryEmit("${type.label} berhasil dicatat!")
                onDone()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Gagal menyimpan transaksi")
            }
            _isBusy.value = false
        }
    }

    fun updateTransaction(
        existingTx: CashTransaction,
        amount: Long,
        category: String,
        source: String?,
        paymentMethod: String?,
        note: String?,
        receiptUri: Uri? = null,
        roleLabel: String,
        dateMillis: Long,
        onDone: () -> Unit
    ) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateTransaction(
                household = hh,
                existingTx = existingTx,
                amount = amount,
                category = category,
                source = source,
                paymentMethod = paymentMethod,
                note = note,
                receiptUri = receiptUri,
                roleLabel = roleLabel,
                transactionDateMillis = dateMillis
            ).onSuccess {
                _uiMessages.tryEmit("Transaksi diperbarui dan dicatat ke riwayat audit")
                onDone()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Gagal memperbarui transaksi")
            }
            _isBusy.value = false
        }
    }

    fun deleteTransaction(txId: String) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            repository.deleteTransaction(hh, txId, currentRoleLabel())
                .onSuccess { _uiMessages.tryEmit("Transaksi dihapus (tersimpan di riwayat audit & dapat dipulihkan)") }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal menghapus transaksi") }
        }
    }

    fun restoreTransaction(txId: String) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.restoreTransaction(hh, txId, currentRoleLabel())
                .onSuccess { _uiMessages.tryEmit("Transaksi berhasil dipulihkan!") }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal memulihkan transaksi") }
            _isBusy.value = false
        }
    }

    fun resetAllHouseholdData(onDone: () -> Unit) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.resetAllHouseholdData(hh, currentRoleLabel())
                .onSuccess {
                    _uiMessages.tryEmit("Seluruh data keuangan berhasil direset ke Rp0")
                    onDone()
                }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal mereset data keseluruhan") }
            _isBusy.value = false
        }
    }

    fun upsertBudget(category: String, limitAmount: Long, thresholdPercent: Long, onDone: () -> Unit) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertBudget(
                household = hh,
                monthKey = selectedMonthKey.value,
                category = category,
                limitAmount = limitAmount,
                alertThresholdPercent = thresholdPercent,
                roleLabel = currentRoleLabel()
            ).onSuccess {
                _uiMessages.tryEmit("Anggaran berhasil disimpan")
                onDone()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Gagal menyimpan anggaran")
            }
            _isBusy.value = false
        }
    }

    fun deleteBudget(budgetId: String) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            repository.deleteBudget(hh.id, budgetId)
                .onSuccess { _uiMessages.tryEmit("Anggaran dihapus") }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal menghapus anggaran") }
        }
    }

    fun createSavingsGoal(
        name: String,
        category: String,
        targetAmount: Long,
        initialBalance: Long = 0L,
        startDateMillis: Long,
        deadlineMillis: Long,
        onDone: () -> Unit
    ) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createSavingsGoal(
                household = hh,
                name = name,
                category = category,
                targetAmount = targetAmount,
                initialBalance = initialBalance,
                startDateMillis = startDateMillis,
                deadlineMillis = deadlineMillis,
                roleLabel = currentRoleLabel()
            ).onSuccess {
                _uiMessages.tryEmit("Target tabungan dibuat!")
                onDone()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Gagal membuat target tabungan")
            }
            _isBusy.value = false
        }
    }

    fun updateSavingsGoal(
        goal: SavingsGoal,
        newName: String,
        newCategory: String,
        newTargetAmount: Long,
        newDeadlineMillis: Long,
        onDone: () -> Unit
    ) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateSavingsGoal(hh.id, goal, newName, newCategory, newTargetAmount, newDeadlineMillis)
                .onSuccess {
                    _uiMessages.tryEmit("Target tabungan diperbarui")
                    onDone()
                }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal memperbarui target") }
            _isBusy.value = false
        }
    }

    fun deleteSavingsGoal(goalId: String) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            repository.deleteSavingsGoal(hh.id, goalId)
                .onSuccess { _uiMessages.tryEmit("Target tabungan dihapus") }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Gagal menghapus target") }
        }
    }

    fun recordSavingsMovement(
        goal: SavingsGoal,
        type: TransactionType,
        amount: Long,
        note: String?,
        dateMillis: Long,
        onDone: () -> Unit
    ) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.recordSavingsMovement(
                household = hh,
                goal = goal,
                type = type,
                amount = amount,
                note = note,
                roleLabel = currentRoleLabel(),
                dateMillis = dateMillis
            ).onSuccess {
                _uiMessages.tryEmit(
                    if (type == TransactionType.SAVINGS_DEPOSIT) "Setoran tabungan berhasil dicatat!"
                    else "Penarikan tabungan berhasil dicatat!"
                )
                onDone()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Gagal mencatat mutasi tabungan")
            }
            _isBusy.value = false
        }
    }

    fun recordOpeningSavingsBalance(
        goal: SavingsGoal,
        amount: Long,
        note: String?,
        dateMillis: Long,
        onDone: () -> Unit
    ) {
        val hh = activeHousehold.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.recordSavingsMovement(
                household = hh,
                goal = goal,
                type = TransactionType.SAVINGS_DEPOSIT,
                amount = amount,
                note = note,
                roleLabel = currentRoleLabel(),
                dateMillis = dateMillis,
                isOpeningBalance = true
            ).onSuccess {
                _uiMessages.tryEmit("Saldo tabungan yang sudah ada berhasil ditambahkan!")
                onDone()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Gagal mencatat saldo awal tabungan")
            }
            _isBusy.value = false
        }
    }
}
