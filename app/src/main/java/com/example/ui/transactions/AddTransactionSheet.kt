package com.example.ui.transactions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.CoupleCashCategories
import com.example.model.TransactionType
import com.example.ui.components.DatePickerField
import com.example.ui.components.RupiahAmountField
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    initialType: TransactionType,
    defaultRoleLabel: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        type: TransactionType,
        amount: Long,
        category: String,
        source: String?,
        paymentMethod: String?,
        note: String?,
        receiptUri: Uri?,
        roleLabel: String,
        dateMillis: Long,
        clientMutationId: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var txType by remember {
        mutableStateOf(
            if (initialType == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        )
    }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember(txType) {
        mutableStateOf(
            if (txType == TransactionType.INCOME) CoupleCashCategories.incomeCategories.first()
            else CoupleCashCategories.expenseCategories.first()
        )
    }
    var sourceText by remember {
        mutableStateOf(
            if (initialType == TransactionType.INCOME) CoupleCashCategories.incomeCategories.first()
            else ""
        )
    }
    var selectedPaymentMethod by remember { mutableStateOf(CoupleCashCategories.paymentMethods.first()) }
    var noteText by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(defaultRoleLabel) }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    val clientMutationId = remember { UUID.randomUUID().toString() }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) receiptUri = uri
    }

    val categories = if (txType == TransactionType.INCOME) {
        CoupleCashCategories.incomeCategories
    } else {
        CoupleCashCategories.expenseCategories
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (txType == TransactionType.INCOME) "Catat Pemasukan" else "Catat Pengeluaran",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = txType == TransactionType.EXPENSE,
                    onClick = { txType = TransactionType.EXPENSE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Pengeluaran")
                }
                SegmentedButton(
                    selected = txType == TransactionType.INCOME,
                    onClick = {
                        txType = TransactionType.INCOME
                        if (sourceText.isBlank()) {
                            sourceText = CoupleCashCategories.incomeCategories.first()
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Pemasukan")
                }
            }

            RupiahAmountField(
                valueText = amountText,
                onValueChange = { amountText = it },
                label = "Nominal (IDR)",
                modifier = Modifier.testTag("transaction_amount_input")
            )

            Text("Kategori", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = cat
                            if (txType == TransactionType.INCOME) {
                                sourceText = cat
                            }
                        },
                        label = { Text(cat) }
                    )
                }
            }

            if (txType == TransactionType.INCOME) {
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it.take(100) },
                    label = { Text("Sumber Pemasukan") },
                    placeholder = { Text("Misal: PT Maju Jaya / Proyek Desain") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("Metode Pembayaran", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                label = "Tanggal Transaksi",
                selectedMillis = selectedDateMillis,
                onDateSelected = { selectedDateMillis = it }
            )

            Text(
                text = if (txType == TransactionType.INCOME) "Penerima / Pencatat" else "Pencatat Transaksi",
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoupleCashCategories.partnerRoleLabels.forEach { label ->
                    FilterChip(
                        selected = selectedRole == label,
                        onClick = { selectedRole = label },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it.take(300) },
                label = { Text("Catatan (Opsional)") },
                placeholder = { Text("Tambahkan keterangan transaksi...") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    Text(if (receiptUri == null) "Foto Bukti / Struk (Opsional)" else "Ganti Foto")
                }
                if (receiptUri != null) {
                    AsyncImage(
                        model = receiptUri,
                        contentDescription = "Pratinjau struk",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            val parsedAmount = amountText.toLongOrNull() ?: 0L
            Button(
                onClick = {
                    onSubmit(
                        txType,
                        parsedAmount,
                        selectedCategory,
                        if (txType == TransactionType.INCOME) sourceText else null,
                        if (txType == TransactionType.EXPENSE) selectedPaymentMethod else null,
                        noteText,
                        receiptUri,
                        selectedRole,
                        selectedDateMillis,
                        clientMutationId
                    )
                },
                enabled = !isBusy && parsedAmount > 0L,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (txType == TransactionType.INCOME) IncomeGreen else ExpenseRed
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_transaction_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (txType == TransactionType.INCOME) "Simpan Pemasukan" else "Simpan Pengeluaran")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
