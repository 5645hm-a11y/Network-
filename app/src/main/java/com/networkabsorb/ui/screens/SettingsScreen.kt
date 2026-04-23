package com.networkabsorb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkabsorb.ui.MainViewModel

private val BgDeep    = Color(0xFF060914)
private val BgCard    = Color(0xFF0C1428)
private val BgSurface = Color(0xFF111D35)
private val Electric  = Color(0xFF00B4FF)
private val Mint      = Color(0xFF00F5A0)
private val VioletAI  = Color(0xFF9B72FF)
private val Amber     = Color(0xFFFFB800)
private val TextPrim  = Color(0xFFE8F4FD)
private val TextSec   = Color(0xFF7B9AB2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var apiKey by remember { mutableStateOf(viewModel.getAiApiKey()) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var apiKeySaved by remember { mutableStateOf(false) }

    val certLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onCaCertInstalled() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "חזור",
                        tint = TextPrim
                    )
                }
                Text(
                    text = "הגדרות",
                    color = TextPrim,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── AI Agent Section ─────────────────────────────────────
                SectionLabel("סוכן AI", VioletAI)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1040), BgCard)
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = VioletAI,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    "Claude API Key",
                                    color = TextPrim,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "מפתח לסוכן AI חכם עם Claude Haiku",
                                    color = TextSec,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                apiKeySaved = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("sk-ant-api03-...", color = TextSec, fontSize = 13.sp)
                            },
                            visualTransformation = if (apiKeyVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        if (apiKeyVisible) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSec
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.setAiApiKey(apiKey)
                                    apiKeySaved = true
                                }
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VioletAI,
                                unfocusedBorderColor = Color(0xFF2A3A55),
                                focusedTextColor = TextPrim,
                                unfocusedTextColor = TextPrim,
                                cursorColor = VioletAI,
                                focusedContainerColor = Color(0xFF0A0F20),
                                unfocusedContainerColor = Color(0xFF0A0F20)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.setAiApiKey(apiKey)
                                    apiKeySaved = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VioletAI
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    if (apiKeySaved) Icons.Default.Check else Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (apiKeySaved) "נשמר!" else "שמור מפתח")
                            }

                            if (apiKey.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        apiKey = ""
                                        apiKeySaved = false
                                        viewModel.setAiApiKey("")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedButtonDefaults.outlinedButtonColors(
                                        contentColor = TextSec
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.linearGradient(listOf(Color(0xFF2A3A55), Color(0xFF2A3A55)))
                                    )
                                ) {
                                    Text("מחק")
                                }
                            }
                        }

                        val hasKey = uiState.agentStatus.hasKey
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (hasKey) Mint else Amber)
                            )
                            Text(
                                text = if (hasKey) "סוכן AI פעיל — Claude Haiku מחובר"
                                       else "אין מפתח — פועל עם כללים מובנים",
                                color = if (hasKey) Mint else Amber,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // ── Certificate Section ──────────────────────────────────
                SectionLabel("אבטחה", Electric)

                DarkCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Electric,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "תעודת CA",
                                color = TextPrim,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (uiState.caCertReady) "מותקנת ✓" else "נדרשת ליירוט HTTPS",
                                color = if (uiState.caCertReady) Mint else TextSec,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = {
                                try { certLauncher.launch(viewModel.buildCaInstallIntent()) }
                                catch (_: Exception) { viewModel.onCaCertInstalled() }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.caCertReady) BgSurface else Electric
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                if (uiState.caCertReady) Icons.Default.Check else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (uiState.caCertReady) Mint else BgDeep
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (uiState.caCertReady) "מותקן" else "התקן",
                                color = if (uiState.caCertReady) Mint else BgDeep,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // ── Cache Section ────────────────────────────────────────
                SectionLabel("מטמון", Mint)

                DarkCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = Mint,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "סטטיסטיקות מטמון",
                                color = TextPrim,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "${uiState.cacheEntryCount} כתובות · ${"%.1f".format(uiState.cacheSizeMb)} MB",
                                color = TextSec,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.purgeExpired() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedButtonDefaults.outlinedButtonColors(
                                contentColor = Electric
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(Electric.copy(alpha = 0.5f), Electric.copy(alpha = 0.5f)))
                            )
                        ) {
                            Text("פנה פג תוקף", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { viewModel.clearCache() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3D1515)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("נקה הכל", color = Color(0xFFFF6B6B), fontSize = 13.sp)
                        }
                    }
                }

                // ── About Section ────────────────────────────────────────
                SectionLabel("אודות", TextSec)

                DarkCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = TextSec,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                "Virtual SIM",
                                color = TextPrim,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "גרסה 1.0.0 · VPN proxy לאינטרנט offline",
                                color = TextSec,
                                fontSize = 12.sp
                            )
                            Text(
                                "מנוהל על ידי Claude Haiku AI",
                                color = VioletAI,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}
