package com.networkabsorb.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkabsorb.ui.MainViewModel
import com.networkabsorb.ui.UiState
import com.networkabsorb.vpn.InternetExtractorVpnService.Mode

// ── Palette ─────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF060914)
private val BgCard      = Color(0xFF0C1428)
private val BgSurface   = Color(0xFF111D35)
private val Electric    = Color(0xFF00B4FF)
private val Mint        = Color(0xFF00F5A0)
private val VioletAI    = Color(0xFF9B72FF)
private val CyanSim     = Color(0xFF00E5FF)
private val Amber       = Color(0xFFFFB800)
private val RedStop     = Color(0xFFFF3B30)
private val TextPrimary = Color(0xFFECF0FF)
private val TextMuted   = Color(0xFF5A6A8A)

@Composable
fun DashboardScreen(viewModel: MainViewModel, onNavigateTo: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == Activity.RESULT_OK) viewModel.startAbsorbing() }

    val certLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onCaCertInstalled() }

    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        // Subtle gradient orb in background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Electric.copy(0.07f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.15f),
                    radius = size.width * 0.7f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(VioletAI.copy(0.06f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.6f),
                    radius = size.width * 0.6f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TopBar(uiState, onNavigateTo)

            if (!uiState.caCertReady && !uiState.vpnRunning) {
                CertWarning { try { certLauncher.launch(viewModel.buildCaInstallIntent()) }
                              catch (_: Exception) { viewModel.onCaCertInstalled() } }
            }

            AiAgentPanel(uiState)

            MainCard(
                uiState   = uiState,
                onAbsorb  = {
                    val p = viewModel.prepareVpn()
                    if (p != null) vpnLauncher.launch(p) else viewModel.startAbsorbing()
                },
                onServe   = { viewModel.startServing() },
                onStop    = { viewModel.stopVpn() },
                onQuota   = { viewModel.setQuota(it) }
            )

            if (uiState.vpnRunning && uiState.mode == Mode.ABSORB) {
                DomainFeed(uiState)
            }

            StatsRow(uiState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(uiState: UiState, onNavigateTo: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Virtual SIM",
                fontSize   = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TextPrimary
            )
            Text(
                "שאיבת אינטרנט · גלישה ללא רשת",
                fontSize = 12.sp,
                color    = TextMuted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Status dot
            val dotColor = when {
                !uiState.vpnRunning                -> TextMuted
                uiState.mode == Mode.ABSORB -> Mint
                else                               -> CyanSim
            }
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(dotColor)
                    .align(Alignment.CenterVertically)
            )
            IconButton(onClick = { onNavigateTo("traffic") }) {
                Icon(Icons.Default.Timeline, null, tint = TextMuted, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onNavigateTo("settings") }) {
                Icon(Icons.Default.Settings, null, tint = TextMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cert warning
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CertWarning(onInstall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Amber.copy(0.12f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Lock, null, tint = Amber, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("נדרש אישור CA", fontWeight = FontWeight.Bold, color = Amber, fontSize = 13.sp)
            Text("נדרש לשאיבת HTTPS", color = TextMuted, fontSize = 11.sp)
        }
        Button(
            onClick = onInstall,
            colors  = ButtonDefaults.buttonColors(containerColor = Amber),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            shape   = RoundedCornerShape(10.dp)
        ) {
            Text("התקן", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Agent Panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AiAgentPanel(uiState: UiState) {
    val agent = uiState.agentStatus

    val inf = rememberInfiniteTransition(label = "ai")
    val pulse by inf.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Brain icon with glow
        Box(
            Modifier.size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VioletAI.copy(if (agent.thinking) pulse * 0.3f else 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (agent.thinking) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(20.dp),
                    color     = VioletAI,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.AutoAwesome, null, tint = VioletAI, modifier = Modifier.size(20.dp))
            }
        }

        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "AI Agent",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = VioletAI
                )
                if (!agent.hasKey) {
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(Amber.copy(0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("ללא API Key", fontSize = 9.sp, color = Amber)
                    }
                }
            }
            Text(
                agent.message,
                fontSize = 12.sp,
                color    = TextPrimary.copy(0.85f),
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main state card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MainCard(
    uiState : UiState,
    onAbsorb: () -> Unit,
    onServe : () -> Unit,
    onStop  : () -> Unit,
    onQuota : (Int) -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .padding(24.dp)
    ) {
        when {
            !uiState.vpnRunning -> IdleContent(uiState, onAbsorb, onServe, onQuota)
            uiState.mode == Mode.ABSORB -> AbsorbingContent(uiState, onStop)
            else -> SimActiveContent(uiState, onStop)
        }
    }
}

// ── Idle ─────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    uiState: UiState, onAbsorb: () -> Unit, onServe: () -> Unit, onQuota: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {

        // Step flow
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgSurface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            listOf("📶 WiFi", "→", "⬇ שאב", "→", "✈ חו\"ל", "→", "🌐 גלוש").forEach { s ->
                Text(s, fontSize = 11.sp, color = if (s == "→") TextMuted else TextPrimary,
                     fontWeight = if (s != "→") FontWeight.Medium else FontWeight.Normal)
            }
        }

        // Quota slider
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("כמות לשאיבה", fontSize = 13.sp, color = TextMuted)
                Text(
                    if (uiState.quotaMb >= 1024) "${"%.1f".format(uiState.quotaMb / 1024f)} GB"
                    else "${uiState.quotaMb} MB",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Mint
                )
            }
            Slider(
                value         = uiState.quotaMb.toFloat(),
                onValueChange = { onQuota(it.toInt()) },
                valueRange    = 100f..5120f,
                steps         = 49,
                colors        = SliderDefaults.colors(
                    thumbColor         = Mint,
                    activeTrackColor   = Mint,
                    inactiveTrackColor = Mint.copy(0.15f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("100 MB", fontSize = 10.sp, color = TextMuted)
                Text("5 GB",   fontSize = 10.sp, color = TextMuted)
            }
        }

        // Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick  = onAbsorb,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Mint)
            ) {
                Icon(Icons.Default.CloudDownload, null, Modifier.size(20.dp), tint = Color.Black)
                Spacer(Modifier.width(10.dp))
                Text("שאב אינטרנט מה-WiFi",
                     fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
            }

            if (uiState.cacheSizeMb > 0f) {
                OutlinedButton(
                    onClick  = onServe,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = CyanSim),
                    border   = androidx.compose.foundation.BorderStroke(1.5.dp, CyanSim.copy(0.4f))
                ) {
                    Icon(Icons.Default.SimCard, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "הפעל Virtual SIM  ·  ${"%.0f".format(uiState.cacheSizeMb)} MB",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ── Absorbing ─────────────────────────────────────────────────────────────────

@Composable
private fun AbsorbingContent(uiState: UiState, onStop: () -> Unit) {
    val progress = (uiState.absorptionProgressMb / uiState.quotaMb.toFloat()).coerceIn(0f, 1f)

    val inf = rememberInfiniteTransition(label = "absorb")
    val ring by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "ring"
    )
    val pulse by inf.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(20.dp)
    ) {
        // Animated ring progress
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(12f, cap = StrokeCap.Round)
                val inset  = 12f
                val rect   = Size(size.width - inset * 2, size.height - inset * 2)
                // Track
                drawArc(Mint.copy(0.1f), 0f, 360f, false, Offset(inset, inset), rect, style = stroke)
                // Progress
                drawArc(Mint, -90f, 360f * progress, false, Offset(inset, inset), rect, style = stroke)
                // Spinning dot
                drawCircle(Mint.copy(pulse), 8f,
                    center = Offset(
                        size.width / 2 + (size.width / 2 - inset) * kotlin.math.cos(Math.toRadians((ring - 90).toDouble())).toFloat(),
                        size.height / 2 + (size.height / 2 - inset) * kotlin.math.sin(Math.toRadians((ring - 90).toDouble())).toFloat()
                    )
                )
                // Glow
                drawCircle(Brush.radialGradient(listOf(Mint.copy(0.15f), Color.Transparent)),
                    size.minDimension / 2)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${"%.0f".format(uiState.absorptionProgressMb)} MB",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Mint
                )
                Text(
                    "מתוך ${uiState.quotaMb} MB",
                    fontSize = 11.sp,
                    color    = TextMuted
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )
            }
        }

        // Live counters
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowStat(Modifier.weight(1f), "${uiState.sessionRequests}", "בקשות", Mint)
            GlowStat(Modifier.weight(1f), fmtKb(uiState.sessionBytesKb), "נשאב", Mint)
        }

        Button(
            onClick  = onStop,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = RedStop.copy(0.15f))
        ) {
            Icon(Icons.Default.Stop, null, Modifier.size(16.dp), tint = RedStop)
            Spacer(Modifier.width(6.dp))
            Text("עצור שאיבה", color = RedStop, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Virtual SIM Active ────────────────────────────────────────────────────────

@Composable
private fun SimActiveContent(uiState: UiState, onStop: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "sim")
    val glow by inf.animateFloat(
        0.3f, 0.9f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(20.dp)
    ) {
        // SIM icon with glow
        Box(
            Modifier.size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.radialGradient(
                    listOf(CyanSim.copy(glow * 0.3f), BgSurface)
                )),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SimCard, null, tint = CyanSim, modifier = Modifier.size(52.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VIRTUAL SIM", fontSize = 11.sp, color = CyanSim, fontWeight = FontWeight.Bold,
                 letterSpacing = 3.sp)
            Text("פעיל — גולש ממטמון", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                 color = TextPrimary)
        }

        // Checklist
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CyanSim.copy(0.07f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("DNS — מוגש ממטמון", "HTTP/HTTPS — מוגש ממטמון",
                   "אפליקציות שנשאבו — עובדות רגיל").forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = CyanSim, modifier = Modifier.size(14.dp))
                    Text(item, fontSize = 12.sp, color = TextPrimary.copy(0.85f))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowStat(Modifier.weight(1f), "${uiState.sessionCacheHits}", "הוגש", CyanSim)
            GlowStat(Modifier.weight(1f), "${uiState.cacheEntryCount}", "URLs", CyanSim)
            GlowStat(Modifier.weight(1f), fmtMb(uiState.cacheSizeMb), "שמור", CyanSim)
        }

        Button(
            onClick  = onStop,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = RedStop.copy(0.15f))
        ) {
            Icon(Icons.Default.Stop, null, Modifier.size(16.dp), tint = RedStop)
            Spacer(Modifier.width(6.dp))
            Text("כבה Virtual SIM", color = RedStop, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Domain feed (during absorption)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DomainFeed(uiState: UiState) {
    if (uiState.topDomains.isEmpty()) return
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("אתרים שנשאבים", fontSize = 11.sp, color = TextMuted,
             fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        uiState.topDomains.take(6).forEach { (domain, count) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Mint.copy(0.7f)))
                    Text(domain, fontSize = 13.sp, color = TextPrimary.copy(0.9f),
                         maxLines = 1, overflow = TextOverflow.Ellipsis,
                         modifier = Modifier.widthIn(max = 220.dp))
                }
                Text("$count", fontSize = 12.sp, color = Mint, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(uiState: UiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(Modifier.weight(1f), "שמורות",  "${uiState.cacheEntryCount}", "URLs",   Electric)
        StatCard(Modifier.weight(1f), "נפח",     fmtMb(uiState.cacheSizeMb),  "",       Electric)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlowStat(modifier: Modifier, value: String, label: String, color: Color) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(0.08f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color,
             maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, unit: String, color: Color) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (unit.isNotEmpty()) Text(unit, fontSize = 11.sp, color = TextMuted,
                                        modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

private fun fmtMb(mb: Float) = if (mb >= 1024f) "${"%.2f".format(mb / 1024f)} GB" else "${"%.1f".format(mb)} MB"
private fun fmtKb(kb: Float) = if (kb >= 1024f) "${"%.1f".format(kb / 1024f)} MB" else "${"%.0f".format(kb)} KB"
