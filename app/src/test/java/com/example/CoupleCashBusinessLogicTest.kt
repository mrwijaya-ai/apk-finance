package com.example

import com.example.model.ActivityLog
import com.example.model.CashTransaction
import com.example.model.CoupleCashCategories
import com.example.model.MonthlyBudget
import com.example.model.SavingsGoal
import com.example.model.SavingsTransaction
import com.example.model.TransactionType
import com.example.util.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CoupleCashBusinessLogicTest {

    private fun dateMillis(year: Int, monthZeroBased: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, monthZeroBased, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `prompt canonical example - Income 10M Expense 3M Savings 2M yields Cash 5M Savings 2M Assets 7M`() {
        val monthKey = "2026-11"
        val d = dateMillis(2026, Calendar.NOVEMBER, 10)
        val txs = listOf(
            CashTransaction(
                id = "t1",
                householdId = "hh_1",
                memberUids = listOf("suami", "istri"),
                type = TransactionType.INCOME.name,
                amount = 10_000_000L,
                category = "Gaji suami",
                recordedByUid = "suami",
                recordedByName = "Budi",
                recordedByRoleLabel = "Suami",
                transactionDateMillis = d,
                clientMutationId = "m1"
            ),
            CashTransaction(
                id = "t2",
                householdId = "hh_1",
                memberUids = listOf("suami", "istri"),
                type = TransactionType.EXPENSE.name,
                amount = 3_000_000L,
                category = "Belanja bulanan",
                recordedByUid = "istri",
                recordedByName = "Sari",
                recordedByRoleLabel = "Istri",
                transactionDateMillis = d + 1000,
                clientMutationId = "m2"
            ),
            CashTransaction(
                id = "t3",
                householdId = "hh_1",
                memberUids = listOf("suami", "istri"),
                type = TransactionType.SAVINGS_DEPOSIT.name,
                amount = 2_000_000L,
                category = "Dana Darurat",
                recordedByUid = "suami",
                recordedByName = "Budi",
                recordedByRoleLabel = "Suami",
                savingsGoalId = "g1",
                savingsGoalName = "Dana Darurat",
                transactionDateMillis = d + 2000,
                clientMutationId = "m3"
            )
        )
        val goals = listOf(
            SavingsGoal(id = "g1", name = "Dana Darurat", targetAmount = 10_000_000L, currentAmount = 2_000_000L)
        )
        val budgets = listOf(
            MonthlyBudget(id = "b1", monthKey = monthKey, category = "TOTAL_BULANAN", limitAmount = 5_000_000L)
        )

        val summary = FinanceCalculator.computeDashboardSummary(txs, goals, budgets, monthKey)
        assertEquals(5_000_000L, summary.cashBalance)
        assertEquals(2_000_000L, summary.totalSavings)
        assertEquals(7_000_000L, summary.totalAssets)
        assertEquals(3_000_000L, summary.monthlyExpense)
        assertEquals(2_000_000L, summary.totalMonthlyBudgetRemaining)
    }

    @Test
    fun `TEST 1 and 2 - shared full transparency income 5M and expense 500k visible and tallied`() {
        val monthKey = "2026-11"
        val d = dateMillis(2026, Calendar.NOVEMBER, 15)
        val txs = listOf(
            CashTransaction(
                id = "inc_1",
                householdId = "hh_1",
                memberUids = listOf("suami_uid", "istri_uid"),
                type = TransactionType.INCOME.name,
                amount = 5_000_000L,
                category = "Gaji suami",
                recordedByUid = "suami_uid",
                recordedByName = "Budi",
                recordedByRoleLabel = "Suami",
                transactionDateMillis = d,
                clientMutationId = "mut_1"
            ),
            CashTransaction(
                id = "exp_1",
                householdId = "hh_1",
                memberUids = listOf("suami_uid", "istri_uid"),
                type = TransactionType.EXPENSE.name,
                amount = 500_000L,
                category = "Makanan",
                recordedByUid = "istri_uid",
                recordedByName = "Sari",
                recordedByRoleLabel = "Istri",
                transactionDateMillis = d + 5000,
                clientMutationId = "mut_2"
            )
        )

        val summary = FinanceCalculator.computeDashboardSummary(txs, emptyList(), emptyList(), monthKey)
        assertEquals(5_000_000L, summary.monthlyIncome)
        assertEquals(500_000L, summary.monthlyExpense)
        assertEquals(4_500_000L, summary.cashBalance)
        assertEquals(4_500_000L, summary.totalAssets)
        assertTrue(summary.lastTransactionLabel!!.contains("Dicatat oleh Istri"))
    }

    @Test
    fun `TEST 3 and 4 - savings deposit 1M and withdrawal 300k update cash and savings without altering income or budget`() {
        val monthKey = "2026-11"
        val d = dateMillis(2026, Calendar.NOVEMBER, 18)

        val baseTxs = listOf(
            CashTransaction(
                id = "inc_1",
                type = TransactionType.INCOME.name,
                amount = 5_000_000L,
                category = "Gaji suami",
                recordedByUid = "suami_uid",
                recordedByRoleLabel = "Suami",
                transactionDateMillis = d,
                clientMutationId = "m_inc"
            ),
            CashTransaction(
                id = "exp_1",
                type = TransactionType.EXPENSE.name,
                amount = 500_000L,
                category = "Makanan",
                recordedByUid = "istri_uid",
                recordedByRoleLabel = "Istri",
                transactionDateMillis = d + 1000,
                clientMutationId = "m_exp"
            )
        )
        val budgets = listOf(
            MonthlyBudget(id = "b_total", monthKey = monthKey, category = "TOTAL_BULANAN", limitAmount = 3_000_000L)
        )

        // TEST 3: Husband deposits 1,000,000 to savings
        val afterDepositTxs = baseTxs + CashTransaction(
            id = "sav_dep_1",
            type = TransactionType.SAVINGS_DEPOSIT.name,
            amount = 1_000_000L,
            category = "Dana Darurat",
            recordedByUid = "suami_uid",
            recordedByRoleLabel = "Suami",
            savingsGoalId = "g1",
            transactionDateMillis = d + 2000,
            clientMutationId = "m_dep"
        )
        val afterDepositSummary = FinanceCalculator.computeDashboardSummary(afterDepositTxs, emptyList(), budgets, monthKey)
        assertEquals(3_500_000L, afterDepositSummary.cashBalance) // 4.5M - 1M
        assertEquals(1_000_000L, afterDepositSummary.totalSavings) // +1M
        assertEquals(4_500_000L, afterDepositSummary.totalAssets) // preserved!
        assertEquals(2_500_000L, afterDepositSummary.totalMonthlyBudgetRemaining) // budget untouched!

        // TEST 4: Wife withdraws 300,000 from savings
        val afterWithdrawTxs = afterDepositTxs + CashTransaction(
            id = "sav_wd_1",
            type = TransactionType.SAVINGS_WITHDRAWAL.name,
            amount = 300_000L,
            category = "Dana Darurat",
            recordedByUid = "istri_uid",
            recordedByRoleLabel = "Istri",
            savingsGoalId = "g1",
            transactionDateMillis = d + 3000,
            clientMutationId = "m_wd"
        )
        val afterWithdrawSummary = FinanceCalculator.computeDashboardSummary(afterWithdrawTxs, emptyList(), budgets, monthKey)
        assertEquals(3_800_000L, afterWithdrawSummary.cashBalance) // +300k
        assertEquals(700_000L, afterWithdrawSummary.totalSavings) // -300k
        assertEquals(5_000_000L, afterWithdrawSummary.monthlyIncome) // income unchanged!
        assertEquals(2_500_000L, afterWithdrawSummary.totalMonthlyBudgetRemaining) // budget unchanged!

        val savingsLedger = listOf(
            SavingsTransaction(id = "st1", goalId = "g1", type = TransactionType.SAVINGS_DEPOSIT.name, amount = 1_000_000L, transactionDateMillis = d),
            SavingsTransaction(id = "st2", goalId = "g1", type = TransactionType.SAVINGS_WITHDRAWAL.name, amount = 300_000L, transactionDateMillis = d)
        )
        assertEquals(700_000L, FinanceCalculator.goalBalanceFromTransactions("g1", savingsLedger))
    }

    @Test
    fun `TEST 5 - budget updates do not mutate cash balance or transaction totals`() {
        val monthKey = "2026-11"
        val d = dateMillis(2026, Calendar.NOVEMBER, 19)
        val txs = listOf(
            CashTransaction(id = "t1", type = TransactionType.INCOME.name, amount = 4_000_000L, transactionDateMillis = d, clientMutationId = "m1"),
            CashTransaction(id = "t2", type = TransactionType.EXPENSE.name, amount = 1_000_000L, category = "Makanan", transactionDateMillis = d, clientMutationId = "m2")
        )
        val budgetV1 = listOf(MonthlyBudget(id = "b1", monthKey = monthKey, category = "Makanan", limitAmount = 2_000_000L))
        val budgetV2 = listOf(MonthlyBudget(id = "b1", monthKey = monthKey, category = "Makanan", limitAmount = 1_200_000L))

        val sum1 = FinanceCalculator.computeDashboardSummary(txs, emptyList(), budgetV1, monthKey)
        val sum2 = FinanceCalculator.computeDashboardSummary(txs, emptyList(), budgetV2, monthKey)

        assertEquals(sum1.cashBalance, sum2.cashBalance)
        assertEquals(sum1.monthlyExpense, sum2.monthlyExpense)
        val eval2 = FinanceCalculator.evaluateBudgets(txs, budgetV2, monthKey).first()
        assertEquals(83, eval2.percentUsed)
        assertTrue(eval2.isWarning)
    }

    @Test
    fun `TEST 6 and 9 - offline duplicate clientMutationId deduplicated and soft-deleted transactions excluded`() {
        val monthKey = "2026-11"
        val d = dateMillis(2026, Calendar.NOVEMBER, 20)
        val txsWithDuplicateAndSoftDelete = listOf(
            CashTransaction(id = "t1", type = TransactionType.INCOME.name, amount = 6_000_000L, transactionDateMillis = d, clientMutationId = "same_mut_1"),
            // Simulated duplicate retry from offline queue with identical clientMutationId
            CashTransaction(id = "t1_dup", type = TransactionType.INCOME.name, amount = 6_000_000L, transactionDateMillis = d, clientMutationId = "same_mut_1"),
            CashTransaction(id = "t2", type = TransactionType.EXPENSE.name, amount = 800_000L, category = "Makanan", transactionDateMillis = d, clientMutationId = "mut_2"),
            // Soft-deleted transaction must remain in audit history but NEVER affect balances
            CashTransaction(id = "t3_del", type = TransactionType.EXPENSE.name, amount = 2_000_000L, category = "Makanan", transactionDateMillis = d, clientMutationId = "mut_3", deleted = true)
        )

        val summary = FinanceCalculator.computeDashboardSummary(txsWithDuplicateAndSoftDelete, emptyList(), emptyList(), monthKey)
        assertEquals(6_000_000L, summary.monthlyIncome)
        assertEquals(800_000L, summary.monthlyExpense)
        assertEquals(5_200_000L, summary.cashBalance)
    }

    @Test
    fun `shared savings categories contain no private savings concept`() {
        assertFalse(CoupleCashCategories.savingsCategories.any { it.contains("pribadi", ignoreCase = true) })
        assertTrue(CoupleCashCategories.savingsCategories.contains("Dana Darurat"))
        assertTrue(CoupleCashCategories.savingsCategories.contains("Beli Rumah"))
        assertTrue(CoupleCashCategories.savingsCategories.contains("Pendidikan Anak"))
    }

    @Test
    fun `opening savings balance adds savings and totalAssets without altering cashBalance or monthlyIncome`() {
        val monthKey = "2026-11"
        val d = dateMillis(2026, Calendar.NOVEMBER, 26)
        val txs = listOf(
            CashTransaction(
                id = "open_sav_1",
                householdId = "hh_1",
                memberUids = listOf("suami", "istri"),
                type = TransactionType.SAVINGS_DEPOSIT.name,
                amount = 15_000_000L,
                category = "Dana Darurat",
                recordedByUid = "suami",
                recordedByName = "Wijaya",
                recordedByRoleLabel = "Suami",
                savingsGoalId = "g1",
                savingsGoalName = "Dana Darurat",
                transactionDateMillis = d,
                clientMutationId = "m_open_sav"
            ),
            CashTransaction(
                id = "open_inc_1",
                householdId = "hh_1",
                memberUids = listOf("suami", "istri"),
                type = TransactionType.INCOME.name,
                amount = 15_000_000L,
                category = CoupleCashCategories.OPENING_SAVINGS_CATEGORY,
                source = "Tabungan Sebelumnya",
                recordedByUid = "suami",
                recordedByName = "Wijaya",
                recordedByRoleLabel = "Suami",
                savingsGoalId = "g1",
                savingsGoalName = "Dana Darurat",
                transactionDateMillis = d,
                clientMutationId = "m_open_inc"
            )
        )
        val goals = listOf(
            SavingsGoal(id = "g1", name = "Dana Darurat", targetAmount = 30_000_000L, currentAmount = 15_000_000L)
        )

        val summary = FinanceCalculator.computeDashboardSummary(txs, goals, emptyList(), monthKey)
        assertEquals(0L, summary.cashBalance)
        assertEquals(15_000_000L, summary.totalSavings)
        assertEquals(15_000_000L, summary.totalAssets)
        assertEquals(0L, summary.monthlyIncome)
        assertEquals(0L, summary.monthlyExpense)

        val series = FinanceCalculator.lastSixMonthsSeries(txs, monthKey)
        assertEquals(0L, series.last().income)

        val recorderBreakdown = FinanceCalculator.breakdownByRecorder(txs)
        assertEquals(0L, recorderBreakdown.first().incomeTotal)
        assertEquals(15_000_000L, recorderBreakdown.first().savingsTotal)
    }

    @Test
    fun `filterLogsAfterReset clears pre-reset activity history and keeps only post-reset logs`() {
        val newestAfterReset = ActivityLog(id = "l3", activityType = "INCOME_ADDED", detail = "Baru")
        val resetEvent = ActivityLog(id = "l2", activityType = "SYSTEM_RESET", detail = "Reset")
        val oldBeforeReset = ActivityLog(id = "l1", activityType = "EXPENSE_ADDED", detail = "Lama")

        val immediatelyAfterReset = FinanceCalculator.filterLogsAfterReset(listOf(resetEvent, oldBeforeReset))
        assertTrue(immediatelyAfterReset.isEmpty())

        val afterNewActivity = FinanceCalculator.filterLogsAfterReset(listOf(newestAfterReset, resetEvent, oldBeforeReset))
        assertEquals(listOf(newestAfterReset), afterNewActivity)
    }
}
