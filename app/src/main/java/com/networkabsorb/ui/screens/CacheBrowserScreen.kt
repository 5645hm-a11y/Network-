package com.networkabsorb.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkabsorb.cache.CachedResponse
import com.networkabsorb.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheBrowserScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val entries by viewModel.cachedEntries.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cache Browser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, "Clear all")
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
        ) {
            // Stats header
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${uiState.cacheEntryCount} entries  •  ${"%.1f".format(uiState.cacheSizeMb)} MB",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { viewModel.purgeExpired() }) {
                    Text("Purge expired")
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.key }) { entry ->
                    CacheEntryCard(entry)
                }
                if (entries.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No cached content yet.", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title   = { Text("Clear all cache?") },
            text    = { Text("This will delete all cached content. You will need to reconnect to WiFi to absorb again.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCache(); showClearDialog = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CacheEntryCard(entry: CachedResponse) {
    val dateStr = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(entry.timestampMs))
    }
    val stale = entry.isStale()

    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (stale)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Method badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = methodColor(entry.method).copy(alpha = 0.15f)
            ) {
                Text(
                    entry.method,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = methodColor(entry.method)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = entry.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = "${entry.sizeBytes / 1024}KB  •  ${entry.contentType.substringBefore(';')}  •  $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (stale) {
                Icon(Icons.Default.HourglassEmpty, "Stale",
                     tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun methodColor(method: String) = when (method) {
    "GET"    -> MaterialTheme.colorScheme.primary
    "POST"   -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    "DELETE" -> MaterialTheme.colorScheme.error
    else     -> MaterialTheme.colorScheme.secondary
}
