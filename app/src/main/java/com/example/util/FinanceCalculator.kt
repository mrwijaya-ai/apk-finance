package com.example.util

import com.example.model.ActivityLog
import com.example.model.CashTransaction
import com.example.model.CoupleCashCategories
import com.example.model.MonthlyBudget
import com.example.model.SavingsGoal
import com.example.model.SavingsTransaction
import com.example.model.TransactionType
import java.util.Calendar

data class BudgetEvaluation(
    val budget: MonthlyBudget,
    val spentAmount: Long,
    val remainingAmount: Long,
    val percentUsed: Int,
    val isWarning: Boolean,
    val isExceeded: Boolean
)

data class DashboardSummary(
    val monthKey: String,
    val monthlyIncome: Long,
    val monthlyExpense: Long,
    val cashBalance: Long,
    val totalSavings: Long,
    val totalAssets: Long,
    val totalMonthlyBudgetLimit: Long,
    val totalMonthlyBudgetRemaining: Long,
    val totalBudgetPercentUsed: Int,
    val lastTransactionLabel: String?
)

data class CategoryBreakdownItem(
    val category: String,
    val amount: Long,
    val percentage: Float
)

data class RecorderBreakdownItem(
    val label: String,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val savingsTotal: Long
)

data class PeriodChartPoint(
    val label: String,
    val income: Long,
    val expense: Long
)

object FinanceCalculator {

    private val tagihanSubCategories = setOf("Tagihan listrik", "Air", "Internet", "Cicilan")

    /**
     * Maps a transaction expense category to its monthly budget bucket.
     */
    fun matchesBudgetCategory(txCategory: String, budgetCategory: String): Boolean {
        if (budgetCategory == "TOTAL_BULANAN") return true
        if (budgetCategory == "Tagihan") {
            return txCategory == "Tagihan" || txCategory in tagihanSubCategories
        }
        return txCategory.equals(budgetCategory, ignoreCase = true)
    }

    private fun activeUniqueTransactions(transactions: List<CashTransaction>): List<CashTransaction> {
        val seenMutations = HashSet<String>()
        val result = ArrayList<CashTransaction>(transactions.size)
        for (tx in transactions) {
            if (tx.deleted) continue
            val mutKey = tx.clientMutationId.takeIf { it.isNotBlank() } ?: tx.id
            if (mutKey.isNotBlank() && !seenMutations.add(mutKey)) continue
            result.add(tx)
        }
        return result
    }

