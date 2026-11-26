package com.example.ui.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ActivityLog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.DarkEmeraldPrimary
import com.example.ui.theme.DarkExpenseRed
import com.example.ui.theme.DarkIncomeGreen
import com.example.ui.theme.DarkSavingsAmber
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber
import com.example.util.CurrencyFormatter

private enum class ActivityFilter(val label: String) {
    ALL("Semua"),
    TRANSACTIONS("Transaksi"),
    SAVINGS("Tabungan"),
    BUDGET("Anggaran"),
    AUDIT("Edit & Hapus")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    activityLogs: List<ActivityLog>,
    onBack: () -> Unit
) {
    var filter by remember { mutableStateOf(ActivityFilter.ALL) }

    val filtered = remember(activityLogs, filter) {
        when (filter) {
            ActivityFilter.ALL -> activityLogs
            ActivityFilter.TRANSACTIONS -> activityLogs.filter {
                it.activityType in setOf(
                    "INCOME_ADDED",
                    "EXPENSE_ADDED",
                    "TRANSACTION_EDITED",
                    "TRANSACTION_DELETED",
                    "TRANSACTION_RESTORED"
                )
            }
            ActivityFilter.SAVINGS -> activityLogs.filter {
                it.activityType in setOf(
                    "GOAL_CREATED",
                    "SAVINGS_DEPOSIT",
                    "SAVINGS_WITHDRAWAL",
                    "SAVINGS_OPENING_BALANCE"
                )
            }
            ActivityFilter.BUDGET -> activityLogs.filter { it.activityType == "BUDGET_UPDATED" }
            ActivityFilter.AUDIT -> activityLogs.filter {
                it.activityType in setOf(
                    "TRANSACTION_EDITED",
                    "TRANSACTION_DELETED",
                    "TRANSACTION_RESTORED",
                    "SYSTEM_RESET"
                )
            }
        }
    }

    val filterCounts = remember(activityLogs) {
        mapOf(
            ActivityFilter.ALL to activityLogs.size,
            ActivityFilter.TRANSACTIONS to activityLogs.count {
                it.activityType in setOf("INCOME_ADDED", "EXPENSE_ADDED", "TRANSACTION_EDITED", "TRANSACTION_DELETED", "TRANSACTION_RESTORED")
            },
            ActivityFilter.SAVINGS to activityLogs.count {
                it.activityType in setOf("GOAL_CREATED", "SAVINGS_DEPOSIT", "SAVINGS_WITHDRAWAL", "SAVINGS_OPENING_BALANCE")
            },
            ActivityFilter.BUDGET to activityLogs.count { it.activityType == "BUDGET_UPDATED" },
            ActivityFilter.AUDIT to activityLogs.count {
                it.activityType in setOf("TRANSACTION_EDITED", "TRANSACTION_DELETED", "TRANSACTION_RESTORED", "SYSTEM_RESET")
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktivitas Bersama") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Transparansi penuh: semua pencatatan, perubahan nominal, dan penghapusan tersimpan sebagai audit log permanen dengan timestamp server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityFilter.entries.forEach { item ->
                    val count = filterCounts[item] ?: 0
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text("${item.label} ($count)") }
                    )
                }
            }

            if (filtered.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.History,
                            title = "Belum Ada Aktivitas",
                            subtitle = "Aktivitas keuangan Suami dan Istri akan tampil real-time di sini."
                        )
                    }
                }
            } else {
                val isDark = isSystemInDarkTheme()
                val incColor = if (isDark) DarkIncomeGreen else IncomeGreen
                val expColor = if (isDark) DarkExpenseRed else ExpenseRed
                val savColor = if (isDark) DarkSavingsAmber else SavingsAmber
                val priColor = if (isDark) DarkEmeraldPrimary else EmeraldPrimary
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { log ->
                        val (badgeLabel, badgeColor) = when (log.activityType) {
                            "INCOME_ADDED" -> "Pemasukan" to incColor
                            "EXPENSE_ADDED" -> "Pengeluaran" to expColor
                            "SAVINGS_DEPOSIT", "SAVINGS_WITHDRAWAL", "GOAL_CREATED", "SAVINGS_OPENING_BALANCE" -> "Tabungan" to savColor
                            "BUDGET_UPDATED" -> "Anggaran" to priColor
                            "TRANSACTION_EDITED" -> "Edit" to savColor
                            "TRANSACTION_DELETED" -> "Hapus" to expColor
                            "TRANSACTION_RESTORED" -> "Pulih" to incColor
                            "SYSTEM_RESET" -> "Reset" to expColor
                            else -> "Keluarga" to priColor
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberAvatar(name = log.actorName, photoUrl = null, sizeDp = 36)
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = badgeColor.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = log.actorRoleLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = log.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = log.createdAt?.toDate()?.time?.let {
                                            CurrencyFormatter.formatDateTime(it)
                                        } ?: "Sinkronisasi server...",
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
    }
}
