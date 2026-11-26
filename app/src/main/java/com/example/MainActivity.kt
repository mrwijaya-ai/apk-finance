package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.CoupleCashRepository
import com.example.notifications.CoupleCashNotificationHelper
import com.example.ui.CoupleCashViewModel
import com.example.ui.MainShell
import com.example.ui.UiState
import com.example.ui.auth.AuthHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.pairing.HouseholdSetupScreen
import com.example.ui.theme.CoupleCashTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CoupleCashNotificationHelper.ensureChannel(this)
        setContent {
            CoupleCashTheme {
                CoupleCashRootApp()
            }
        }
    }
}

@Composable
private fun CoupleCashRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(user.uid) {
            val viewModel: CoupleCashViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        CoupleCashViewModel(
                            repository = CoupleCashRepository(app),
                            currentUserId = user.uid
                        )
                    }
                }
            )
            AuthenticatedCoupleCashFlow(
                viewModel = viewModel,
                fallbackDisplayName = user.displayName ?: "Pengguna CoupleCash",
                onSignOut = {
                    AuthHelper.signOut(credentialManager, scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedCoupleCashFlow(
    viewModel: CoupleCashViewModel,
    fallbackDisplayName: String,
    onSignOut: () -> Unit
) {
    val householdsState by viewModel.householdsState.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val activeHousehold by viewModel.activeHousehold.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val invitePreviewState by viewModel.invitePreviewState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, activeHousehold) {
        if (activeHousehold == null) {
            viewModel.uiMessages.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    when (val hhState = householdsState) {
        is UiState.Loading -> {
            Scaffold { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Menghubungkan Buku Kas Keluarga...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is UiState.Error -> {
            Scaffold { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Gagal Memuat Buku Kas",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = hhState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onSignOut) {
                            Text("Keluar & Masuk Ulang")
                        }
                    }
                }
            }
        }

        is UiState.Success -> {
            val household = activeHousehold
            if (household == null) {
                val profileName = (userProfileState as? UiState.Success)?.data?.displayName
                    ?.takeIf { it.isNotBlank() } ?: fallbackDisplayName
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        HouseholdSetupScreen(
                            userName = profileName,
                            isBusy = isBusy,
                            invitePreviewState = invitePreviewState,
                            onCreateHousehold = viewModel::createHousehold,
                            onPreviewCode = viewModel::previewInviteCode,
                            onJoinCode = { code, role -> viewModel.joinHouseholdByCode(code, role) },
                            onResetPreview = viewModel::resetInvitePreview,
                            onSignOut = onSignOut
                        )
                    }
                }
            } else {
                MainShell(
                    viewModel = viewModel,
                    household = household,
                    onSignOut = onSignOut
                )
            }
        }
    }
}