    /**
     * Computes monthly and all-time financial balances using double-entry accounting without double counting.
     *
     * Accounting invariant:
     * - Monthly Income = sum(INCOME in monthKey)
     * - Monthly Expense = sum(EXPENSE in monthKey) — SAVINGS_DEPOSIT / SAVINGS_WITHDRAWAL excluded!
     * - Cash Balance = All-time INCOME - All-time EXPENSE - All-time SAVINGS_DEPOSIT + All-time SAVINGS_WITHDRAWAL
     * - Savings Balance = All-time SAVINGS_DEPOSIT - All-time SAVINGS_WITHDRAWAL (sourced from real savings transactions)
     * - Total Assets = Cash Balance + Savings Balance
     */
    fun computeDashboardSummary(
        transactions: List<CashTransaction>,
        savingsGoals: List<SavingsGoal>,
        budgets: List<MonthlyBudget>,
        monthKey: String
    ): DashboardSummary {
        val activeTx = activeUniqueTransactions(transactions)
        var monthlyIncome = 0L
        var monthlyExpense = 0L
        var allTimeIncome = 0L
        var allTimeExpense = 0L
        var allTimeDeposits = 0L
        var allTimeWithdrawals = 0L

        for (tx in activeTx) {
            val txMonth = CurrencyFormatter.monthKeyFromMillis(tx.transactionDateMillis)
            when (tx.parsedType) {
                TransactionType.INCOME -> {
                    allTimeIncome += tx.amount
                    if (txMonth == monthKey && tx.category != CoupleCashCategories.OPENING_SAVINGS_CATEGORY) {
                        monthlyIncome += tx.amount
                    }
                }
                TransactionType.EXPENSE -> {
                    allTimeExpense += tx.amount
                    if (txMonth == monthKey) monthlyExpense += tx.amount
                }
                TransactionType.SAVINGS_DEPOSIT -> {
                    allTimeDeposits += tx.amount
                }
                TransactionType.SAVINGS_WITHDRAWAL -> {
                    allTimeWithdrawals += tx.amount
                }
            }
        }

        val cashBalance = allTimeIncome - allTimeExpense - allTimeDeposits + allTimeWithdrawals
        val txSavingsBalance = (allTimeDeposits - allTimeWithdrawals).coerceAtLeast(0L)
        val totalSavings = if (allTimeDeposits > 0L || allTimeWithdrawals > 0L) {
            txSavingsBalance
        } else {
            savingsGoals.sumOf { it.currentAmount }
        }
        val totalAssets = cashBalance + totalSavings

        val monthBudgets = budgets.filter { it.monthKey == monthKey }
        val totalBudgetDoc = monthBudgets.firstOrNull { it.category == "TOTAL_BULANAN" }
        val totalLimit = totalBudgetDoc?.limitAmount
            ?: monthBudgets.sumOf { it.limitAmount }

        val remaining = if (totalLimit > 0L) totalLimit - monthlyExpense else 0L
        val pctUsed = if (totalLimit > 0L) ((monthlyExpense * 100L) / totalLimit).toInt() else 0

        val latest = activeTx.maxByOrNull { it.transactionDateMillis }
        val lastLabel = latest?.let {
            val action = when (it.parsedType) {
                TransactionType.INCOME -> "mencatat pemasukan"
                TransactionType.EXPENSE -> "mencatat pengeluaran"
                TransactionType.SAVINGS_DEPOSIT -> "menyetor tabungan"
                TransactionType.SAVINGS_WITHDRAWAL -> "menarik tabungan"
            }
            "Dicatat oleh ${it.recordedByRoleLabel} (${it.recordedByName}) • $action ${CurrencyFormatter.formatRupiah(it.amount)}"
        }

        return DashboardSummary(
            monthKey = monthKey,
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            cashBalance = cashBalance,
            totalSavings = totalSavings,
            totalAssets = totalAssets,
            totalMonthlyBudgetLimit = totalLimit,
            totalMonthlyBudgetRemaining = remaining,
            totalBudgetPercentUsed = pctUsed,
            lastTransactionLabel = lastLabel
        )
    }

    /**
     * Computes a goal's accumulated savings balance strictly from its SavingsTransaction ledger.
     */
    fun goalBalanceFromTransactions(goalId: String, savingsTransactions: List<SavingsTransaction>): Long {
        return savingsTransactions
            .filter { it.goalId == goalId }
            .sumOf { if (it.parsedType == TransactionType.SAVINGS_DEPOSIT) it.amount else -it.amount }
            .coerceAtLeast(0L)
    }

    /**
     * Evaluates every monthly budget using strictly active EXPENSE transactions (never savings deposits).
     */
    fun evaluateBudgets(
        transactions: List<CashTransaction>,
        budgets: List<MonthlyBudget>,
        monthKey: String
    ): List<BudgetEvaluation> {
        val monthExpenses = activeUniqueTransactions(transactions).filter {
            it.parsedType == TransactionType.EXPENSE &&
                CurrencyFormatter.monthKeyFromMillis(it.transactionDateMillis) == monthKey
        }

        return budgets
            .filter { it.monthKey == monthKey }
            .sortedWith(compareBy<MonthlyBudget> { it.category != "TOTAL_BULANAN" }.thenBy { it.category })
            .map { budget ->
                val spent = monthExpenses
                    .filter { matchesBudgetCategory(it.category, budget.category) }
                    .sumOf { it.amount }
                val remaining = budget.limitAmount - spent
                val pct = if (budget.limitAmount > 0L) ((spent * 100L) / budget.limitAmount).toInt() else 0
                BudgetEvaluation(
                    budget = budget,
                    spentAmount = spent,
                    remainingAmount = remaining,
                    percentUsed = pct,
                    isWarning = pct >= budget.alertThresholdPercent.toInt() && pct < 100,
                    isExceeded = pct >= 100
                )
            }
    }

