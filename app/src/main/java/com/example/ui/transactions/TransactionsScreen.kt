package com.example.ui.transactions

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.CoupleCashRepository
import com.example.model.CashTransaction
import com.example.model.CoupleCashCategories
import com.example.model.TransactionType
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RupiahAmountField
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber
import com.example.util.CurrencyFormatter
import java.util.Calendar

enum class PeriodFilter(val label: String) {
    TODAY("Hari Ini"),
    THIS_WEEK("Minggu Ini"),
    THIS_MONTH("Bulan Ini"),
    CUSTOM_RANGE("Rentang Tanggal"),
    ALL("Semua")
}

@Composable
fun TransactionsScreen(
    transactions: List<CashTransaction>,
    defaultRoleLabel: String,
    isBusy: Boolean,
    onUpdateTransaction: (
        existingTx: CashTransaction,
        amount: Long,
        category: String,
        source: String?,
        paymentMethod: String?,
        note: String?,
        receiptUri: Uri?,
        roleLabel: String,
        dateMillis: Long,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onRestoreTransaction: (String) -> Unit,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    var periodFilter by remember { mutableStateOf(PeriodFilter.THIS_MONTH) }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var recorderFilter by remember { mutableStateOf<String?>(null) }
    var showDeletedOnly by remember { mutableStateOf(false) }

    var showCategoryMenu by remember { mutableStateOf(false) }
    var showRecorderMenu by remember { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }

    val now = remember { System.currentTimeMillis() }
    var rangeStartMillis by remember { mutableLongStateOf(now - 7L * 24 * 60 * 60 * 1000) }
    var rangeEndMillis by remember { mutableLongStateOf(now) }

    var receiptPreviewUrl by remember { mutableStateOf<String?>(null) }
    var detailTx by remember { mutableStateOf<CashTransaction?>(null) }
    var editingTx by remember { mutableStateOf<CashTransaction?>(null) }
    var deleteConfirmTx by remember { mutableStateOf<CashTransaction?>(null) }
    var restoreConfirmTx by remember { mutableStateOf<CashTransaction?>(null) }

    val deletedRestorableCount = remember(transactions) {
        transactions.count {
            it.deleted &&
                it.lastEditSummary != CoupleCashRepository.RESET_MARKER &&
                (it.parsedType == TransactionType.INCOME || it.parsedType == TransactionType.EXPENSE)
        }
    }

    val allCategories = remember(transactions) {
        (CoupleCashCategories.incomeCategories + CoupleCashCategories.expenseCategories + transactions.map { it.category })
            .distinct()
    }
    val allRecorders = remember(transactions) {
        (listOf("Suami", "Istri") + transactions.map { it.recordedByRoleLabel }).distinct()
    }

    val filtered = remember(transactions, periodFilter, typeFilter, categoryFilter, recorderFilter, showDeletedOnly, rangeStartMillis, rangeEndMillis) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val currentMonthKey = CurrencyFormatter.currentMonthKey()

        transactions.filter { tx ->
            val matchDeleted = if (showDeletedOnly) {
                tx.deleted && tx.lastEditSummary != CoupleCashRepository.RESET_MARKER
            } else {
                !tx.deleted
            }
            val matchPeriod = if (showDeletedOnly) {
                true
            } else {
                when (periodFilter) {
                    PeriodFilter.TODAY -> tx.transactionDateMillis >= todayStart
                    PeriodFilter.THIS_WEEK -> tx.transactionDateMillis >= weekStart
                    PeriodFilter.THIS_MONTH -> CurrencyFormatter.monthKeyFromMillis(tx.transactionDateMillis) == currentMonthKey
                    PeriodFilter.CUSTOM_RANGE -> tx.transactionDateMillis in rangeStartMillis..(rangeEndMillis + 86_399_999L)
                    PeriodFilter.ALL -> true
                }
            }
            val matchType = typeFilter == null || tx.parsedType == typeFilter
            val matchCategory = categoryFilter == null || tx.category == categoryFilter
            val matchRecorder = recorderFilter == null || tx.recordedByRoleLabel == recorderFilter
            matchDeleted && matchPeriod && matchType && matchCategory && matchRecorder
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header + Reports button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Daftar Transaksi", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "${filtered.size} transaksi ditampilkan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onOpenReports,
                modifier = Modifier.testTag("open_reports_button")
            ) {
                Icon(Icons.Rounded.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Laporan")
            }
        }

        // Period Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PeriodFilter.entries.forEach { pf ->
                FilterChip(
                    selected = periodFilter == pf,
                    onClick = {
                        periodFilter = pf
                        if (pf == PeriodFilter.CUSTOM_RANGE) showRangeDialog = true
                    },
                    label = { Text(pf.label) }
                )
            }
        }

        // Secondary Filters (Type, Category, Recorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null },
                label = { Text("Semua Tipe") }
            )
            FilterChip(
                selected = typeFilter == TransactionType.INCOME,
                onClick = { typeFilter = if (typeFilter == TransactionType.INCOME) null else TransactionType.INCOME },
                label = { Text("Pemasukan") }
            )
            FilterChip(
                selected = typeFilter == TransactionType.EXPENSE,
                onClick = { typeFilter = if (typeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE },
                label = { Text("Pengeluaran") }
            )
            FilterChip(
                selected = typeFilter == TransactionType.SAVINGS_DEPOSIT,
                onClick = {
                    typeFilter = if (typeFilter == TransactionType.SAVINGS_DEPOSIT) null else TransactionType.SAVINGS_DEPOSIT
                },
                label = { Text("Tabungan") }
            )

            // Category Dropdown Chip
            FilterChip(
                selected = categoryFilter != null,
                onClick = { showCategoryMenu = true },
                leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(categoryFilter ?: "Kategori") }
            )
            DropdownMenu(
                expanded = showCategoryMenu,
                onDismissRequest = { showCategoryMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Semua Kategori") },
                    onClick = {
                        categoryFilter = null
                        showCategoryMenu = false
                    }
                )
                allCategories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            categoryFilter = cat
                            showCategoryMenu = false
                        }
                    )
                }
            }

            // Recorder Dropdown Chip
            FilterChip(
                selected = recorderFilter != null,
                onClick = { showRecorderMenu = true },
                label = { Text(recorderFilter ?: "Pencatat") }
            )
            DropdownMenu(
                expanded = showRecorderMenu,
                onDismissRequest = { showRecorderMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Semua Pencatat") },
                    onClick = {
                        recorderFilter = null
                        showRecorderMenu = false
                    }
                )
                allRecorders.forEach { rec ->
                    DropdownMenuItem(
                        text = { Text(rec) },
                        onClick = {
                            recorderFilter = rec
                            showRecorderMenu = false
                        }
                    )
                }
            }

            // Soft-Deleted Audit Toggle Chip
            FilterChip(
                selected = showDeletedOnly,
                onClick = { showDeletedOnly = !showDeletedOnly },
                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = {
                    Text(
                        if (deletedRestorableCount > 0) "Terhapus ($deletedRestorableCount)" else "Terhapus"
                    )
                },
                modifier = Modifier.testTag("filter_deleted_transactions")
            )
        }

        if (filtered.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = if (showDeletedOnly) "Tidak Ada Transaksi Terhapus" else "Tidak Ada Transaksi",
                        subtitle = if (showDeletedOnly) "Transaksi yang dihapus (soft delete) akan muncul di sini dan dapat dipulihkan."
                        else "Belum ada transaksi yang sesuai dengan filter yang dipilih."
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { tx ->
                    TransactionItemCard(
                        tx = tx,
                        onViewReceipt = { url -> receiptPreviewUrl = url },
                        onViewDetail = { detailTx = tx },
                        onRequestEdit = { editingTx = tx },
                        onRequestRestore = { restoreConfirmTx = tx }
                    )
                }
            }
        }
    }

    if (showRangeDialog) {
        AlertDialog(
            onDismissRequest = { showRangeDialog = false },
            title = { Text("Pilih Rentang Tanggal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DatePickerField(
                        label = "Dari Tanggal",
                        selectedMillis = rangeStartMillis,
                        onDateSelected = { rangeStartMillis = it }
                    )
                    DatePickerField(
                        label = "Sampai Tanggal",
                        selectedMillis = rangeEndMillis,
                        onDateSelected = { rangeEndMillis = it }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showRangeDialog = false }) { Text("Terapkan") }
            }
        )
    }

    if (receiptPreviewUrl != null) {
        AlertDialog(
            onDismissRequest = { receiptPreviewUrl = null },
            title = { Text("Foto Bukti / Struk") },
            text = {
                ReceiptPhotoView(
                    receiptData = receiptPreviewUrl!!,
                    contentDescription = "Foto struk transaksi",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = { receiptPreviewUrl = null }) { Text("Tutup") }
            }
        )
    }

    detailTx?.let { tx ->
        TransactionDetailDialog(
            tx = tx,
            onDismiss = { detailTx = null },
            onViewFullReceipt = { url -> receiptPreviewUrl = url },
            onRequestEdit = {
                detailTx = null
                editingTx = tx
            },
            onRequestDelete = {
                detailTx = null
                deleteConfirmTx = tx
            },
            onRequestRestore = {
                detailTx = null
                restoreConfirmTx = tx
            }
        )
    }

    editingTx?.let { tx ->
        EditTransactionDialog(
            tx = tx,
            defaultRoleLabel = defaultRoleLabel,
            isBusy = isBusy,
            onDismiss = { editingTx = null },
            onConfirm = { amount, category, source, paymentMethod, note, receiptUri, roleLabel, dateMillis ->
                onUpdateTransaction(tx, amount, category, source, paymentMethod, note, receiptUri, roleLabel, dateMillis) {
                    editingTx = null
                }
            }
        )
    }

    deleteConfirmTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTx = null },
            title = { Text("Hapus Transaksi?") },
            text = {
                Text("Hapus ${tx.category} senilai ${CurrencyFormatter.formatRupiah(tx.amount)}? Penghapusan bersifat soft-delete dan masih dapat dipulihkan dari filter Terhapus.")
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteTransaction(tx.id)
                    deleteConfirmTx = null
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTx = null }) { Text("Batal") }
            }
        )
    }

    restoreConfirmTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { restoreConfirmTx = null },
            title = { Text("Pulihkan Transaksi?") },
            text = {
                Text("Pulihkan ${tx.category} senilai ${CurrencyFormatter.formatRupiah(tx.amount)}? Transaksi akan kembali aktif di daftar transaksi dan memengaruhi perhitungan saldo serta anggaran.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreTransaction(tx.id)
                        restoreConfirmTx = null
                    },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("confirm_restore_transaction")
                ) { Text("Pulihkan") }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirmTx = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun TransactionItemCard(
    tx: CashTransaction,
    onViewReceipt: (String) -> Unit,
    onViewDetail: () -> Unit,
    onRequestEdit: () -> Unit,
    onRequestRestore: () -> Unit
) {
    val type = tx.parsedType
    val (color, sign) = when (type) {
        TransactionType.INCOME -> IncomeGreen to "+"
        TransactionType.EXPENSE -> ExpenseRed to "-"
        TransactionType.SAVINGS_DEPOSIT -> SavingsAmber to "->"
        TransactionType.SAVINGS_WITHDRAWAL -> EmeraldPrimary to "<-"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewDetail),
        colors = CardDefaults.cardColors(
            containerColor = if (tx.deleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.category,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (tx.deleted) TextDecoration.LineThrough else null
                    )
                    Surface(
                        color = color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (tx.deleted) {
                        Surface(
                            color = ExpenseRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Terhapus",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                val meta = buildString {
                    append("Dicatat oleh ${tx.recordedByRoleLabel} (${tx.recordedByName})")
                    if (!tx.paymentMethod.isNullOrBlank()) append(" • ${tx.paymentMethod}")
                    if (!tx.source.isNullOrBlank()) append(" • ${tx.source}")
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!tx.note.isNullOrBlank()) {
                    Text(
                        text = tx.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!tx.lastEditSummary.isNullOrBlank()) {
                    Text(
                        text = "Riwayat edit: ${tx.lastEditSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SavingsAmber
                    )
                }
                if (tx.deleted && !tx.deletedByName.isNullOrBlank()) {
                    Text(
                        text = "Dihapus oleh ${tx.deletedByRoleLabel ?: ""} (${tx.deletedByName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseRed
                    )
                }
                Text(
                    text = CurrencyFormatter.formatDateShort(tx.transactionDateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!tx.receiptUrl.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onViewReceipt(tx.receiptUrl) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = "Lihat bukti foto",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Lihat Foto Bukti",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$sign${CurrencyFormatter.formatRupiah(tx.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tx.deleted) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (tx.deleted) TextDecoration.LineThrough else null
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tx.deleted && (type == TransactionType.INCOME || type == TransactionType.EXPENSE) && tx.lastEditSummary != CoupleCashRepository.RESET_MARKER) {
                        IconButton(
                            onClick = onRequestRestore,
                            modifier = Modifier.testTag("restore_transaction_${tx.id}")
                        ) {
                            Icon(
                                Icons.Rounded.RestoreFromTrash,
                                contentDescription = "Pulihkan transaksi",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (!tx.deleted && (type == TransactionType.INCOME || type == TransactionType.EXPENSE)) {
                        IconButton(
                            onClick = onRequestEdit,
                            modifier = Modifier.testTag("edit_transaction_${tx.id}")
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit transaksi",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onViewDetail,
                        modifier = Modifier.testTag("detail_transaction_${tx.id}")
                    ) {
                        Icon(
                            Icons.Rounded.Visibility,
                            contentDescription = "Lihat detail transaksi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailDialog(
    tx: CashTransaction,
    onDismiss: () -> Unit,
    onViewFullReceipt: (String) -> Unit,
    onRequestEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestRestore: () -> Unit
) {
    val type = tx.parsedType
    val (color, sign) = when (type) {
        TransactionType.INCOME -> IncomeGreen to "+"
        TransactionType.EXPENSE -> ExpenseRed to "-"
        TransactionType.SAVINGS_DEPOSIT -> SavingsAmber to "->"
        TransactionType.SAVINGS_WITHDRAWAL -> EmeraldPrimary to "<-"
    }
    val canModify = !tx.deleted && (type == TransactionType.INCOME || type == TransactionType.EXPENSE)
    val canRestore = tx.deleted && (type == TransactionType.INCOME || type == TransactionType.EXPENSE) && tx.lastEditSummary != CoupleCashRepository.RESET_MARKER

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Detail Transaksi", style = MaterialTheme.typography.titleLarge)
                Surface(
                    color = color.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$sign${CurrencyFormatter.formatRupiah(tx.amount)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (tx.deleted) TextDecoration.LineThrough else null
                )

                DetailInfoRow("Kategori", tx.category)
                DetailInfoRow("Tanggal", CurrencyFormatter.formatDateShort(tx.transactionDateMillis))
                DetailInfoRow("Pencatat", "Dicatat oleh ${tx.recordedByRoleLabel} (${tx.recordedByName})")
                if (!tx.source.isNullOrBlank()) {
                    DetailInfoRow("Sumber Pemasukan", tx.source)
                }
                if (!tx.paymentMethod.isNullOrBlank()) {
                    DetailInfoRow("Metode Pembayaran", tx.paymentMethod)
                }
                DetailInfoRow(
                    "Catatan",
                    tx.note?.takeIf { it.isNotBlank() } ?: "Tidak ada catatan"
                )
                tx.createdAt?.toDate()?.time?.let { createdMillis ->
                    DetailInfoRow("Waktu Catat", CurrencyFormatter.formatDateTime(createdMillis))
                }

                if (tx.editCount > 0L || !tx.lastEditSummary.isNullOrBlank()) {
                    Surface(
                        color = SavingsAmber.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Riwayat Edit (${tx.editCount}x)",
                                style = MaterialTheme.typography.labelMedium,
                                color = SavingsAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!tx.lastEditSummary.isNullOrBlank()) {
                                Text(
                                    text = tx.lastEditSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!tx.lastEditedByName.isNullOrBlank()) {
                                Text(
                                    text = "Oleh ${tx.lastEditedByRoleLabel ?: ""} (${tx.lastEditedByName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (tx.deleted) {
                    Surface(
                        color = ExpenseRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Status: Dihapus (Soft Delete)",
                                style = MaterialTheme.typography.labelMedium,
                                color = ExpenseRed,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!tx.deletedByName.isNullOrBlank()) {
                                Text(
                                    text = "Oleh ${tx.deletedByRoleLabel ?: ""} (${tx.deletedByName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Bukti Foto / Struk",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!tx.receiptUrl.isNullOrBlank()) {
                    ReceiptPhotoView(
                        receiptData = tx.receiptUrl,
                        contentDescription = "Foto bukti transaksi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onViewFullReceipt(tx.receiptUrl) }
                    )
                    Text(
                        text = "Ketuk foto untuk memperbesar",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary
                    )
                } else {
                    Text(
                        text = "Tidak ada foto bukti/struk yang dilampirkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (canModify) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRequestEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                        TextButton(
                            onClick = onRequestDelete,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hapus (Soft Delete)", color = ExpenseRed)
                        }
                    }
                } else if (canRestore) {
                    Button(
                        onClick = onRequestRestore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_restore_transaction_button")
                    ) {
                        Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pulihkan Transaksi")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ReceiptPhotoView(
    receiptData: String,
    contentDescription: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    if (receiptData.startsWith("data:image", ignoreCase = true)) {
        val decodedBitmap by produceState<ImageBitmap?>(initialValue = null, receiptData) {
            value = withContext(Dispatchers.Default) {
                try {
                    val base64Part = receiptData.substringAfter(",", "")
                    if (base64Part.isBlank()) null
                    else {
                        val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
        val bmp = decodedBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        } else {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    } else {
        AsyncImage(
            model = receiptData,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTransactionDialog(
    tx: CashTransaction,
    defaultRoleLabel: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        amount: Long,
        category: String,
        source: String?,
        paymentMethod: String?,
        note: String?,
        receiptUri: Uri?,
        roleLabel: String,
        dateMillis: Long
    ) -> Unit
) {
    val isIncome = tx.parsedType == TransactionType.INCOME
    val categories = if (isIncome) CoupleCashCategories.incomeCategories else CoupleCashCategories.expenseCategories

    var amountText by remember { mutableStateOf(tx.amount.toString()) }
    var selectedCategory by remember { mutableStateOf(tx.category) }
    var sourceText by remember { mutableStateOf(tx.source ?: "") }
    var selectedPaymentMethod by remember { mutableStateOf(tx.paymentMethod ?: CoupleCashCategories.paymentMethods.first()) }
    var noteText by remember { mutableStateOf(tx.note ?: "") }
    var selectedDateMillis by remember { mutableLongStateOf(tx.transactionDateMillis) }
    var newReceiptUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) newReceiptUri = uri
    }

    val parsedAmount = amountText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${tx.parsedType.label}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RupiahAmountField(
                    valueText = amountText,
                    onValueChange = { amountText = it },
                    label = "Nominal (IDR)"
                )
                Text("Kategori", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                if (isIncome) {
                                    sourceText = cat
                                }
                            },
                            label = { Text(cat) }
                        )
                    }
                }
                if (isIncome) {
                    androidx.compose.material3.OutlinedTextField(
                        value = sourceText,
                        onValueChange = { sourceText = it.take(100) },
                        label = { Text("Sumber Pemasukan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Metode Pembayaran", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CoupleCashCategories.paymentMethods.forEach { method ->
                            FilterChip(
                                selected = selectedPaymentMethod == method,
                                onClick = { selectedPaymentMethod = method },
                                label = { Text(method) }
                            )
                        }
                    }
                }
                DatePickerField(
                    label = "Tanggal",
                    selectedMillis = selectedDateMillis,
                    onDateSelected = { selectedDateMillis = it }
                )
                androidx.compose.material3.OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(300) },
                    label = { Text("Catatan") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (newReceiptUri != null || !tx.receiptUrl.isNullOrBlank()) "Ganti Foto Bukti"
                            else "Lampirkan Foto Bukti"
                        )
                    }
                    if (newReceiptUri != null) {
                        AsyncImage(
                            model = newReceiptUri,
                            contentDescription = "Foto bukti baru",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        parsedAmount,
                        selectedCategory,
                        if (isIncome) sourceText else null,
                        if (!isIncome) selectedPaymentMethod else null,
                        noteText,
                        newReceiptUri,
                        defaultRoleLabel,
                        selectedDateMillis
                    )
                },
                enabled = !isBusy && parsedAmount > 0L
            ) {
                Text("Simpan Perubahan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
