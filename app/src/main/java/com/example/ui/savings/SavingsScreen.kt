package com.example.ui.savings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CoupleCashCategories
import com.example.model.GoalStatus
import com.example.model.SavingsGoal
import com.example.model.SavingsTransaction
import com.example.model.TransactionType
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RupiahAmountField
import com.example.ui.theme.DarkEmeraldPrimary
import com.example.ui.theme.DarkExpenseRed
import com.example.ui.theme.DarkIncomeGreen
import com.example.ui.theme.DarkIncomeGreenBg
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.SavingsAmber
import com.example.util.CurrencyFormatter

@Composable
fun SavingsScreen(
    savingsGoals: List<SavingsGoal>,
    savingsTransactions: List<SavingsTransaction>,
    totalSavings: Long,
    isBusy: Boolean,
    onCreateGoal: (name: String, category: String, target: Long, initialBalance: Long, startMillis: Long, deadlineMillis: Long, onDone: () -> Unit) -> Unit,
    onUpdateGoal: (goal: SavingsGoal, name: String, category: String, target: Long, deadlineMillis: Long, onDone: () -> Unit) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onRecordMovement: (goal: SavingsGoal, type: TransactionType, amount: Long, note: String?, dateMillis: Long, onDone: () -> Unit) -> Unit,
    onRecordOpeningBalance: (goal: SavingsGoal, amount: Long, note: String?, dateMillis: Long, onDone: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showOpeningBalanceDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var movementTarget by remember { mutableStateOf<Pair<SavingsGoal, TransactionType>?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tabungan Keluarga", style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("add_savings_goal_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Target Baru")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Total Seluruh Tabungan",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = CurrencyFormatter.formatRupiah(totalSavings),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Catatan: Setoran tabungan memindahkan dana dari saldo kas ke tabungan tanpa mengurangi anggaran belanja bulanan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (savingsGoals.isEmpty()) {
                                showCreateDialog = true
                            } else {
                                showOpeningBalanceDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("record_existing_savings_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (savingsGoals.isEmpty()) {
                                "Input Nominal Tabungan Sudah Ada"
                            } else {
                                "Catat Nominal yang Sudah Ada"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (savingsGoals.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Savings,
                    title = "Belum Ada Target Tabungan",
                    subtitle = "Mulai buat target Nikah, Dana Darurat, Beli Rumah, Liburan, atau Pendidikan Anak bersama pasangan.",
                    actionLabel = "Buat Target Pertama",
                    onAction = { showCreateDialog = true }
                )
            }
        } else {
            items(savingsGoals, key = { it.id }) { goal ->
                SavingsGoalCard(
                    goal = goal,
                    onDeposit = { movementTarget = goal to TransactionType.SAVINGS_DEPOSIT },
                    onWithdraw = { movementTarget = goal to TransactionType.SAVINGS_WITHDRAWAL },
                    onEdit = { editingGoal = goal },
                    onDelete = { deletingGoal = goal }
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Riwayat Setoran & Penarikan", style = MaterialTheme.typography.titleMedium)
        }

        if (savingsTransactions.isEmpty()) {
            item {
                Text(
                    text = "Belum ada riwayat setoran atau penarikan tabungan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(savingsTransactions.take(25), key = { it.id }) { stx ->
                val isDeposit = stx.parsedType == TransactionType.SAVINGS_DEPOSIT
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (isDeposit) "Setoran" else "Penarikan"} • ${stx.goalName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${stx.recordedByName} (${stx.recordedByRoleLabel}) • ${CurrencyFormatter.formatDateShort(stx.transactionDateMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!stx.note.isNullOrBlank()) {
                                Text(stx.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val isDark = isSystemInDarkTheme()
                        val amtColor = when {
                            isDeposit && isDark -> DarkIncomeGreen
                            isDeposit -> IncomeGreen
                            isDark -> DarkExpenseRed
                            else -> ExpenseRed
                        }
                        Text(
                            text = "${if (isDeposit) "+" else "-"}${CurrencyFormatter.formatRupiah(stx.amount)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = amtColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        SavingsGoalEditorDialog(
            initialGoal = null,
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onSave = { name, cat, target, initialBal, start, deadline ->
                onCreateGoal(name, cat, target, initialBal, start, deadline) { showCreateDialog = false }
            }
        )
    }

    if (showOpeningBalanceDialog && savingsGoals.isNotEmpty()) {
        ExistingSavingsBalanceDialog(
            goals = savingsGoals,
            isBusy = isBusy,
            onDismiss = { showOpeningBalanceDialog = false },
            onConfirm = { goal, amount, note, dateMillis ->
                onRecordOpeningBalance(goal, amount, note, dateMillis) { showOpeningBalanceDialog = false }
            }
        )
    }

    editingGoal?.let { goal ->
        SavingsGoalEditorDialog(
            initialGoal = goal,
            isBusy = isBusy,
            onDismiss = { editingGoal = null },
            onSave = { name, cat, target, _, _, deadline ->
                onUpdateGoal(goal, name, cat, target, deadline) { editingGoal = null }
            }
        )
    }

    deletingGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { deletingGoal = null },
            title = { Text(if (goal.currentAmount > 0L) "Saldo Tabungan Belum Kosong" else "Hapus Target Tabungan?") },
            text = {
                Text(
                    if (goal.currentAmount > 0L) {
                        "Target \"${goal.name}\" masih memiliki saldo ${CurrencyFormatter.formatRupiah(goal.currentAmount)}. Demi konsistensi akuntansi, tarik seluruh dana kembali ke Saldo Kas sebelum menghapus target."
                    } else {
                        "Target \"${goal.name}\" akan dihapus dari daftar target bersama."
                    }
                )
            },
            confirmButton = {
                if (goal.currentAmount == 0L) {
                    Button(onClick = {
                        onDeleteGoal(goal.id)
                        deletingGoal = null
                    }) { Text("Hapus") }
                } else {
                    Button(onClick = {
                        movementTarget = goal to TransactionType.SAVINGS_WITHDRAWAL
                        deletingGoal = null
                    }) { Text("Tarik ke Kas") }
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) { Text("Batal") }
            }
        )
    }

    movementTarget?.let { (goal, type) ->
        SavingsMovementDialog(
            goal = goal,
            type = type,
            isBusy = isBusy,
            onDismiss = { movementTarget = null },
            onConfirm = { amount, note, dateMillis ->
                onRecordMovement(goal, type, amount, note, dateMillis) { movementTarget = null }
            }
        )
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = goal.parsedStatus == GoalStatus.COMPLETED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium)
                        if (isCompleted) {
                            val isDark = isSystemInDarkTheme()
                            val badgeBg = if (isDark) DarkIncomeGreenBg else IncomeGreenBg
                            val badgeFg = if (isDark) DarkIncomeGreen else IncomeGreen
                            Spacer(Modifier.width(6.dp))
                            Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = badgeFg,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text("Tercapai", style = MaterialTheme.typography.labelSmall, color = badgeFg)
                                }
                            }
                        }
                    }
                    Text(
                        text = "${goal.category} • Tenggat ${CurrencyFormatter.formatDateShort(goal.deadlineMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit target", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus target", modifier = Modifier.size(20.dp))
                    }
                }
            }

            val isDarkCard = isSystemInDarkTheme()
            val accentPrimary = if (isDarkCard) DarkEmeraldPrimary else EmeraldPrimary
            val completedAccent = if (isDarkCard) DarkIncomeGreen else IncomeGreen
            LinearProgressIndicator(
                progress = { goal.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (isCompleted) completedAccent else accentPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${CurrencyFormatter.formatRupiah(goal.currentAmount)} / ${CurrencyFormatter.formatRupiah(goal.targetAmount)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${goal.progressPercent}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = accentPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDeposit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Setor")
                }
                OutlinedButton(
                    onClick = onWithdraw,
                    enabled = goal.currentAmount > 0L,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tarik")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavingsGoalEditorDialog(
    initialGoal: SavingsGoal?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, targetAmount: Long, initialBalance: Long, startMillis: Long, deadlineMillis: Long) -> Unit
) {
    var name by remember { mutableStateOf(initialGoal?.name ?: "") }
    var category by remember { mutableStateOf(initialGoal?.category ?: CoupleCashCategories.savingsCategories.first()) }
    var targetText by remember { mutableStateOf(initialGoal?.targetAmount?.toString() ?: "") }
    var initialBalanceText by remember { mutableStateOf("") }
    var startMillis by remember { mutableLongStateOf(initialGoal?.startDateMillis ?: System.currentTimeMillis()) }
    var deadlineMillis by remember {
        mutableLongStateOf(initialGoal?.deadlineMillis ?: (System.currentTimeMillis() + 180L * 24 * 60 * 60 * 1000))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialGoal == null) "Buat Target Tabungan" else "Edit Target Tabungan") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Nama Target") },
                    placeholder = { Text("Misal: Dana Darurat 6 Bulan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Tujuan Tabungan", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoupleCashCategories.savingsCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                RupiahAmountField(
                    valueText = targetText,
                    onValueChange = { targetText = it },
                    label = "Target Nominal"
                )
                if (initialGoal == null) {
                    RupiahAmountField(
                        valueText = initialBalanceText,
                        onValueChange = { initialBalanceText = it },
                        label = "Nominal yang Sudah Ada (Opsional)"
                    )
                    Text(
                        text = "Isi jika sudah memiliki tabungan sebelum memakai aplikasi. Tidak mengurangi saldo kas atau anggaran bulanan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DatePickerField(
                        label = "Tanggal Mulai",
                        selectedMillis = startMillis,
                        onDateSelected = { startMillis = it }
                    )
                }
                DatePickerField(
                    label = "Tenggat Waktu",
                    selectedMillis = deadlineMillis,
                    onDateSelected = { deadlineMillis = it }
                )
            }
        },
        confirmButton = {
            val targetAmount = targetText.toLongOrNull() ?: 0L
            val initialBalance = initialBalanceText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            Button(
                onClick = { onSave(name, category, targetAmount, initialBalance, startMillis, deadlineMillis) },
                enabled = !isBusy && name.isNotBlank() && targetAmount > 0L && deadlineMillis >= startMillis
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExistingSavingsBalanceDialog(
    goals: List<SavingsGoal>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (goal: SavingsGoal, amount: Long, note: String?, dateMillis: Long) -> Unit
) {
    var selectedGoalId by remember(goals) { mutableStateOf(goals.first().id) }
    val selectedGoal = goals.firstOrNull { it.id == selectedGoalId } ?: goals.first()
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val parsedAmount = amountText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Nominal yang Sudah Ada") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tambahkan tabungan yang sudah dikumpulkan sebelumnya ke target pilihan tanpa memotong Saldo Kas atau menaikkan Pemasukan Bulan Ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Pilih Target Tabungan", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    goals.forEach { goal ->
                        FilterChip(
                            selected = goal.id == selectedGoal.id,
                            onClick = { selectedGoalId = goal.id },
                            label = { Text(goal.name) }
                        )
                    }
                }
                Text(
                    text = "Saldo saat ini: ${CurrencyFormatter.formatRupiah(selectedGoal.currentAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RupiahAmountField(
                    valueText = amountText,
                    onValueChange = { amountText = it },
                    label = "Nominal yang Sudah Ada (Rp)"
                )
                DatePickerField(
                    label = "Tanggal Pencatatan",
                    selectedMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(300) },
                    label = { Text("Catatan (Opsional)") },
                    placeholder = { Text("Misal: Saldo tabungan dari rekening lama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedGoal, parsedAmount, noteText, dateMillis) },
                enabled = !isBusy && parsedAmount > 0L,
                modifier = Modifier.testTag("confirm_existing_savings_button")
            ) {
                Text("Simpan Nominal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun SavingsMovementDialog(
    goal: SavingsGoal,
    type: TransactionType,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, note: String?, dateMillis: Long) -> Unit
) {
    val isDeposit = type == TransactionType.SAVINGS_DEPOSIT
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val parsedAmount = amountText.toLongOrNull() ?: 0L
    val validWithdrawal = isDeposit || parsedAmount <= goal.currentAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isDeposit) "Setor ke ${goal.name}" else "Tarik dari ${goal.name}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Saldo terkumpul saat ini: ${CurrencyFormatter.formatRupiah(goal.currentAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RupiahAmountField(
                    valueText = amountText,
                    onValueChange = { amountText = it },
                    label = if (isDeposit) "Nominal Setoran" else "Nominal Penarikan"
                )
                if (!validWithdrawal) {
                    val isDark = isSystemInDarkTheme()
                    Text(
                        text = "Nominal penarikan melebihi saldo tabungan yang tersedia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) DarkExpenseRed else ExpenseRed
                    )
                }
                DatePickerField(
                    label = "Tanggal",
                    selectedMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(300) },
                    label = { Text("Catatan (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parsedAmount, noteText, dateMillis) },
                enabled = !isBusy && parsedAmount > 0L && validWithdrawal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDeposit) EmeraldPrimary else SavingsAmber
                )
            ) {
                Text(if (isDeposit) "Setor Sekarang" else "Tarik Dana")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