    fun expenseByCategory(transactions: List<CashTransaction>): List<CategoryBreakdownItem> {
        val expenses = activeUniqueTransactions(transactions).filter { it.parsedType == TransactionType.EXPENSE }
        val total = expenses.sumOf { it.amount }.coerceAtLeast(1L)
        return expenses
            .groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                CategoryBreakdownItem(
                    category = cat,
                    amount = sum,
                    percentage = (sum.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                )
            }
            .sortedByDescending { it.amount }
    }

    fun breakdownByRecorder(transactions: List<CashTransaction>): List<RecorderBreakdownItem> {
        return activeUniqueTransactions(transactions)
            .groupBy { "${it.recordedByName} (${it.recordedByRoleLabel})" }
            .map { (label, list) ->
                RecorderBreakdownItem(
                    label = label,
                    incomeTotal = list.filter {
                        it.parsedType == TransactionType.INCOME &&
                            it.category != CoupleCashCategories.OPENING_SAVINGS_CATEGORY
                    }.sumOf { it.amount },
                    expenseTotal = list.filter { it.parsedType == TransactionType.EXPENSE }.sumOf { it.amount },
                    savingsTotal = list.filter { it.parsedType == TransactionType.SAVINGS_DEPOSIT }.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.incomeTotal + it.expenseTotal }
    }

    /**
     * Generates last 6 months Income vs Expense series for Vico charts.
     */
    fun lastSixMonthsSeries(
        transactions: List<CashTransaction>,
        anchorMonthKey: String
    ): List<PeriodChartPoint> {
        val activeTx = activeUniqueTransactions(transactions)
        val monthKeys = (5 downTo 0).map { offset ->
            CurrencyFormatter.shiftMonthKey(anchorMonthKey, -offset)
        }
        return monthKeys.map { mk ->
            val monthTx = activeTx.filter {
                CurrencyFormatter.monthKeyFromMillis(it.transactionDateMillis) == mk
            }
            val inc = monthTx.filter {
                it.parsedType == TransactionType.INCOME && it.category != CoupleCashCategories.OPENING_SAVINGS_CATEGORY
            }.sumOf { it.amount }
            val exp = monthTx.filter { it.parsedType == TransactionType.EXPENSE }.sumOf { it.amount }
            val parts = mk.split("-")
            val monthNum = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val shortLabel = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
                .getOrElse(monthNum - 1) { mk }
            PeriodChartPoint(label = shortLabel, income = inc, expense = exp)
        }
    }

    /**
     * Cumulative savings growth series over last 6 months from savings transactions.
     */
    fun savingsGrowthSeries(
        savingsTransactions: List<SavingsTransaction>,
        anchorMonthKey: String
    ): List<Pair<String, Long>> {
        val monthKeys = (5 downTo 0).map { offset ->
            CurrencyFormatter.shiftMonthKey(anchorMonthKey, -offset)
        }
        val firstCal = monthKeyEndMillis(CurrencyFormatter.shiftMonthKey(anchorMonthKey, -6))
        var runningBalance = savingsTransactions
            .filter { it.transactionDateMillis <= firstCal }
            .sumOf {
                if (it.parsedType == TransactionType.SAVINGS_DEPOSIT) it.amount else -it.amount
            }
            .coerceAtLeast(0L)

        return monthKeys.map { mk ->
            val monthNet = savingsTransactions
                .filter { CurrencyFormatter.monthKeyFromMillis(it.transactionDateMillis) == mk }
                .sumOf {
                    if (it.parsedType == TransactionType.SAVINGS_DEPOSIT) it.amount else -it.amount
                }
            runningBalance = (runningBalance + monthNet).coerceAtLeast(0L)
            val monthNum = mk.split("-").getOrNull(1)?.toIntOrNull() ?: 1
            val shortLabel = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
                .getOrElse(monthNum - 1) { mk }
            shortLabel to runningBalance
        }
    }

    private fun monthKeyEndMillis(monthKey: String): Long {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    /**
     * Filters activity logs (sorted newest-first) to only include entries created after the most recent SYSTEM_RESET.
     */
    fun filterLogsAfterReset(sortedLogsNewestFirst: List<ActivityLog>): List<ActivityLog> {
        val resetIdx = sortedLogsNewestFirst.indexOfFirst { it.activityType == "SYSTEM_RESET" }
        return if (resetIdx >= 0) sortedLogsNewestFirst.take(resetIdx) else sortedLogsNewestFirst
    }
}
