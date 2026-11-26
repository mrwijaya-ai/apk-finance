package com.example.ui.profile

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ActivityLog
import com.example.model.CoupleCashCategories
import com.example.model.Household
import com.example.model.HouseholdMember
import com.example.model.InviteCode
import com.example.model.UserProfile
import com.example.ui.InvitePreviewState
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

@Composable
fun ProfileScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    household: Household,
    members: List<HouseholdMember>,
    activityLogs: List<ActivityLog>,
    generatedInvite: InviteCode?,
    invitePreviewState: InvitePreviewState,
    isBusy: Boolean,
    onUpdateRoleLabel: (String) -> Unit,
    onGenerateInviteCode: () -> Unit,
    onRevokeInviteCode: (String) -> Unit,
    onPreviewInviteCode: (String) -> Unit,
    onJoinInviteCode: (String, String) -> Unit,
    onResetInvitePreview: () -> Unit,
    onRemoveOrLeaveMember: (String) -> Unit,
    onUpdateNotificationPrefs: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onOpenActivity: () -> Unit,
    onResetAllData: (onDone: () -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isOwner = household.ownerId == currentUserId
    val activeCode = generatedInvite?.code ?: household.activeInviteCode

    var showJoinDialog by remember { mutableStateOf(false) }
    var showResetDataDialog by remember { mutableStateOf(false) }
    var expandRecentActivity by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<HouseholdMember?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onShowMessage(
            if (granted) "Izin notifikasi diaktifkan" else "Izin notifikasi ditolak oleh pengguna"
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Profil & Pengaturan", style = MaterialTheme.typography.headlineSmall)
        }

        // Google User Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(
                            name = userProfile?.displayName ?: "Pengguna",
                            photoUrl = userProfile?.photoUrl,
                            sizeDp = 52
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.displayName ?: "Memuat...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = userProfile?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val joinedDate = userProfile?.createdAt?.toDate()?.time
                            if (joinedDate != null) {
                                Text(
                                    text = "Bergabung: ${CurrencyFormatter.formatDateShort(joinedDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text("Peran Pencatatan", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoupleCashCategories.partnerRoleLabels.forEach { label ->
                            FilterChip(
                                selected = (userProfile?.partnerRoleLabel ?: "Suami") == label,
                                onClick = { onUpdateRoleLabel(label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Household Members & Invite Code Card
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
                        Column {
                            Text(household.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Anggota Aktif (${household.memberUids.size}/2)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showJoinDialog = true },
                            modifier = Modifier.testTag("open_join_code_dialog")
                        ) {
                            Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gabung Kode")
                        }
                    }

                    members.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                MemberAvatar(name = member.displayName, photoUrl = member.photoUrl, sizeDp = 38)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${member.parsedRole.label} • ${member.partnerLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (household.memberUids.size == 2 && member.uid != household.ownerId && (isOwner || member.uid == currentUserId)) {
                                TextButton(
                                    onClick = { memberToRemove = member },
                                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed)
                                ) {
                                    Text(if (member.uid == currentUserId) "Keluar" else "Hapus")
                                }
                            }
                        }
                    }

                    if (isOwner && household.memberUids.size < 2) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Key, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Kode Undangan Pasangan", style = MaterialTheme.typography.titleSmall)
                                }
                                if (!activeCode.isNullOrBlank()) {
                                    Text(
                                        text = activeCode,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    val expMillis = generatedInvite?.expiresAt?.toDate()?.time
                                    if (expMillis != null) {
                                        Text(
                                            text = "Berlaku hingga ${CurrencyFormatter.formatDateTime(expMillis)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            clipboard.setText(AnnotatedString(activeCode))
                                            onShowMessage("Kode undangan disalin")
                                        }) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Salin")
                                        }
                                        OutlinedButton(onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Yuk gabung ke Buku Kas Bersama \"${household.name}\" di aplikasi CoupleCash dengan kode undangan: $activeCode"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Kode CoupleCash"))
                                        }) {
                                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Bagikan")
                                        }
                                        IconButton(onClick = { onRevokeInviteCode(activeCode) }) {
                                            Icon(Icons.Rounded.DeleteForever, contentDescription = "Batalkan kode", tint = ExpenseRed)
                                        }
                                    }
                                }
                                Button(
                                    onClick = onGenerateInviteCode,
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("generate_invite_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (activeCode.isNullOrBlank()) "Buat Kode Undangan" else "Buat Kode Baru")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notification Preferences Card
        item {
            val p = userProfile ?: UserProfile()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pengaturan Notifikasi", style = MaterialTheme.typography.titleMedium)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            TextButton(onClick = {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Izin")
                            }
                        }
                    }

                    NotifToggleRow("Aktivitas Transaksi Pasangan", p.notifPartnerTx) {
                        onUpdateNotificationPrefs(it, p.notifIncome, p.notifExpense, p.notifSavings, p.notifBudget, p.notifGoal)
                    }
                    NotifToggleRow("Pemasukan Baru", p.notifIncome) {
                        onUpdateNotificationPrefs(p.notifPartnerTx, it, p.notifExpense, p.notifSavings, p.notifBudget, p.notifGoal)
                    }
                    NotifToggleRow("Pengeluaran Baru", p.notifExpense) {
                        onUpdateNotificationPrefs(p.notifPartnerTx, p.notifIncome, it, p.notifSavings, p.notifBudget, p.notifGoal)
                    }
                    NotifToggleRow("Setoran Tabungan", p.notifSavings) {
                        onUpdateNotificationPrefs(p.notifPartnerTx, p.notifIncome, p.notifExpense, it, p.notifBudget, p.notifGoal)
                    }
                    NotifToggleRow("Peringatan Anggaran Hampir Habis", p.notifBudget) {
                        onUpdateNotificationPrefs(p.notifPartnerTx, p.notifIncome, p.notifExpense, p.notifSavings, it, p.notifGoal)
                    }
                    NotifToggleRow("Target Tabungan Tercapai", p.notifGoal) {
                        onUpdateNotificationPrefs(p.notifPartnerTx, p.notifIncome, p.notifExpense, p.notifSavings, p.notifBudget, it)
                    }
                }
            }
        }

        // Activity Log Breakdown Section (Compact & Simple, Synced with Current Data)
        item {
            val isDark = isSystemInDarkTheme()
            val txColor = if (isDark) DarkIncomeGreen else IncomeGreen
            val savColor = if (isDark) DarkSavingsAmber else SavingsAmber
            val budgetColor = if (isDark) DarkEmeraldPrimary else EmeraldPrimary
            val auditColor = if (isDark) DarkExpenseRed else ExpenseRed

            val txTypes = setOf("INCOME_ADDED", "EXPENSE_ADDED")
            val savTypes = setOf("GOAL_CREATED", "SAVINGS_DEPOSIT", "SAVINGS_WITHDRAWAL", "SAVINGS_OPENING_BALANCE")
            val budgetTypes = setOf("BUDGET_UPDATED")
            val auditTypes = setOf("TRANSACTION_EDITED", "TRANSACTION_DELETED", "TRANSACTION_RESTORED")
            val categorizedTypes = txTypes + savTypes + budgetTypes + auditTypes

            val financialLogs = remember(activityLogs) {
                activityLogs.filter { it.activityType in categorizedTypes }
            }
            val txCount = remember(financialLogs) {
                financialLogs.count { it.activityType in txTypes }
            }
            val savCount = remember(financialLogs) {
                financialLogs.count { it.activityType in savTypes }
            }
            val budgetCount = remember(financialLogs) {
                financialLogs.count { it.activityType in budgetTypes }
            }
            val auditCount = remember(financialLogs) {
                financialLogs.count { it.activityType in auditTypes }
            }
            val partnerBreakdown = remember(financialLogs) {
                financialLogs
                    .groupBy { it.actorRoleLabel.ifBlank { "Anggota" } }
                    .entries
                    .joinToString(" • ") { "${it.key}: ${it.value.size}" }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text("Riwayat Aktivitas Pasangan", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (financialLogs.isEmpty()) "Total 0 aktivitas (sesuai data saat ini)"
                            else "Total ${financialLogs.size} aktivitas ($partnerBreakdown)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityBreakdownTile("Transaksi", txCount, txColor, Modifier.weight(1f))
                        ActivityBreakdownTile("Tabungan", savCount, savColor, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityBreakdownTile("Anggaran", budgetCount, budgetColor, Modifier.weight(1f))
                        ActivityBreakdownTile("Edit & Hapus", auditCount, auditColor, Modifier.weight(1f))
                    }

                    if (expandRecentActivity && financialLogs.isNotEmpty()) {
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            financialLogs.take(3).forEach { log ->
                                Column {
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

                    if (financialLogs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { expandRecentActivity = !expandRecentActivity },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("toggle_recent_activity_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (expandRecentActivity) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (expandRecentActivity) "Ringkas" else "3 Terbaru")
                            }
                            Button(
                                onClick = onOpenActivity,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("open_full_activity_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Detail Semua")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Reset All Household Data Card
        item {
            val isDark = isSystemInDarkTheme()
            val dangerColor = if (isDark) DarkExpenseRed else ExpenseRed
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, dangerColor.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Reset Data Keseluruhan",
                        style = MaterialTheme.typography.titleMedium,
                        color = dangerColor
                    )
                    Text(
                        text = "Kosongkan seluruh transaksi, target tabungan, dan anggaran bulan ini ke Rp0 untuk memulai buku kas dari awal. Akun pasangan tetap terhubung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showResetDataDialog = true },
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor),
                        border = BorderStroke(1.dp, dangerColor.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("reset_all_data_button")
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Data Keseluruhan", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Sign Out
        item {
            val isDark = isSystemInDarkTheme()
            val dangerColor = if (isDark) DarkExpenseRed else ExpenseRed
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Keluar dari Akun Google")
            }
        }
    }

    if (showResetDataDialog) {
        ResetAllDataConfirmDialog(
            householdName = household.name,
            isBusy = isBusy,
            onDismiss = { showResetDataDialog = false },
            onConfirmReset = {
                onResetAllData { showResetDataDialog = false }
            }
        )
    }

    if (showJoinDialog) {
        JoinCodeDialog(
            defaultRoleLabel = userProfile?.partnerRoleLabel ?: "Istri",
            invitePreviewState = invitePreviewState,
            isBusy = isBusy,
            onPreview = onPreviewInviteCode,
            onJoin = { code, label ->
                onJoinInviteCode(code, label)
                showJoinDialog = false
            },
            onDismiss = {
                onResetInvitePreview()
                showJoinDialog = false
            }
        )
    }

    memberToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = {
                Text(if (target.uid == currentUserId) "Keluar dari Buku Kas?" else "Hapus Anggota?")
            },
            text = {
                Text(
                    if (target.uid == currentUserId) {
                        "Anda akan keluar dari buku kas \"${household.name}\"."
                    } else {
                        "Hapus ${target.displayName} dari buku kas \"${household.name}\"?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveOrLeaveMember(target.uid)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Konfirmasi")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ActivityBreakdownTile(
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun ResetAllDataConfirmDialog(
    householdName: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmReset: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()
    val dangerColor = if (isDark) DarkExpenseRed else ExpenseRed
    val canConfirm = confirmText.trim().equals("RESET", ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Reset Data Keseluruhan?", color = dangerColor) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Seluruh transaksi, saldo & target tabungan, dan anggaran pada \"$householdName\" akan dikosongkan ke Rp0. Data akun pasangan tidak terhapus.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Ketik RESET untuk konfirmasi:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it.take(16) },
                    placeholder = { Text("RESET") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_confirm_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmReset,
                enabled = !isBusy && canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                modifier = Modifier.testTag("confirm_reset_all_data_button")
            ) {
                Text(if (isBusy) "Mereset..." else "Reset Sekarang")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun NotifToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun JoinCodeDialog(
    defaultRoleLabel: String,
    invitePreviewState: InvitePreviewState,
    isBusy: Boolean,
    onPreview: (String) -> Unit,
    onJoin: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(defaultRoleLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gabung Kode Pasangan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(16) },
                    label = { Text("Kode Undangan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoupleCashCategories.partnerRoleLabels.forEach { l ->
                        FilterChip(selected = role == l, onClick = { role = l }, label = { Text(l) })
                    }
                }
                when (invitePreviewState) {
                    is InvitePreviewState.Loaded -> {
                        val p = invitePreviewState.preview
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Buku Kas: ${p.householdName}", style = MaterialTheme.typography.titleSmall)
                                Text("Pemilik: ${p.ownerName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is InvitePreviewState.Error -> {
                        Text(invitePreviewState.message, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            if (invitePreviewState is InvitePreviewState.Loaded) {
                Button(
                    onClick = { onJoin(invitePreviewState.preview.code, role) },
                    enabled = !isBusy
                ) { Text("Gabung Sekarang") }
            } else {
                Button(
                    onClick = { onPreview(code) },
                    enabled = !isBusy && code.length >= 6
                ) { Text("Periksa Kode") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
