package com.networkabsorb.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkabsorb.ui.MainViewModel
import com.networkabsorb.ui.UiState
import com.networkabsorb.vpn.InternetExtractorVpnService

// ── Design tokens ───────────────────────────────────────────────────────────
private val GreenAbsorb = Color(0xFF00E676)
private val CyanSim     = Color(0xFF00BCD4)
private val OrangeWarn  = Color(0xFFFF9800)
private val RedStop     = Color(0xFFEF5350)
private val CardBg      = Color(0xFF131B2E)
private val PageBg      = Color(0xFF0A0F1E)
private val SurfaceBg   = Color(0xFF1C2540)

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateTo: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startAbsorbing()
    }

    val certLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onCaCertInstalled() }

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
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Virtual SIM",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White
                )
                Text(
                    "שאיבת אינטרנט · גלישה ללא רשת",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
            Row {
                IconButton(onClick = { onNavigateTo("traffic") }) {
                    Icon(Icons.Default.Timeline, null, tint = Color.White.copy(0.6f))
                }
                IconButton(onClick = { onNavigateTo("settings") }) {
                    Icon(Icons.Default.Settings, null, tint = Color.White.copy(0.6f))
                }
            }
        }

        // ── CA cert warning ──────────────────────────────────────────────────
        if (!uiState.caCertReady && !uiState.vpnRunning) {
            CaCertBanner {
                try { certLauncher.launch(viewModel.buildCaInstallIntent()) }
                catch (_: Exception) { viewModel.onCaCertInstalled() }
            }
        }

        // ── Main card — switches based on VPN state ──────────────────────────
        when {
            !uiState.vpnRunning -> ReadyCard(
                uiState  = uiState,
                onAbsorb = {
                    val prep = viewModel.prepareVpn()
                    if (prep != null) vpnLauncher.launch(prep)
                    else viewModel.startAbsorbing()
                },
                onServe  = { viewModel.startServing() },
                onQuota  = { viewModel.setQuota(it) }
            )
            uiState.mode == InternetExtractorVpnService.Mode.ABSORB -> AbsorbingCard(
                uiState = uiState,
                onStop  = { viewModel.stopVpn() }
            )
            else -> VirtualSimActiveCard(
                uiState = uiState,
                onStop  = { viewModel.stopVpn() }
            )
        }

        // ── Storage summary ──────────────────────────────────────────────────
        StorageSummaryRow(uiState)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CA cert banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaCertBanner(onInstall: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeWarn.copy(0.12f))
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, null, tint = OrangeWarn, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("נדרש אישור אבטחה", fontWeight = FontWeight.Bold, color = OrangeWarn, fontSize = 13.sp)
                Text(
                    "כדי לשאוב HTTPS – התקן את אישור ה-CA",
                    color = Color.White.copy(0.65f),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp
                )
            }
            Button(
                onClick = onInstall,
                colors  = ButtonDefaults.buttonColors(containerColor = OrangeWarn),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("התקן", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle / ready card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyCard(
    uiState : UiState,
    onAbsorb: () -> Unit,
    onServe : () -> Unit,
    onQuota : (Int) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Title
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(listOf(GreenAbsorb.copy(0.3f), Color.Transparent))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.WifiTethering, null, tint = GreenAbsorb, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text("מוכן לשאיבה", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    Text(
                        "WiFi → מטמון → Virtual SIM",
                        color = Color.White.copy(0.4f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(0.07f))

            // Quota slider
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("כמות לשאיבה", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text(
                        if (uiState.quotaMb >= 1024) "${"%.1f".format(uiState.quotaMb / 1024f)} GB"
                        else "${uiState.quotaMb} MB",
                        color      = GreenAbsorb,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp
                    )
                }
                Slider(
                    value         = uiState.quotaMb.toFloat(),
                    onValueChange = { onQuota(it.toInt()) },
                    valueRange    = 100f..5120f,
                    steps         = 49,
                    colors        = SliderDefaults.colors(
                        thumbColor         = GreenAbsorb,
                        activeTrackColor   = GreenAbsorb,
                        inactiveTrackColor = GreenAbsorb.copy(0.2f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("100 MB", color = Color.White.copy(0.28f), fontSize = 11.sp)
                    Text("5 GB",   color = Color.White.copy(0.28f), fontSize = 11.sp)
                }
            }

            // Step hint
            StepHint()

            // Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick  = onAbsorb,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenAbsorb)
                ) {
                    Icon(Icons.Default.CloudDownload, null, Modifier.size(20.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("שאב אינטרנט מה-WiFi", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.Black)
                }

                if (uiState.cacheSizeMb > 0f) {
                    OutlinedButton(
                        onClick  = onServe,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = CyanSim),
                        border   = androidx.compose.foundation.BorderStroke(1.5.dp, CyanSim.copy(0.5f))
                    ) {
                        Icon(Icons.Default.SimCard, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "הפעל Virtual SIM (${"%.0f".format(uiState.cacheSizeMb)} MB)",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHint() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(SurfaceBg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepChip("1", "WiFi", GreenAbsorb)
        Icon(Icons.Default.ArrowForward, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
        StepChip("2", "שאב", GreenAbsorb)
        Icon(Icons.Default.ArrowForward, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
        StepChip("3", "חו\"ל", CyanSim)
        Icon(Icons.Default.ArrowForward, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
        StepChip("4", "גלוש", CyanSim)
    }
}

@Composable
private fun RowScope.StepChip(num: String, label: String, color: Color) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(color.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Color.White.copy(0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Absorbing card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AbsorbingCard(uiState: UiState, onStop: () -> Unit) {
    val progress = (uiState.absorptionProgressMb / uiState.quotaMb.toFloat()).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )

    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Header row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(GreenAbsorb.copy(dotAlpha)))
                    Text("שואב מהרשת...", fontWeight = FontWeight.Bold, color = GreenAbsorb, fontSize = 16.sp)
                }
                TextButton(
                    onClick = onStop,
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedStop)
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("עצור", fontWeight = FontWeight.Bold)
                }
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress   = { progress },
                    modifier   = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color      = GreenAbsorb,
                    trackColor = GreenAbsorb.copy(0.15f)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatMb(uiState.absorptionProgressMb),
                        color = GreenAbsorb, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                    Text(
                        "מתוך ${formatMb(uiState.quotaMb.toFloat())}",
                        color = Color.White.copy(0.35f), fontSize = 13.sp
                    )
                }
            }

            // Counters
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(Modifier.weight(1f), "בקשות", "${uiState.sessionRequests}", GreenAbsorb)
                StatBox(Modifier.weight(1f), "נשאב", formatKb(uiState.sessionBytesKb), GreenAbsorb)
            }

            // Domain list
            if (uiState.topDomains.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("אתרים שנשמרים:", color = Color.White.copy(0.4f), fontSize = 11.sp)
                    uiState.topDomains.take(5).forEach { (domain, count) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(GreenAbsorb.copy(0.55f)))
                                Text(
                                    domain,
                                    color    = Color.White.copy(0.8f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp)
                                )
                            }
                            Text("$count", color = GreenAbsorb.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Virtual SIM active card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VirtualSimActiveCard(uiState: UiState, onStop: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sim")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(CyanSim.copy(glow * 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SimCard, null, tint = CyanSim, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("Virtual SIM פעיל", fontWeight = FontWeight.Bold, color = CyanSim, fontSize = 16.sp)
                        Text("גולש ללא רשת מהמטמון", color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(
                    onClick = onStop,
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedStop)
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("עצור", fontWeight = FontWeight.Bold)
                }
            }

            // Info box
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(CyanSim.copy(0.08f)).padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = CyanSim, modifier = Modifier.size(16.dp))
                        Text(
                            "הטלפון עובד ללא WiFi / סלולרי",
                            color = Color.White.copy(0.9f), fontWeight = FontWeight.Medium, fontSize = 13.sp
                        )
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = CyanSim, modifier = Modifier.size(16.dp))
                        Text(
                            "DNS, HTTP ו-HTTPS מוגשים מהמטמון",
                            color = Color.White.copy(0.9f), fontWeight = FontWeight.Medium, fontSize = 13.sp
                        )
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(16.dp))
                        Text(
                            "אתרים שלא נשאבו לא ייטענו",
                            color = Color.White.copy(0.45f), style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp
                        )
                    }
                }
            }

            // Stats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(Modifier.weight(1f), "הוגש",   "${uiState.sessionCacheHits}", CyanSim)
                StatBox(Modifier.weight(1f), "במטמון", "${uiState.cacheEntryCount}",  CyanSim)
                StatBox(Modifier.weight(1f), "שמור",   formatMb(uiState.cacheSizeMb), CyanSim)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Storage summary
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StorageSummaryRow(uiState: UiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStatCard(
            modifier = Modifier.weight(1f),
            label    = "בקשות שמורות",
            value    = "${uiState.cacheEntryCount}",
            unit     = "URLs",
            color    = GreenAbsorb
        )
        MiniStatCard(
            modifier = Modifier.weight(1f),
            label    = "נפח מטמון",
            value    = "%.1f".format(uiState.cacheSizeMb),
            unit     = "MB",
            color    = CyanSim
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small reusable components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatBox(modifier: Modifier, label: String, value: String, color: Color) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(0.09f)).padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = Color.White.copy(0.45f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MiniStatCard(modifier: Modifier, label: String, value: String, unit: String, color: Color) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color.White.copy(0.38f), style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(unit, color = Color.White.copy(0.38f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Format helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatMb(mb: Float): String =
    if (mb >= 1024f) "${"%.2f".format(mb / 1024f)} GB"
    else "${"%.1f".format(mb)} MB"

private fun formatKb(kb: Float): String =
    if (kb >= 1024f) "${"%.1f".format(kb / 1024f)} MB"
    else "${"%.0f".format(kb)} KB"
