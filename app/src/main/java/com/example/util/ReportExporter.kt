package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.model.CashTransaction
import com.example.model.TransactionType
import java.io.File
import java.io.FileOutputStream

object ReportExporter {

    fun exportCsvAndShare(
        context: Context,
        householdName: String,
        periodTitle: String,
        transactions: List<CashTransaction>
    ): Result<File> = runCatching {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "Laporan_CoupleCash_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder()
        sb.appendLine("Buku Kas,$householdName")
        sb.appendLine("Periode,$periodTitle")
        sb.appendLine("Tanggal,Tipe,Kategori,Sumber/Metode,Nominal (IDR),Pencatat,Peran,Catatan")
        transactions.filter { !it.deleted }.sortedByDescending { it.transactionDateMillis }.forEach { tx ->
            val date = CurrencyFormatter.formatDateShort(tx.transactionDateMillis)
            val sourceOrMethod = (tx.source ?: tx.paymentMethod ?: "-").replace(",", " ")
            val note = (tx.note ?: "-").replace(",", " ").replace("\n", " ")
            sb.appendLine(
                "$date,${tx.parsedType.label},${tx.category},$sourceOrMethod,${tx.amount},${tx.recordedByName},${tx.recordedByRoleLabel},$note"
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        shareFile(context, file, "text/csv", "Bagikan Laporan CSV CoupleCash")
        file
    }

    fun exportPdfAndShare(
        context: Context,
        householdName: String,
        periodTitle: String,
        totalIncome: Long,
        totalExpense: Long,
        totalSavingsDeposit: Long,
        transactions: List<CashTransaction>
    ): Result<File> = runCatching {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "Laporan_CoupleCash_${System.currentTimeMillis()}.pdf")

        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(5, 150, 105)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 11f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var y = 48f
        canvas.drawText("CoupleCash - Laporan Keuangan Keluarga", 40f, y, titlePaint)
        y += 22f
        canvas.drawText("Buku Kas: $householdName  |  Periode: $periodTitle", 40f, y, bodyPaint)
        y += 18f
        canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
        y += 22f

        canvas.drawText("Ringkasan Periode", 40f, y, headerPaint)
        y += 18f
        canvas.drawText("Total Pemasukan      : ${CurrencyFormatter.formatRupiah(totalIncome)}", 40f, y, bodyPaint)
        y += 16f
        canvas.drawText("Total Pengeluaran    : ${CurrencyFormatter.formatRupiah(totalExpense)}", 40f, y, bodyPaint)
        y += 16f
        canvas.drawText("Setoran Tabungan     : ${CurrencyFormatter.formatRupiah(totalSavingsDeposit)}", 40f, y, bodyPaint)
        y += 16f
        canvas.drawText("Arus Kas Bersih      : ${CurrencyFormatter.formatRupiah(totalIncome - totalExpense)}", 40f, y, headerPaint)
        y += 22f
        canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
        y += 22f

        val activeTransactions = transactions.filter { !it.deleted }
        canvas.drawText("Daftar Transaksi (${activeTransactions.size})", 40f, y, headerPaint)
        y += 18f

        activeTransactions.sortedByDescending { it.transactionDateMillis }.forEach { tx ->
            if (y > pageHeight - 60) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
            val date = CurrencyFormatter.formatDateShort(tx.transactionDateMillis)
            val sign = when (tx.parsedType) {
                TransactionType.INCOME -> "+"
                TransactionType.EXPENSE -> "-"
                TransactionType.SAVINGS_DEPOSIT -> "->"
                TransactionType.SAVINGS_WITHDRAWAL -> "<-"
            }
            val rowText = "$date | ${tx.category} | $sign${CurrencyFormatter.formatRupiah(tx.amount)} | ${tx.recordedByName} (${tx.recordedByRoleLabel})"
            canvas.drawText(rowText.take(86), 40f, y, bodyPaint)
            y += 16f
        }

        pdf.finishPage(page)
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()

        shareFile(context, file, "application/pdf", "Bagikan Laporan PDF CoupleCash")
        file
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
