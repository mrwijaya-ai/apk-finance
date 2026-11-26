package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrencyFormatter {
    private val idSymbols = DecimalFormatSymbols(Locale("id", "ID")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val numberFormat = DecimalFormat("#,##0", idSymbols)

    fun formatRupiah(amount: Long): String {
        val absFormatted = numberFormat.format(kotlin.math.abs(amount))
        return if (amount < 0L) "-Rp$absFormatted" else "Rp$absFormatted"
    }

    fun formatCompactRupiah(amount: Long): String {
        val abs = kotlin.math.abs(amount)
        val prefix = if (amount < 0L) "-Rp" else "Rp"
        return when {
            abs >= 1_000_000_000L -> String.format(Locale("id", "ID"), "%s%.1fM", prefix, abs / 1_000_000_000.0)
            abs >= 1_000_000L -> String.format(Locale("id", "ID"), "%s%.1fJt", prefix, abs / 1_000_000.0)
            abs >= 1_000L -> String.format(Locale("id", "ID"), "%s%dRb", prefix, abs / 1_000L)
            else -> formatRupiah(amount)
        }
    }

    fun monthKeyFromMillis(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun currentMonthKey(): String = monthKeyFromMillis(System.currentTimeMillis())

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)) - 1
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return monthKeyFromMillis(cal.timeInMillis)
    }

    fun formatMonthLabel(monthKey: String): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return monthKey
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(cal.time)
    }

    fun formatDateShort(millis: Long): String {
        if (millis <= 0L) return "-"
        return SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "-"
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(millis))
    }
}
