package com.networkabsorb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networkabsorb.logger.TrafficEvent
import com.networkabsorb.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficMonitorScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traffic Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.recentTraffic) { event ->
                TrafficEventRow(event)
            }

            if (uiState.recentTraffic.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No traffic yet. Start absorbing to see live events.",
                             color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficEventRow(event: TrafficEvent) {
    val (icon, color, label) = when (event) {
        is TrafficEvent.Request   -> Triple(Icons.Default.Upload,   Color(0xFF42A5F5), "REQ")
        is TrafficEvent.Response  -> Triple(Icons.Default.Download, Color(0xFF66BB6A), "RES")
        is TrafficEvent.CacheHit  -> Triple(Icons.Default.FlashOn,  Color(0xFFFFCA28), "HIT")
        is TrafficEvent.Error     -> Triple(Icons.Default.Error,    Color(0xFFEF5350), "ERR")
    }

    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = event.host,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = event.url.take(60) + if (event.url.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape  = RoundedCornerShape(4.dp),
                color  = color.copy(alpha = 0.15f)
            ) {
                Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                     style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}
