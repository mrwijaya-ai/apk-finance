package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.SyncStatus
import com.example.ui.theme.DarkExpenseRed
import com.example.ui.theme.DarkIncomeGreen
import com.example.ui.theme.DarkIncomeGreenBg
import com.example.ui.theme.DarkSavingsAmber
import com.example.ui.theme.DarkSavingsAmberBg
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.SavingsAmber
import com.example.ui.theme.SavingsAmberBg
import com.example.ui.theme.SyncBlue
import com.example.util.CurrencyFormatter
import com.example.util.PeriodChartPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer

@Composable
fun SyncStatusBadge(status: SyncStatus, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val (bg, fg, icon, label) = when (status) {
        SyncStatus.SYNCED -> Quad(
            if (isDark) DarkIncomeGreenBg else IncomeGreenBg,
            if (isDark) DarkIncomeGreen else IncomeGreen,
            Icons.Rounded.CloudDone,
            stringResource(R.string.sync_synced)
        )
        SyncStatus.SYNCING -> Quad(
            if (isDark) DarkSavingsAmberBg else SavingsAmberBg,
            if (isDark) DarkSavingsAmber else SyncBlue,
            Icons.Rounded.CloudSync,
            stringResource(R.string.sync_syncing)
        )
        SyncStatus.OFFLINE -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Rounded.CloudOff,
            stringResource(R.string.sync_offline)
        )
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun MemberAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 36
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun RupiahAmountField(
    valueText: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val digitsOnly = valueText.filter { it.isDigit() }
    val parsed = digitsOnly.toLongOrNull() ?: 0L
    OutlinedTextField(
        value = digitsOnly,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(12)) },
        label = { Text(label) },
        prefix = { Text("Rp ", fontWeight = FontWeight.SemiBold) },
        supportingText = {
            if (parsed > 0L) {
                Text(CurrencyFormatter.formatRupiah(parsed))
            } else {
                Text("Contoh: 500000 untuk Rp500.000")
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    selectedMillis: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = CurrencyFormatter.formatDateShort(selectedMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = "Pilih tanggal")
            }
        },
        modifier = modifier.fillMaxWidth()
    )

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(onDateSelected)
                    showPicker = false
                }) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun VicoIncomeExpenseChart(
    points: List<PeriodChartPoint>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val hasData = points.any { it.income > 0L || it.expense > 0L }

    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(points.map { (it.income / 1000.0).coerceAtLeast(0.0) })
                    series(points.map { (it.expense / 1000.0).coerceAtLeast(0.0) })
                }
            }
        }
    }

    if (!hasData) {
        Text(
            text = "Belum ada data pemasukan atau pengeluaran untuk ditampilkan pada grafik.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 24.dp)
        )
        return
    }

    val bottomFormatter = CartesianValueFormatter { _, value, _ ->
        points.getOrNull(value.toInt())?.label ?: ""
    }
    val startFormatter = CartesianValueFormatter { _, value, _ ->
        val rp = (value * 1000.0).toLong()
        CurrencyFormatter.formatCompactRupiah(rp)
    }

    val isDark = isSystemInDarkTheme()
    val chartIncomeColor = if (isDark) DarkIncomeGreen else IncomeGreen
    val chartExpenseColor = if (isDark) DarkExpenseRed else ExpenseRed

    Column(modifier = modifier.fillMaxWidth()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(
                            fill = fill(chartIncomeColor),
                            thickness = 10.dp
                        ),
                        rememberLineComponent(
                            fill = fill(chartExpenseColor),
                            thickness = 10.dp
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter)
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            LegendDot(color = chartIncomeColor, label = "Pemasukan")
            Spacer(Modifier.width(18.dp))
            LegendDot(color = chartExpenseColor, label = "Pengeluaran")
        }
    }
}

@Composable
fun VicoSavingsGrowthChart(
    points: List<Pair<String, Long>>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val hasData = points.any { it.second > 0L }

    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(points.map { (it.second / 1000.0).coerceAtLeast(0.0) })
                }
            }
        }
    }

    if (!hasData) {
        Text(
            text = "Belum ada pertumbuhan setoran tabungan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 20.dp)
        )
        return
    }

    val bottomFormatter = CartesianValueFormatter { _, value, _ ->
        points.getOrNull(value.toInt())?.first ?: ""
    }
    val startFormatter = CartesianValueFormatter { _, value, _ ->
        CurrencyFormatter.formatCompactRupiah((value * 1000.0).toLong())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter)
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        )
        Spacer(Modifier.height(8.dp))
        val isDark = isSystemInDarkTheme()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            LegendDot(color = if (isDark) DarkSavingsAmber else SavingsAmber, label = "Akumulasi Tabungan")
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
