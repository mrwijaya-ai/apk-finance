package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActivityLog
import com.example.model.CashTransaction
import com.example.model.Household
import com.example.model.HouseholdMember
import com.example.model.SavingsGoal
import com.example.model.SyncStatus
import com.example.model.TransactionType
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MemberAvatar
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.VicoIncomeExpenseChart
import com.example.ui.theme.DarkEmeraldPrimary
import com.example.ui.theme.DarkExpenseRed
import com.example.ui.theme.DarkExpenseRedBg
import com.example.ui.theme.DarkIncomeGreen
import com.example.ui.theme.DarkIncomeGreenBg
import com.example.ui.theme.DarkSavingsAmber
import com.example.ui.theme.DarkSavingsAmberBg
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryDark
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedBg
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SavingsAmber
import com.example.ui.theme.SavingsAmberBg
import com.example.util.CurrencyFormatter
import com.example.util.DashboardSummary
import com.example.util.FinanceCalculator

@Composable
fun DashboardScreen(
    household: Household,
    members: List<HouseholdMember>,
    summary: DashboardSummary,
    transactions: List<CashTransaction>,
    savingsGoals: List<SavingsGoal>,
    activityLogs: List<ActivityLog>,
    syncStatus: SyncStatus,
    selectedMonthKey: String,
    onShiftMonth: (Int) -> Unit,
    onOpenReports: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTransactions = remember(transactions) { transactions.filter { !it.deleted } }
    val chartPoints = remember(activeTransactions, selectedMonthKey) {
        FinanceCalculator.lastSixMonthsSeries(activeTransactions, selectedMonthKey)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row: Household + Sync + Partner Avatars
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = household.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    SyncStatusBadge(status = syncStatus)
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    members.take(2).forEach { member ->
                        MemberAvatar(
                            name = member.displayName,
                            photoUrl = member.photoUrl,
                            sizeDp = 38
                        )
                    }
                }
            }
        }

        // Month Switcher
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

        // Hero Balance Card (WCAG AAA high-contrast Navy -> Deep Emerald surface)
        item {
            val heroLabelColor = Color(0xFFF1F5F9)
            val heroTileBg = NavySecondary.copy(alpha = 0.56f)
            val heroTileBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NavySecondary, NavySurface, EmeraldPrimaryDark)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Saldo Kas",
                                style = MaterialTheme.typography.labelLarge,
                                color = heroLabelColor,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = CurrencyFormatter.formatRupiah(summary.cashBalance),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = heroTileBg,
                            border = heroTileBorder,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable(onClick = onOpenReports)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Assessment,
                                    contentDescription = "Buka laporan",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Laporan",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = heroTileBg,
                            border = heroTileBorder,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Total Tabungan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroLabelColor,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = CurrencyFormatter.formatRupiah(summary.totalSavings),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            color = heroTileBg,
                            border = heroTileBorder,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Total Aset Keluarga",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroLabelColor,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = CurrencyFormatter.formatRupiah(summary.totalAssets),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (!summary.lastTransactionLabel.isNullOrBlank()) {
                        Surface(
                            color = NavySecondary.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Terakhir: ${summary.lastTransactionLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Monthly Income & Expense Cards
        item {
            val isDark = isSystemInDarkTheme()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricMiniCard(
                    title = "Pemasukan Bulan Ini",
                    amount = summary.monthlyIncome,
                    accent = if (isDark) DarkIncomeGreen else IncomeGreen,
                    bg = if (isDark) DarkIncomeGreenBg else IncomeGreenBg,
                    icon = Icons.Rounded.ArrowDownward,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "Pengeluaran Bulan Ini",
                    amount = summary.monthlyExpense,
                    accent = if (isDark) DarkExpenseRed else ExpenseRed,
                    bg = if (isDark) DarkExpenseRedBg else ExpenseRedBg,
                    icon = Icons.Rounded.ArrowUpward,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Monthly Budget Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBudget),
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
                        Text(
                            text = "Anggaran Bulanan",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onOpenBudget) {
                            Text("Atur")
                        }
                    }

                    if (summary.totalMonthlyBudgetLimit <= 0L) {
                        Text(
                            text = "Belum ada anggaran bulan ini. Ketuk Atur untuk menentukan batas belanja keluarga.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val isDark = isSystemInDarkTheme()
                        val overColor = if (isDark) DarkExpenseRed else ExpenseRed
                        val warnColor = if (isDark) DarkSavingsAmber else SavingsAmber
                        val okColor = if (isDark) DarkEmeraldPrimary else EmeraldPrimary
                        val okIncomeColor = if (isDark) DarkIncomeGreen else IncomeGreen
                        val isOver = summary.totalBudgetPercentUsed >= 100
                        val isWarn = summary.totalBudgetPercentUsed in 80..99
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Anggaran Awal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.formatRupiah(summary.totalMonthlyBudgetLimit),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (summary.totalMonthlyBudgetRemaining >= 0L) "Sisa Anggaran" else "Melebihi Anggaran",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOver) overColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.formatRupiah(summary.totalMonthlyBudgetRemaining),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isOver) overColor else okIncomeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { (summary.totalBudgetPercentUsed / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = when {
                                isOver -> overColor
                                isWarn -> warnColor
                                else -> okColor
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Terpakai ${summary.totalBudgetPercentUsed}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOver) overColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isOver || isWarn) {
                                val pillBg = when {
                                    isOver && isDark -> DarkExpenseRedBg
                                    isOver -> ExpenseRedBg
                                    isDark -> DarkSavingsAmberBg
                                    else -> SavingsAmberBg
                                }
                                val pillFg = when {
                                    isOver -> overColor
                                    else -> warnColor
                                }
                                Surface(
                                    color = pillBg,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = pillFg,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isOver) "Terlampaui" else "Hampir Habis",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = pillFg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vico Income vs Expense Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grafik Pemasukan vs Pengeluaran",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onOpenReports) {
                            Text("Detail")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    VicoIncomeExpenseChart(points = chartPoints)
                }
            }
        }

        // Savings Goals Progress Preview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progres Target Tabungan",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onOpenSavings) {
                            Text("Lihat Semua")
                        }
                    }
                    if (savingsGoals.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.Savings,
                            title = "Belum Ada Target Tabungan",
                            subtitle = "Buat target seperti Dana Darurat atau Beli Rumah bersama pasangan.",
                            actionLabel = "Buat Target",
                            onAction = onOpenSavings
                        )
                    } else {
                        val isDark = isSystemInDarkTheme()
                        val goalAccent = if (isDark) DarkEmeraldPrimary else EmeraldPrimary
                        savingsGoals.take(3).forEach { goal ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(goal.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${goal.progressPercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = goalAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { goal.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = goalAccent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "${CurrencyFormatter.formatRupiah(goal.currentAmount)} / ${CurrencyFormatter.formatRupiah(goal.targetAmount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Transaksi Terbaru", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenTransactions) {
                    Text("Semua")
                }
            }
        }

        if (activeTransactions.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.History,
                    title = "Belum Ada Transaksi",
                    subtitle = "Tekan tombol + di kanan bawah untuk mencatat pemasukan atau pengeluaran pertama."
                )
            }
        } else {
            items(activeTransactions.take(5), key = { it.id }) { tx ->
                DashboardTransactionRow(tx = tx)
            }
        }

        // Recent Partner Activity
        if (activityLogs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aktivitas Pasangan Terbaru", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenActivity) {
                        Text("Lihat Semua")
                    }
                }
            }
            items(activityLogs.take(4), key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = log.actorName, photoUrl = null, sizeDp = 32)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.detail, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = log.createdAt?.toDate()?.time?.let { CurrencyFormatter.formatDateTime(it) } ?: "Baru saja",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    amount: Long,
    accent: Color,
    bg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bg,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyFormatter.formatRupiah(amount),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardTransactionRow(tx: CashTransaction) {
    val type = tx.parsedType
    val isDark = isSystemInDarkTheme()
    val (color, sign) = when (type) {
        TransactionType.INCOME -> (if (isDark) DarkIncomeGreen else IncomeGreen) to "+"
        TransactionType.EXPENSE -> (if (isDark) DarkExpenseRed else ExpenseRed) to "-"
        TransactionType.SAVINGS_DEPOSIT -> (if (isDark) DarkSavingsAmber else SavingsAmber) to "->"
        TransactionType.SAVINGS_WITHDRAWAL -> (if (isDark) DarkEmeraldPrimary else EmeraldPrimary) to "<-"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.category, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Dicatat oleh ${tx.recordedByRoleLabel} (${tx.recordedByName}) • ${CurrencyFormatter.formatDateShort(tx.transactionDateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$sign${CurrencyFormatter.formatRupiah(tx.amount)}",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
