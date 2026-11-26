package com.example.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.model.MonthlyBudget
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RupiahAmountField
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.theme.DarkExpenseRed
import com.example.ui.theme.DarkExpenseRedBg
import com.example.ui.theme.DarkSavingsAmber
import com.example.ui.theme.DarkSavingsAmberBg
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedBg
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber
import com.example.ui.theme.SavingsAmberBg
import com.example.util.BudgetEvaluation
import com.example.util.CurrencyFormatter
import kotlin.math.roundToLong

@Composable
fun BudgetScreen(
    selectedMonthKey: String,
    budgetEvaluations: List<BudgetEvaluation>,
    isBusy: Boolean,
    onShiftMonth: (Int) -> Unit,
    onSaveBudget: (category: String, limitAmount: Long, thresholdPercent: Long, onDone: () -> Unit) -> Unit,
    onDeleteBudget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingBudget by remember { mutableStateOf<MonthlyBudget?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

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
                Text("Anggaran Bulanan", style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("add_budget_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Atur")
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onShiftMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Bulan sebelumnya")
                    }
                    Text(
                        text = CurrencyFormatter.formatMonthLabel(selectedMonthKey),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { onShiftMonth(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Bulan berikutnya")
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pengeluaran suami dan istri digabung ke anggaran rumah tangga ini. Setoran tabungan tidak mengurangi batas anggaran.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (budgetEvaluations.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.AccountBalance,
                    title = "Belum Ada Anggaran",
                    subtitle = "Tentukan Anggaran Total Bulanan, Makanan, Transportasi, Belanja, atau Tagihan untuk bulan ini.",
                    actionLabel = "Buat Anggaran",
                    onAction = { showCreateDialog = true }
                )
            }
        } else {
            items(budgetEvaluations, key = { it.budget.id }) { eval ->
                BudgetCard(
                    eval = eval,
                    onEdit = { editingBudget = eval.budget },
                    onDelete = { onDeleteBudget(eval.budget.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        BudgetEditorDialog(
            initialBudget = null,
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onSave = { cat, limit, threshold ->
                onSaveBudget(cat, limit, threshold) { showCreateDialog = false }
            }
        )
    }

    editingBudget?.let { b ->
        BudgetEditorDialog(
            initialBudget = b,
            isBusy = isBusy,
            onDismiss = { editingBudget = null },
            onSave = { cat, limit, threshold ->
                onSaveBudget(cat, limit, threshold) { editingBudget = null }
            }
        )
    }
}

@Composable
private fun BudgetCard(
    eval: BudgetEvaluation,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val barColor = when {
        eval.isExceeded -> ExpenseRed
        eval.isWarning -> SavingsAmber
        else -> EmeraldPrimary
    }

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
                    Text(
                        text = CoupleCashCategories.budgetDisplayName(eval.budget.category),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Diatur oleh ${eval.budget.updatedByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit anggaran", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus anggaran", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (eval.isExceeded || eval.isWarning) {
                val isDark = isSystemInDarkTheme()
                val bannerBg = when {
                    eval.isExceeded && isDark -> DarkExpenseRedBg
                    eval.isExceeded -> ExpenseRedBg
                    isDark -> DarkSavingsAmberBg
                    else -> SavingsAmberBg
                }
                val bannerFg = when {
                    eval.isExceeded && isDark -> DarkExpenseRed
                    eval.isExceeded -> ExpenseRed
                    isDark -> DarkSavingsAmber
                    else -> SavingsAmber
                }
                Surface(
                    color = bannerBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = bannerFg,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (eval.isExceeded) {
                                "Anggaran terlampaui (${eval.percentUsed}%)!"
                            } else {
                                "Anggaran hampir habis (${eval.percentUsed}% terpakai)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = bannerFg
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { (eval.percentUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Anggaran Awal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.formatRupiah(eval.budget.limitAmount), style = MaterialTheme.typography.titleSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Terpakai", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.formatRupiah(eval.spentAmount), style = MaterialTheme.typography.titleSmall, color = barColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (eval.remainingAmount >= 0L) "Sisa" else "Defisit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatRupiah(eval.remainingAmount),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (eval.remainingAmount >= 0L) IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorDialog(
    initialBudget: MonthlyBudget?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (category: String, limitAmount: Long, thresholdPercent: Long) -> Unit
) {
    var selectedCategory by remember {
        mutableStateOf(initialBudget?.category ?: CoupleCashCategories.budgetCategories.first())
    }
    var limitText by remember { mutableStateOf(initialBudget?.limitAmount?.toString() ?: "") }
    var thresholdFloat by remember {
        mutableFloatStateOf((initialBudget?.alertThresholdPercent ?: 80L).toFloat())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialBudget == null) "Atur Anggaran Bulanan" else "Ubah Anggaran") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialBudget == null) {
                    Text("Kategori Anggaran", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CoupleCashCategories.budgetCategories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(CoupleCashCategories.budgetDisplayName(cat)) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = CoupleCashCategories.budgetDisplayName(selectedCategory),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                RupiahAmountField(
                    valueText = limitText,
                    onValueChange = { limitText = it },
                    label = "Batas Anggaran Bulanan"
                )

                Text(
                    text = "Peringatan saat pemakaian mencapai ${thresholdFloat.roundToLong()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = thresholdFloat,
                    onValueChange = { thresholdFloat = it },
                    valueRange = 50f..95f
                )
            }
        },
        confirmButton = {
            val amount = limitText.toLongOrNull() ?: 0L
            Button(
                onClick = { onSave(selectedCategory, amount, thresholdFloat.roundToLong()) },
                enabled = !isBusy && amount > 0L
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
