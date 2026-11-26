package com.example.ui.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CashTransaction
import com.example.model.CoupleCashCategories
import com.example.model.Household
import com.example.model.MonthlyBudget
import com.example.model.SavingsTransaction
import com.example.model.TransactionType
import com.example.ui.components.VicoIncomeExpenseChart
import com.example.ui.components.VicoSavingsGrowthChart
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber
import com.example.util.CurrencyFormatter
import com.example.util.FinanceCalculator
import com.example.util.ReportExporter
import java.util.Calendar

enum class ReportPeriod(val label: String) {
    DAILY("Harian"),
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
    YEARLY("Tahunan")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    household: Household,
    transactions: List<CashTransaction>,
    savingsTransactions: List<SavingsTransaction>,
    budgets: List<MonthlyBudget>,
    selectedMonthKey: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var reportPeriod by remember { mutableStateOf(ReportPeriod.MONTHLY) }
    var recorderFilter by remember { mutableStateOf<String?>(null) }

    val periodTransactions = remember(transactions, reportPeriod, selectedMonthKey, recorderFilter) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yearPrefix = selectedMonthKey.take(4)

        transactions.filter { tx ->
            if (tx.deleted) return@filter false
            val matchPeriod = when (reportPeriod) {
                ReportPeriod.DAILY -> tx.transactionDateMillis >= todayStart
                ReportPeriod.WEEKLY -> tx.transactionDateMillis >= weekStart
                ReportPeriod.MONTHLY -> CurrencyFormatter.monthKeyFromMillis(tx.transactionDateMillis) == selectedMonthKey
                ReportPeriod.YEARLY -> CurrencyFormatter.monthKeyFromMillis(tx.transactionDateMillis).startsWith(yearPrefix)
            }
            val matchRecorder = recorderFilter == null || tx.recordedByRoleLabel == recorderFilter
            matchPeriod && matchRecorder
        }
    }

    val totalIncome = remember(periodTransactions) {
        periodTransactions.filter { it.parsedType == TransactionType.INCOME }.sumOf { it.amount }
    }
    val totalExpense = remember(periodTransactions) {
        periodTransactions.filter { it.parsedType == TransactionType.EXPENSE }.sumOf { it.amount }
    }
    val totalSavingsDeposit = remember(periodTransactions) {
        periodTransactions.filter { it.parsedType == TransactionType.SAVINGS_DEPOSIT }.sumOf { it.amount }
    }

    val categoryBreakdown = remember(periodTransactions) {
        FinanceCalculator.expenseByCategory(periodTransactions)
    }
    val recorderBreakdown = remember(periodTransactions) {
        FinanceCalculator.breakdownByRecorder(periodTransactions)
    }
    val sixMonthSeries = remember(transactions, selectedMonthKey) {
        FinanceCalculator.lastSixMonthsSeries(transactions, selectedMonthKey)
    }
    val savingsGrowth = remember(savingsTransactions, selectedMonthKey) {
        FinanceCalculator.savingsGrowthSeries(savingsTransactions, selectedMonthKey)
    }
    val budgetEvaluations = remember(transactions, budgets, selectedMonthKey) {
        FinanceCalculator.evaluateBudgets(transactions, budgets, selectedMonthKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan Keuangan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Period & Recorder Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportPeriod.entries.forEach { p ->
                            FilterChip(
                                selected = reportPeriod == p,
                                onClick = { reportPeriod = p },
                                label = { Text(p.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = recorderFilter == null,
                            onClick = { recorderFilter = null },
                            label = { Text("Suami & Istri") }
                        )
                        listOf("Suami", "Istri").forEach { role ->
                            FilterChip(
                                selected = recorderFilter == role,
                                onClick = { recorderFilter = role },
                                label = { Text("Hanya $role") }
                            )
                        }
                    }
                }
            }

            // Summary Card + Export PDF / CSV
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Ringkasan Laporan (${reportPeriod.label})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        ReportSummaryRow("Total Pemasukan", totalIncome, IncomeGreen)
                        ReportSummaryRow("Total Pengeluaran (Konsumtif)", totalExpense, ExpenseRed)
                        ReportSummaryRow("Alokasi Setoran Tabungan", totalSavingsDeposit, SavingsAmber)
                        ReportSummaryRow("Surplus / Defisit Kas", totalIncome - totalExpense, EmeraldPrimary)

                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    ReportExporter.exportPdfAndShare(
                                        context = context,
                                        householdName = household.name,
                                        periodTitle = "${reportPeriod.label} (${CurrencyFormatter.formatMonthLabel(selectedMonthKey)})",
                                        totalIncome = totalIncome,
                                        totalExpense = totalExpense,
                                        totalSavingsDeposit = totalSavingsDeposit,
                                        transactions = periodTransactions
                                    ).onSuccess {
                                        onMessage("Laporan PDF siap dibagikan")
                                    }.onFailure {
                                        onMessage("Gagal membuat PDF: ${it.localizedMessage}")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_pdf_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ekspor PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    ReportExporter.exportCsvAndShare(
                                        context = context,
                                        householdName = household.name,
                                        periodTitle = "${reportPeriod.label} (${CurrencyFormatter.formatMonthLabel(selectedMonthKey)})",
                                        transactions = periodTransactions
                                    ).onSuccess {
                                        onMessage("Laporan CSV siap dibagikan")
                                    }.onFailure {
                                        onMessage("Gagal membuat CSV: ${it.localizedMessage}")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_csv_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ekspor CSV")
                            }
                        }
                    }
                }
            }

            // Chart 1: Income vs Expense (Vico)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pemasukan vs Pengeluaran (6 Bulan)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        VicoIncomeExpenseChart(points = sixMonthSeries)
                    }
                }
            }

            // Chart 2: Savings Growth (Vico)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pertumbuhan Tabungan (6 Bulan)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        VicoSavingsGrowthChart(points = savingsGrowth)
                    }
                }
            }

            // Expense by Category Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Pengeluaran per Kategori", style = MaterialTheme.typography.titleMedium)
                        if (categoryBreakdown.isEmpty()) {
                            Text(
                                "Belum ada pengeluaran pada periode ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            categoryBreakdown.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.category, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${CurrencyFormatter.formatRupiah(item.amount)} (${(item.percentage * 100).toInt()}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ExpenseRed
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { item.percentage },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = ExpenseRed,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Budget vs Realization Comparison
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Perbandingan Anggaran & Realisasi", style = MaterialTheme.typography.titleMedium)
                        if (budgetEvaluations.isEmpty()) {
                            Text(
                                "Belum ada anggaran yang diatur untuk bulan ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            budgetEvaluations.forEach { ev ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            CoupleCashCategories.budgetDisplayName(ev.budget.category),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "${CurrencyFormatter.formatRupiah(ev.spentAmount)} / ${CurrencyFormatter.formatRupiah(ev.budget.limitAmount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (ev.isExceeded) ExpenseRed else EmeraldPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (ev.percentUsed / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = if (ev.isExceeded) ExpenseRed else EmeraldPrimary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recorder Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Ringkasan Berdasarkan Pencatat", style = MaterialTheme.typography.titleMedium)
                        if (recorderBreakdown.isEmpty()) {
                            Text(
                                "Belum ada aktivitas transaksi pada periode ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            recorderBreakdown.forEach { rec ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(rec.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Masuk: ${CurrencyFormatter.formatRupiah(rec.incomeTotal)} • Keluar: ${CurrencyFormatter.formatRupiah(rec.expenseTotal)} • Tabung: ${CurrencyFormatter.formatRupiah(rec.savingsTotal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSummaryRow(
    label: String,
    amount: Long,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = CurrencyFormatter.formatRupiah(amount),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
