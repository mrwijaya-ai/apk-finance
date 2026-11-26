package com.example

import com.example.model.CashTransaction
import com.example.model.MonthlyBudget
import com.example.model.SavingsGoal
import com.example.model.TransactionType
import com.example.util.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinanceCalculatorTest {

    private fun millisFor(year: Int, monthZeroBased: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `savings deposit transfers cash to savings without inflating monthly expense or reducing budget`() {
        val monthKey = "2026-05"
        val date = millisFor(2026, Calendar.MAY, 10)

        val transactions = listOf(
            CashTransaction(
                id = "tx1",
                type = TransactionType.INCOME.name,
                amount = 10_000_000L,
                category = "Gaji suami",
                transactionDateMillis = date
            ),
            CashTransaction(
                id = "tx2",
                type = TransactionType.EXPENSE.name,
                amount = 2_000_000L,
                category = "Makanan",
                transactionDateMillis = date
            ),
            CashTransaction(
                id = "tx3",
                type = TransactionType.EXPENSE.name,
                amount = 500_000L,
                category = "Tagihan listrik",
                transactionDateMillis = date
            ),
            CashTransaction(
                id = "tx4",
                type = TransactionType.SAVINGS_DEPOSIT.name,
                amount = 3_000_000L,
                category = "Dana darurat",
                transactionDateMillis = date
            ),
            CashTransaction(
                id = "tx5",
                type = TransactionType.SAVINGS_WITHDRAWAL.name,
                amount = 500_000L,
                category = "Dana darurat",
                transactionDateMillis = date
            )
        )

        val goals = listOf(
            SavingsGoal(
                id = "g1",
                name = "Dana darurat",
                targetAmount = 20_000_000L,
                currentAmount = 2_500_000L
            )
        )

        val budgets = listOf(
            MonthlyBudget(
                id = "b_total",
                monthKey = monthKey,
                category = "TOTAL_BULANAN",
                limitAmount = 5_000_000L,
                alertThresholdPercent = 80L
            ),
            MonthlyBudget(
                id = "b_bill",
                monthKey = monthKey,
                category = "Tagihan",
                limitAmount = 1_000_000L,
                alertThresholdPercent = 80L
            )
        )

        val summary = FinanceCalculator.computeDashboardSummary(transactions, goals, budgets, monthKey)

        // Monthly income = 10M; Monthly expense = 2M + 0.5M = 2.5M (3M savings deposit excluded!)
        assertEquals(10_000_000L, summary.monthlyIncome)
        assertEquals(2_500_000L, summary.monthlyExpense)

        // Cash balance = 10M - 2.5M - 3M deposit + 0.5M withdrawal = 5.0M
        assertEquals(5_000_000L, summary.cashBalance)
        // Savings = 2.5M -> Total assets = 7.5M (no double counting)
        assertEquals(2_500_000L, summary.totalSavings)
        assertEquals(7_500_000L, summary.totalAssets)

        // Monthly total budget remaining = 5M - 2.5M = 2.5M (NOT reduced by 3M savings deposit)
        assertEquals(5_000_000L, summary.totalMonthlyBudgetLimit)
        assertEquals(2_500_000L, summary.totalMonthlyBudgetRemaining)

        // Category budget evaluations
        val evals = FinanceCalculator.evaluateBudgets(transactions, budgets, monthKey)
        val totalEval = evals.first { it.budget.category == "TOTAL_BULANAN" }
        assertEquals(2_500_000L, totalEval.spentAmount)
        assertEquals(50, totalEval.percentUsed)
        assertFalse(totalEval.isWarning)
        assertFalse(totalEval.isExceeded)

        val billEval = evals.first { it.budget.category == "Tagihan" }
        assertEquals(500_000L, billEval.spentAmount)
        assertEquals(500_000L, billEval.remainingAmount)
        assertTrue(billEval.percentUsed == 50)
    }
}
