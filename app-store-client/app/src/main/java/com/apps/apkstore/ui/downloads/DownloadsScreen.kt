package com.apps.apkstore.ui.downloads

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apps.apkstore.data.local.DownloadEntity
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, onBack: () -> Unit) {
    var downloads by remember { mutableStateOf<List<DownloadEntity>>(emptyList()) }
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Active", "Completed", "Failed")

    LaunchedEffect(activeTab) {
        viewModel.loadDownloads(activeTab) { list -> downloads = list }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = activeTab, containerColor = MaterialTheme.colorScheme.surface, contentColor = Primary) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = activeTab == index, onClick = { activeTab = index }, text = {
                        Text(title, fontWeight = if (activeTab == index) FontWeight.SemiBold else FontWeight.Normal)
                    })
                }
            }

            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No downloads", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(downloads) { download ->
                        DownloadItem(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onInstall = { viewModel.installDownload(download) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItem(download: DownloadEntity, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onInstall: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = download.iconUrl, contentDescription = download.appName, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(download.appName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${download.progress.toInt()}%  •  ${formatSize(download.downloadedBytes)} / ${formatSize(download.totalBytes)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        TextButton(onClick = onPause) { Text("Pause", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = onCancel) { Text("Cancel", color = Error) }
                    }
                    DownloadStatus.PAUSED -> {
                        Button(onClick = onResume, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), modifier = Modifier.height(30.dp)) {
                            Text("Resume", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onCancel) { Text("Cancel", color = Error) }
                    }
                    DownloadStatus.COMPLETED -> {
                        Button(onClick = onInstall, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), modifier = Modifier.height(30.dp)) {
                            Text("Install", fontSize = 12.sp)
                        }
                    }
                    DownloadStatus.FAILED -> {
                        Button(onClick = onResume, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Error), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), modifier = Modifier.height(30.dp)) {
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun formatSize(size: Long): String = when {
    size >= 1_073_741_824 -> String.format("%.1f GB", size / 1_073_741_824.0)
    size >= 1_048_576 -> String.format("%.1f MB", size / 1_048_576.0)
    size >= 1024 -> String.format("%.1f KB", size / 1024.0)
    else -> "$size B"
}
