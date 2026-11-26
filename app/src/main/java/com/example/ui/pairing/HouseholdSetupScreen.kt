package com.example.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CoupleCashCategories
import com.example.ui.InvitePreviewState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdSetupScreen(
    userName: String,
    isBusy: Boolean,
    invitePreviewState: InvitePreviewState,
    onCreateHousehold: (name: String, roleLabel: String) -> Unit,
    onPreviewCode: (code: String) -> Unit,
    onJoinCode: (code: String, roleLabel: String) -> Unit,
    onResetPreview: () -> Unit,
    onSignOut: () -> Unit
) {
    var householdName by remember { mutableStateOf("Buku Kas Keluarga") }
    var selectedRole by remember { mutableStateOf("Suami") }
    var inviteCodeInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hubungkan Buku Kas") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Keluar akun")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Halo, $userName!",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Pilih peran Anda lalu buat Buku Kas Bersama baru atau gabung menggunakan kode undangan dari pasangan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Peran Anda di Keluarga",
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

                // Option A: Create New Shared Ledger
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AddHome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Buat Buku Kas Bersama Baru",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        OutlinedTextField(
                            value = householdName,
                            onValueChange = { householdName = it.take(80) },
                            label = { Text("Nama Buku Kas") },
                            placeholder = { Text("Misal: Keuangan Keluarga Wijaya") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_household_name_input")
                        )
                        Button(
                            onClick = { onCreateHousehold(householdName, selectedRole) },
                            enabled = !isBusy && householdName.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_household_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Buat Buku Kas Sekarang")
                            }
                        }
                    }
                }

                // Option B: Join Partner Code
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.GroupAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Gabung Kode Pasangan",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = {
                                inviteCodeInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(16)
                                if (invitePreviewState !is InvitePreviewState.Idle) onResetPreview()
                            },
                            label = { Text("Kode Undangan") },
                            placeholder = { Text("Masukkan 8 karakter kode") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("join_code_input")
                        )

                        when (invitePreviewState) {
                            is InvitePreviewState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Memvalidasi kode undangan...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is InvitePreviewState.Error -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = invitePreviewState.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is InvitePreviewState.Loaded -> {
                                val preview = invitePreviewState.preview
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Rounded.VerifiedUser,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Buku Kas Ditemukan",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "Buku Kas: ${preview.householdName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Pemilik: ${preview.ownerName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onJoinCode(preview.code, selectedRole) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("confirm_join_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Konfirmasi & Gabung Sekarang")
                                }
                            }
                            InvitePreviewState.Idle -> Unit
                        }

                        if (invitePreviewState !is InvitePreviewState.Loaded) {
                            OutlinedButton(
                                onClick = { onPreviewCode(inviteCodeInput) },
                                enabled = !isBusy && inviteCodeInput.length >= 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("preview_code_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Periksa Kode Undangan")
                            }
                        }
                    }
                }
            }
        }
    }
}
