package com.networkabsorb.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkabsorb.ui.MainViewModel
import com.networkabsorb.vpn.InternetExtractorVpnService

private val GreenAbsorb = Color(0xFF00E676)
private val BlueServe   = Color(0xFF2979FF)
private val OrangeWarn  = Color(0xFFFF9800)
private val CardBg      = Color(0xFF1A2030)
private val PageBg      = Color(0xFF0D1117)

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateTo: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startAbsorbing()
    }

    val certLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from cert install screen, mark as installed
        viewModel.onCaCertInstalled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Network Absorb",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "שאיבת אינטרנט חכמה",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Row {
                IconButton(onClick = { onNavigateTo("traffic") }) {
                    Icon(Icons.Default.Timeline, "Traffic", tint = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { onNavigateTo("settings") }) {
                    Icon(Icons.Default.Settings, "Settings", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        // ── CA Cert warning (if not installed) ──────────────────────────────
        if (!uiState.caCertReady && !uiState.vpnRunning) {
            CaCertBanner(onInstall = {
                try {
                    val intent = viewModel.buildCaInstallIntent()
                    certLauncher.launch(intent)
                } catch (e: Exception) {
                    viewModel.onCaCertInstalled() // fallback: assume done
                }
            })
        }

        // ── Main action card ─────────────────────────────────────────────────
        when {
            !uiState.vpnRunning -> ReadyCard(
                uiState   = uiState,
                onAbsorb  = {
                    val prep = viewModel.prepareVpn()
                    if (prep != null) vpnLauncher.launch(prep)
                    else viewModel.startAbsorbing()
                },
                onServe   = { viewModel.startServing() },
                onQuota   = { viewModel.setQuota(it) }
            )
            uiState.mode == InternetExtractorVpnService.Mode.ABSORB -> AbsorbingCard(
                uiState = uiState,
                onStop  = { viewModel.stopVpn() }
            )
            else -> ServingCard(
                uiState = uiState,
                onStop  = { viewModel.stopVpn() }
            )
        }

        // ── Total cache stats ────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label    = "סך הכל שמור",
                value    = "${uiState.cacheEntryCount}",
                unit     = "בקשות",
                color    = GreenAbsorb
            )
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label    = "גודל מטמון",
                value    = "%.1f".format(uiState.cacheSizeMb),
                unit     = "MB",
                color    = BlueServe
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CA cert banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaCertBanner(onInstall: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeWarn.copy(alpha = 0.15f))
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint   = OrangeWarn,
                modifier = Modifier.size(28.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "נדרש אישור אבטחה",
                    fontWeight = FontWeight.Bold,
                    color  = OrangeWarn,
                    fontSize = 14.sp
                )
                Text(
                    "כדי לשאוב HTTPS (רוב האינטרנט), התקן את אישור ה-CA של האפליקציה",
                    color  = Color.White.copy(alpha = 0.75f),
                    style  = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
            Button(
                onClick = onInstall,
                colors  = ButtonDefaults.buttonColors(containerColor = OrangeWarn),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("התקן", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ready (idle) card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyCard(
    uiState: com.networkabsorb.ui.UiState,
    onAbsorb: () -> Unit,
    onServe: () -> Unit,
    onQuota: (Int) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Icon + title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(GreenAbsorb.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.WifiTethering, null, tint = GreenAbsorb, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text("מוכן לשאיבה", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    Text("בחר כמות ולחץ התחל", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Quota selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("כמות לשאיבה", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text(
                        if (uiState.quotaMb >= 1024) "${"%.1f".format(uiState.quotaMb / 1024f)} GB"
                        else "${uiState.quotaMb} MB",
                        color = GreenAbsorb,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = uiState.quotaMb.toFloat(),
                    onValueChange = { onQuota(it.toInt()) },
                    valueRange = 100f..5120f,
                    steps = 49,
                    colors = SliderDefaults.colors(
                        thumbColor        = GreenAbsorb,
                        activeTrackColor  = GreenAbsorb,
                        inactiveTrackColor = GreenAbsorb.copy(alpha = 0.2f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("100 MB", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
                    Text("5 GB",   color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
                }
            }

            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAbsorb,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAbsorb)
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(20.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "התחל שאיבה מהרשת",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.Black
                    )
                }

                if (uiState.cacheSizeMb > 0f) {
                    OutlinedButton(
                        onClick = onServe,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape  = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueServe),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BlueServe.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "הפעל אינטרנט ממטמון (${"%.0f".format(uiState.cacheSizeMb)} MB שמור)",
                            fontWeight = FontWeight.Medium,
                            fontSize   = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Absorbing card (live progress)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AbsorbingCard(uiState: com.networkabsorb.ui.UiState, onStop: () -> Unit) {
    val progress = (uiState.absorptionProgressMb / uiState.quotaMb.toFloat()).coerceIn(0f, 1f)

    // Pulsing animation for the "live" dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dot"
    )

    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Status row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(GreenAbsorb.copy(alpha = alpha)))
                    Text("שואב מהרשת...", fontWeight = FontWeight.Bold, color = GreenAbsorb, fontSize = 16.sp)
                }
                TextButton(onClick = onStop, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("עצור", fontWeight = FontWeight.Bold)
                }
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color    = GreenAbsorb,
                    trackColor = GreenAbsorb.copy(alpha = 0.15f)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val progressLabel =
                        if (uiState.absorptionProgressMb >= 1024f)
                            "${"%.2f".format(uiState.absorptionProgressMb / 1024f)} GB"
                        else "${"%.1f".format(uiState.absorptionProgressMb)} MB"
                    val quotaLabel =
                        if (uiState.quotaMb >= 1024)
                            "${"%.1f".format(uiState.quotaMb / 1024f)} GB"
                        else "${uiState.quotaMb} MB"
                    Text(progressLabel, color = GreenAbsorb, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("מתוך $quotaLabel", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            }

            // Live counters
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveStat(Modifier.weight(1f), label = "בקשות", value = "${uiState.sessionRequests}", color = GreenAbsorb)
                LiveStat(
                    modifier = Modifier.weight(1f),
                    label    = "נשאב",
                    value    = if (uiState.sessionBytesKb >= 1024f)
                                   "${"%.1f".format(uiState.sessionBytesKb / 1024f)} MB"
                               else "${"%.0f".format(uiState.sessionBytesKb)} KB",
                    color    = GreenAbsorb
                )
            }

            // Top domains
            if (uiState.topDomains.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("אתרים שנשמרים:", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    uiState.topDomains.take(5).forEach { (domain, count) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(GreenAbsorb.copy(alpha = 0.6f)))
                                Text(domain, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                                     maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                     modifier = Modifier.widthIn(max = 200.dp))
                            }
                            Text("$count", color = GreenAbsorb.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Serving card (offline mode)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServingCard(uiState: com.networkabsorb.ui.UiState, onStop: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Status
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.WifiOff, null, tint = BlueServe, modifier = Modifier.size(22.dp))
                    Text("אינטרנט ממטמון פעיל", fontWeight = FontWeight.Bold, color = BlueServe, fontSize = 16.sp)
                }
                TextButton(onClick = onStop, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("עצור", fontWeight = FontWeight.Bold)
                }
            }

            // Info box
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BlueServe.copy(alpha = 0.1f)).padding(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "הטלפון מקבל אינטרנט מהמטמון השמור",
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        "כל בקשה לאתר שנשאב תוגש מיידית. אתרים חדשים לא ייטענו.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }

            // Serve stats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveStat(Modifier.weight(1f), label = "הוגש",   value = "${uiState.sessionCacheHits}", color = BlueServe)
                LiveStat(Modifier.weight(1f), label = "במטמון", value = "${uiState.cacheEntryCount}",  color = BlueServe)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveStat(modifier: Modifier, label: String, value: String, color: Color) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Text(label, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MiniStatCard(modifier: Modifier, label: String, value: String, unit: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
                Text(unit, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
                     modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
