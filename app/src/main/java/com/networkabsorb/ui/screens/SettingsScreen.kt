package com.networkabsorb.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networkabsorb.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                subtitle = "Required for HTTPS interception. Tap to export, then install via Settings → Security.",
                icon     = Icons.Default.Security
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.exportCaCert() }) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export CA")
                    }
                    Button(onClick = {
                        // Navigate user to certificate installer
                        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open Security")
                    }
                }
                uiState.exportedCaFile?.let { file ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Exported to: ${file.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
            SectionHeader("AI Insights")

            SettingsCard(
                title    = "Prediction Engine",
                subtitle = "View learned URL patterns and prefetch predictions.",
                icon     = Icons.Default.Psychology
            ) {
                Button(onClick = { viewModel.refreshAiInsights() }) {
                    Text("Refresh Insights")
                }
                if (uiState.aiInsights.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    uiState.aiInsights.forEach { (k, v) ->
                        Text(
                            "$k: $v",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

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
