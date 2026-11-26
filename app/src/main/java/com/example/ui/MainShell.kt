package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.model.Household
import com.example.model.TransactionType
import com.example.notifications.CoupleCashNotificationHelper
import com.example.ui.activity.ActivityScreen
import com.example.ui.budget.BudgetScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.reports.ReportsScreen
import com.example.ui.savings.SavingsScreen
import com.example.ui.theme.DarkEmeraldPrimary
import com.example.ui.theme.DarkExpenseRed
import com.example.ui.theme.DarkExpenseRedBg
import com.example.ui.theme.DarkIncomeGreen
import com.example.ui.theme.DarkIncomeGreenBg
import com.example.ui.theme.DarkSavingsAmber
import com.example.ui.theme.DarkSavingsAmberBg
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedBg
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.SavingsAmber
import com.example.ui.theme.SavingsAmberBg
import com.example.ui.transactions.AddTransactionSheet
import com.example.ui.transactions.TransactionsScreen
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object TransactionsRoute

@Serializable
data object SavingsRoute

@Serializable
data object BudgetRoute

@Serializable
data object ProfileRoute

@Serializable
data object ReportsRoute

@Serializable
data object ActivityRoute

private data class NavTab(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val testTag: String
)

@Composable
fun MainShell(
    viewModel: CoupleCashViewModel,
    household: Household,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val savingsGoals by viewModel.savingsGoals.collectAsStateWithLifecycle()
    val savingsTransactions by viewModel.savingsTransactions.collectAsStateWithLifecycle()
    val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val selectedMonthKey by viewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val budgetEvaluations by viewModel.budgetEvaluations.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val generatedInvite by viewModel.generatedInvite.collectAsStateWithLifecycle()
    val invitePreviewState by viewModel.invitePreviewState.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val defaultRoleLabel = members.firstOrNull { it.uid == viewModel.currentUserId }?.partnerLabel
        ?: userProfile?.partnerRoleLabel
        ?: "Suami"

    var showQuickAddChooser by remember { mutableStateOf(false) }
    var addSheetInitialType by remember { mutableStateOf<TransactionType?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.notificationEvents.collect { event ->
            CoupleCashNotificationHelper.showNotification(
                context = context,
                id = event.id,
                title = event.title,
                body = event.body
            )
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isSubScreen = currentDestination?.hasRoute<ReportsRoute>() == true ||
        currentDestination?.hasRoute<ActivityRoute>() == true

    val tabs = listOf(
        NavTab(stringResource(R.string.nav_home), Icons.Rounded.Home, DashboardRoute, "tab_dashboard"),
        NavTab(stringResource(R.string.nav_transactions), Icons.Rounded.ReceiptLong, TransactionsRoute, "tab_transactions"),
        NavTab(stringResource(R.string.nav_savings), Icons.Rounded.Savings, SavingsRoute, "tab_savings"),
        NavTab(stringResource(R.string.nav_budget), Icons.Rounded.AccountBalance, BudgetRoute, "tab_budget"),
        NavTab(stringResource(R.string.nav_profile), Icons.Rounded.Person, ProfileRoute, "tab_profile")
    )

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && !isSubScreen) {
                    val isDarkNav = isSystemInDarkTheme()
                    val activeLabelColor = if (isDarkNav) DarkEmeraldPrimary else EmeraldPrimary
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = activeLabelColor,
                                    indicatorColor = EmeraldPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!isSubScreen) {
                    FloatingActionButton(
                        onClick = { showQuickAddChooser = true },
                        containerColor = EmeraldPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_quick_add")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_add_transaction)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isSubScreen) {
                    val isDarkRail = isSystemInDarkTheme()
                    val activeLabelColor = if (isDarkRail) DarkEmeraldPrimary else EmeraldPrimary
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(12.dp))
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = activeLabelColor,
                                    indicatorColor = EmeraldPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute,
                        modifier = Modifier
                            .widthIn(max = 920.dp)
                            .fillMaxSize()
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                household = household,
                                members = members,
                                summary = dashboardSummary,
                                transactions = transactions,
                                savingsGoals = savingsGoals,
                                activityLogs = activityLogs,
                                syncStatus = syncStatus,
                                selectedMonthKey = selectedMonthKey,
                                onShiftMonth = viewModel::shiftMonth,
                                onOpenReports = { navController.navigate(ReportsRoute) },
                                onOpenTransactions = { navigateToTab(TransactionsRoute) },
                                onOpenSavings = { navigateToTab(SavingsRoute) },
                                onOpenBudget = { navigateToTab(BudgetRoute) },
                                onOpenActivity = { navController.navigate(ActivityRoute) }
                            )
                        }
                        composable<TransactionsRoute> {
                            TransactionsScreen(
                                transactions = transactions,
                                defaultRoleLabel = defaultRoleLabel,
                                isBusy = isBusy,
                                onUpdateTransaction = viewModel::updateTransaction,
                                onDeleteTransaction = viewModel::deleteTransaction,
                                onRestoreTransaction = viewModel::restoreTransaction,
                                onOpenReports = { navController.navigate(ReportsRoute) }
                            )
                        }
                        composable<SavingsRoute> {
                            SavingsScreen(
                                savingsGoals = savingsGoals,
                                savingsTransactions = savingsTransactions,
                                totalSavings = dashboardSummary.totalSavings,
                                isBusy = isBusy,
                                onCreateGoal = viewModel::createSavingsGoal,
                                onUpdateGoal = viewModel::updateSavingsGoal,
                                onDeleteGoal = viewModel::deleteSavingsGoal,
                                onRecordMovement = viewModel::recordSavingsMovement,
                                onRecordOpeningBalance = viewModel::recordOpeningSavingsBalance
                            )
                        }
                        composable<BudgetRoute> {
                            BudgetScreen(
                                selectedMonthKey = selectedMonthKey,
                                budgetEvaluations = budgetEvaluations,
                                isBusy = isBusy,
                                onShiftMonth = viewModel::shiftMonth,
                                onSaveBudget = viewModel::upsertBudget,
                                onDeleteBudget = viewModel::deleteBudget
                            )
                        }
                        composable<ProfileRoute> {
                            ProfileScreen(
                                currentUserId = viewModel.currentUserId,
                                userProfile = userProfile,
                                household = household,
                                members = members,
                                activityLogs = activityLogs,
                                generatedInvite = generatedInvite,
                                invitePreviewState = invitePreviewState,
                                isBusy = isBusy,
                                onUpdateRoleLabel = viewModel::updatePartnerRoleLabel,
                                onGenerateInviteCode = viewModel::generateInviteCode,
                                onRevokeInviteCode = viewModel::revokeInviteCode,
                                onPreviewInviteCode = viewModel::previewInviteCode,
                                onJoinInviteCode = { code, role ->
                                    viewModel.joinHouseholdByCode(code, role) {
                                        navigateToTab(DashboardRoute)
                                    }
                                },
                                onResetInvitePreview = viewModel::resetInvitePreview,
                                onRemoveOrLeaveMember = viewModel::removeOrLeavePartner,
                                onUpdateNotificationPrefs = viewModel::updateNotificationPrefs,
                                onOpenActivity = { navController.navigate(ActivityRoute) },
                                onResetAllData = viewModel::resetAllHouseholdData,
                                onSignOut = onSignOut,
                                onShowMessage = { msg -> viewModel.emitMessage(msg) }
                            )
                        }
                        composable<ReportsRoute> {
                            ReportsScreen(
                                household = household,
                                transactions = transactions,
                                savingsTransactions = savingsTransactions,
                                budgets = budgets,
                                selectedMonthKey = selectedMonthKey,
                                onBack = { navController.popBackStack() },
                                onMessage = { msg -> viewModel.emitMessage(msg) }
                            )
                        }
                        composable<ActivityRoute> {
                            ActivityScreen(
                                activityLogs = activityLogs,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickAddChooser) {
        AlertDialog(
            onDismissRequest = { showQuickAddChooser = false },
            title = { Text("Catat Aktivitas Keuangan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isDark = isSystemInDarkTheme()
                    QuickActionOptionCard(
                        icon = Icons.Rounded.ArrowDownward,
                        title = "Tambah Pemasukan",
                        subtitle = "Gaji, bonus, bisnis, freelance, hadiah",
                        tint = if (isDark) DarkIncomeGreen else IncomeGreen,
                        iconContainerColor = if (isDark) DarkIncomeGreenBg else IncomeGreenBg,
                        cardContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else IncomeGreenBg.copy(alpha = 0.72f),
                        testTag = "quick_add_income",
                        onClick = {
                            showQuickAddChooser = false
                            addSheetInitialType = TransactionType.INCOME
                        }
                    )
                    QuickActionOptionCard(
                        icon = Icons.Rounded.ArrowUpward,
                        title = "Tambah Pengeluaran",
                        subtitle = "Belanja, makan, tagihan, cicilan, dll.",
                        tint = if (isDark) DarkExpenseRed else ExpenseRed,
                        iconContainerColor = if (isDark) DarkExpenseRedBg else ExpenseRedBg,
                        cardContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else ExpenseRedBg.copy(alpha = 0.72f),
                        testTag = "quick_add_expense",
                        onClick = {
                            showQuickAddChooser = false
                            addSheetInitialType = TransactionType.EXPENSE
                        }
                    )
                    QuickActionOptionCard(
                        icon = Icons.Rounded.Savings,
                        title = "Setoran / Tarik Tabungan",
                        subtitle = "Pindahkan kas ke target tabungan (tidak mengurangi anggaran)",
                        tint = if (isDark) DarkSavingsAmber else SavingsAmber,
                        iconContainerColor = if (isDark) DarkSavingsAmberBg else SavingsAmberBg,
                        cardContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else SavingsAmberBg.copy(alpha = 0.72f),
                        testTag = "quick_add_savings",
                        onClick = {
                            showQuickAddChooser = false
                            navigateToTab(SavingsRoute)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickAddChooser = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    addSheetInitialType?.let { initialType ->
        AddTransactionSheet(
            initialType = initialType,
            defaultRoleLabel = defaultRoleLabel,
            isBusy = isBusy,
            onDismiss = { addSheetInitialType = null },
            onSubmit = { type, amount, category, source, paymentMethod, note, receiptUri, roleLabel, dateMillis, clientMutationId ->
                viewModel.addTransaction(
                    type = type,
                    amount = amount,
                    category = category,
                    source = source,
                    paymentMethod = paymentMethod,
                    note = note,
                    receiptUri = receiptUri,
                    roleLabel = roleLabel,
                    dateMillis = dateMillis,
                    clientMutationId = clientMutationId,
                    onDone = { addSheetInitialType = null }
                )
            }
        )
    }
}

@Composable
private fun QuickActionOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    iconContainerColor: Color,
    cardContainerColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = cardContainerColor,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconContainerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
