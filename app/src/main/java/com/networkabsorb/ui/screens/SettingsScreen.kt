package com.networkabsorb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networkabsorb.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    val certLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onCaCertInstalled() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            SectionHeader("Certificate Authority")

            SettingsCard(
                title    = "Install CA Certificate",
                subtitle = if (uiState.caCertReady) "Certificate ready ✓" else "Required for HTTPS interception",
                icon     = Icons.Default.Security
            ) {
                Button(onClick = {
                    try { certLauncher.launch(viewModel.buildCaInstallIntent()) }
                    catch (_: Exception) { viewModel.onCaCertInstalled() }
                }) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Install CA Certificate")
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("Cache")

            SettingsCard(
                title    = "Cache Statistics",
                subtitle = "${uiState.cacheEntryCount} entries · ${"%.1f".format(uiState.cacheSizeMb)} MB used",
                icon     = Icons.Default.Storage
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.purgeExpired() }) {
                        Text("Purge expired")
                    }
                    Button(
                        onClick = { viewModel.clearCache() },
                        colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear all")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(8.dp))
            SectionHeader("About")

            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Network Absorb", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0-PoC", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface)
                    Text("Dual-mode VPN proxy for offline internet caching",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            content()
        }
    }
}
