package com.networkabsorb.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkabsorb.ui.MainViewModel
import com.networkabsorb.vpn.InternetExtractorVpnService

/**
 * Main dashboard: big absorb/serve toggle, stats cards, quick navigation.
 */
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateTo: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // VPN permission launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startAbsorbing()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "Network Absorb",
                style      = MaterialTheme.typography.headlineLarge,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { onNavigateTo("settings") }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Big central absorb button
        AbsorbButton(
            running = uiState.vpnRunning,
            mode    = uiState.mode,
            onAbsorb = {
                val prepIntent = viewModel.prepareVpn()
                if (prepIntent != null) vpnPermissionLauncher.launch(prepIntent)
                else viewModel.startAbsorbing()
            },
            onServe = { viewModel.startServing() },
            onStop  = { viewModel.stopVpn() }
        )

        // Live session stats (shown while absorbing/serving)
        if (uiState.vpnRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label    = if (uiState.mode == InternetExtractorVpnService.Mode.ABSORB)
                                   "שאבתי" else "הגשתי",
                    value    = "${uiState.sessionRequests}",
                    unit     = "בקשות",
                    icon     = Icons.Default.SwapVert
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label    = "נפח סשן",
                    value    = if (uiState.sessionBytesKb >= 1024f)
                                   "%.1f".format(uiState.sessionBytesKb / 1024f)
                               else
                                   "%.0f".format(uiState.sessionBytesKb),
                    unit     = if (uiState.sessionBytesKb >= 1024f) "MB" else "KB",
                    icon     = Icons.Default.DataUsage
                )
            }
        }

        // Total cache stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label    = "סך הכל",
                value    = "${uiState.cacheEntryCount}",
                unit     = "שמורות",
                icon     = Icons.Default.Storage
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label    = "גודל מטמון",
                value    = "%.1f".format(uiState.cacheSizeMb),
                unit     = "MB",
                icon     = Icons.Default.Folder
            )
        }

        // Mode indicator
        ModeIndicatorCard(
            running = uiState.vpnRunning,
            mode    = uiState.mode
        )

        Spacer(modifier = Modifier.weight(1f))

        // Navigation grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NavCard(
                modifier = Modifier.weight(1f),
                label    = "Traffic",
                icon     = Icons.Default.Timeline,
                onClick  = { onNavigateTo("traffic") }
            )
            NavCard(
                modifier = Modifier.weight(1f),
                label    = "Cache",
                icon     = Icons.Default.Folder,
                onClick  = { onNavigateTo("cache") }
            )
        }
    }
}

@Composable
private fun AbsorbButton(
    running: Boolean,
    mode: InternetExtractorVpnService.Mode,
    onAbsorb: () -> Unit,
    onServe: () -> Unit,
    onStop: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = when {
            !running -> MaterialTheme.colorScheme.surfaceVariant
            mode == InternetExtractorVpnService.Mode.ABSORB -> Color(0xFF00C853)
            else -> Color(0xFF2979FF)
        },
        animationSpec = tween(400),
        label = "buttonColor"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Big circle button
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(buttonColor.copy(alpha = 0.9f), buttonColor.copy(alpha = 0.4f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (!running) Icons.Default.WifiTethering
                                  else if (mode == InternetExtractorVpnService.Mode.ABSORB) Icons.Default.CloudDownload
                                  else Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        !running -> "TAP TO\nABSORB"
                        mode == InternetExtractorVpnService.Mode.ABSORB -> "ABSORBING"
                        else -> "SERVING"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAbsorb,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Absorb")
                }
                OutlinedButton(onClick = onServe) {
                    Icon(Icons.Default.CloudOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Serve Offline")
                }
            }
        } else {
            Button(
                onClick = onStop,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                 color = MaterialTheme.colorScheme.onBackground)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ModeIndicatorCard(running: Boolean, mode: InternetExtractorVpnService.Mode) {
    val color = when {
        !running -> MaterialTheme.colorScheme.surfaceVariant
        mode == InternetExtractorVpnService.Mode.ABSORB -> Color(0xFF00C853).copy(alpha = 0.15f)
        else -> Color(0xFF2979FF).copy(alpha = 0.15f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (running) Color(0xFF00C853) else Color(0xFF607D8B))
            )
            Text(
                text = when {
                    !running -> "VPN inactive – connect to WiFi and tap Absorb"
                    mode == InternetExtractorVpnService.Mode.ABSORB ->
                        "Absorbing mode – all traffic is being cached"
                    else -> "Serve mode – serving from cache (offline)"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun NavCard(modifier: Modifier, label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick  = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
