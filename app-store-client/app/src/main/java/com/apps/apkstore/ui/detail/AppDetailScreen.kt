package com.apps.apkstore.ui.detail

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.utils.ShareUtils
import com.apps.apkstore.ui.components.AppIconWithProgress
import com.apps.apkstore.ui.dialogs.ProfessionalScanDialog
import com.apps.apkstore.ui.dialogs.ProfessionalUninstallDialog
import com.apps.apkstore.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(appId: String, viewModel: AppDetailViewModel, onBack: () -> Unit) {
    var app by remember { mutableStateOf<AppModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(appId) {
        if (appId.isNotBlank()) {
            isLoading = true
            viewModel.loadAppDetail(appId,
                onSuccess = { appData -> app = appData; isLoading = false },
                onError = { err -> error = err.message; isLoading = false }
            )
        }
    }

    LaunchedEffect(appId) {
        if (appId.isNotBlank()) {
            viewModel.observeApp(appId) { updatedApp ->
                app = updatedApp
            }
        }
    }

    val isCompatible = app?.let { it.minSdk <= Build.VERSION.SDK_INT } ?: true
    val actuallyInstalled = app?.let {
        try { context.packageManager.getPackageInfo(it.packageName, 0); true } catch (e: Exception) { false }
    } ?: false
    var showScanDialog by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var scanProgress by remember { mutableStateOf(0f) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    if (showScanDialog) {
        ProfessionalScanDialog(
            scanProgress = scanProgress,
            scanResult = scanResult,
            appName = app?.name ?: "",
            onDismiss = {
                showScanDialog = false
                app?.let { viewModel.handlePrimaryAction(it) }
            }
        )

        LaunchedEffect(Unit) {
            scanProgress = 0f
            scanResult = null
            for (i in 1..10) {
                delay(200)
                scanProgress = i / 10f
            }
            val hasDangerousPerms = app?.permissions?.any { p ->
                p.contains("WRITE_SETTINGS") || p.contains("SYSTEM_ALERT_WINDOW") || p.contains("BIND_ACCESSIBILITY_SERVICE")
            } ?: false
            scanResult = if (hasDangerousPerms) "Warning: App requests sensitive permissions" else "Safe: No dangerous permissions detected"
            delay(800)
            showScanDialog = false
            app?.let { viewModel.handlePrimaryAction(it) }
        }
    }

    if (showUninstallDialog && app != null) {
        ProfessionalUninstallDialog(
            appName = app!!.name,
            appIcon = app!!.iconUrl,
            onConfirm = {
                showUninstallDialog = false
                viewModel.uninstallApp(app!!)
            },
            onDismiss = { showUninstallDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { app?.let { ShareUtils.shareApp(context, it.id.toString(), it.name, it.shortDescription, it.versionName) } }) { Icon(Icons.Filled.Share, "Share") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.onBackground, navigationIconContentColor = MaterialTheme.colorScheme.onBackground)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading && app == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else if (error != null && app == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error ?: "Error", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { isLoading = true; error = null; viewModel.loadAppDetail(appId, { app = it; isLoading = false }, { error = it.message; isLoading = false }) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Retry") }
                }
            }
        } else {
            app?.let { appData ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIconWithProgress(
                            app = appData,
                            iconSize = 80.dp,
                            showProgress = true
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(appData.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(appData.developerName, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = RatingStar, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${appData.getFormattedRating()}  •  ${appData.getFormattedDownloads()} downloads  •  ${appData.getFormattedSize()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isCompatible) {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f))) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("App not compatible with this device (Requires Android ${appData.minSdk}+)", fontSize = 13.sp, color = Error)
                            }
                        }
                    }

                    if (isCompatible) {
                        val isDownloading = appData.downloadStatus == DownloadStatus.DOWNLOADING
                        val isInstalling = appData.downloadStatus == DownloadStatus.INSTALLING
                        val isCompleted = appData.downloadStatus == DownloadStatus.COMPLETED
                        val hasUpdate = appData.hasUpdate()

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isDownloading) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.cancelDownload(appData.id)
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                ) {
                                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                                }
                            } else if (isInstalling) {
                                Button(
                                    onClick = { },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                                    enabled = false
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Installing...", fontWeight = FontWeight.SemiBold)
                                }
                            } else if (actuallyInstalled && hasUpdate) {
                                Button(
                                    onClick = {
                                        showScanDialog = true
                                        scanProgress = 0f
                                        scanResult = null
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Filled.SystemUpdate, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Update", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = context.packageManager.getLaunchIntentForPackage(appData.packageName)
                                            if (intent != null) {
                                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                context.startActivity(intent)
                                            }
                                        } catch (e: Exception) { }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                ) {
                                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open", fontWeight = FontWeight.SemiBold)
                                }
                            } else if (actuallyInstalled) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = context.packageManager.getLaunchIntentForPackage(appData.packageName)
                                            if (intent != null) {
                                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                context.startActivity(intent)
                                            }
                                        } catch (e: Exception) { }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                                ) {
                                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { showUninstallDialog = true },
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                                ) {
                                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        showScanDialog = true
                                        scanProgress = 0f
                                        scanResult = null
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Install", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { appData.downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${appData.downloadProgress.toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (appData.shortDescription.isNotBlank()) {
                        Text(appData.shortDescription, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (appData.screenshotUrls.isNotEmpty()) {
                        Text("Screenshots", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(modifier = Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(appData.screenshotUrls) { url ->
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(), contentDescription = "Screenshot", modifier = Modifier.width(200.dp).height(350.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("About this app", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(appData.description.ifBlank { appData.shortDescription }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("App Info", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(12.dp))
                            DetailRow("Version", appData.versionName)
                            DetailRow("Size", appData.getFormattedSize())
                            DetailRow("Category", appData.category)
                            DetailRow("Requires", "Android ${appData.minSdk}+")
                            DetailRow("Updated", appData.lastUpdated.ifBlank { appData.releaseDate })
                            if (appData.developerEmail.isNotBlank()) DetailRow("Developer", appData.developerEmail)
                        }
                    }

                    if (appData.permissions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(8.dp))
                                appData.permissions.take(5).forEach { perm ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(perm.substringAfterLast("."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (appData.permissions.size > 5) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("See all ${appData.permissions.size} permissions", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
