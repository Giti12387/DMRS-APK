package com.apps.apkstore.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apps.apkstore.BuildConfig
import com.apps.apkstore.data.local.AppDatabase
import com.apps.apkstore.service.SilentAutoUpdateService
import com.apps.apkstore.utils.UpdateCheckWorker
import com.apps.apkstore.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChanged: (Int) -> Unit = {}, currentThemeMode: Int = 0) {
    val context = LocalContext.current
    val appPrefs = context.getSharedPreferences("zoro_app_store_prefs", android.content.Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    var autoUpdate by remember { mutableStateOf(appPrefs.getBoolean("auto_update", false)) }
    var silentAutoUpdate by remember { mutableStateOf(appPrefs.getBoolean("silent_auto_update", false)) }
    var downloadNotifications by remember { mutableStateOf(appPrefs.getBoolean("download_notifications", true)) }
    var updateNotifications by remember { mutableStateOf(appPrefs.getBoolean("update_notifications", true)) }
    var themeMode by remember { mutableIntStateOf(currentThemeMode) }
    var cacheSize by remember { mutableStateOf(0L) }
    var installMethod by remember { mutableStateOf(appPrefs.getString("install_method", "system") ?: "system") }
    var showInstallMethodDialog by remember { mutableStateOf(false) }

    var installedStoreApps by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var showAppSelection by remember { mutableStateOf(false) }
    val selectedApps = remember { mutableStateMapOf<String, Boolean>().apply {
        (appPrefs.getStringSet("auto_update_selected_apps", emptySet()) ?: emptySet()).forEach { put(it, true) }
    } }

    LaunchedEffect(Unit) {
        var size = 0L
        context.cacheDir?.let { size += getDirSize(it) }
        context.externalCacheDir?.let { size += getDirSize(it) }
        cacheSize = size

        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val apps = db.appDao().getAllAppsSync()
                installedStoreApps = apps.map { app ->
                    Triple(app.packageName, app.name, app.versionName)
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsSection(title = "Theme") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOption(Modifier.weight(1f), "Dark", 0, themeMode == 0, DarkBackground, Primary) { themeMode = 0; appPrefs.edit().putInt("theme_mode", 0).apply(); onThemeChanged(0) }
                    ThemeOption(Modifier.weight(1f), "Light", 1, themeMode == 1, LightBackground, Primary) { themeMode = 1; appPrefs.edit().putInt("theme_mode", 1).apply(); onThemeChanged(1) }
                    ThemeOption(Modifier.weight(1f), "Neon Blue", 2, themeMode == 2, NeonBackground, NeonPrimary) { themeMode = 2; appPrefs.edit().putInt("theme_mode", 2).apply(); onThemeChanged(2) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = "Auto Update") {
                SettingRow(title = "Auto-update apps", subtitle = "Check for updates periodically", trailing = {
                    Switch(checked = autoUpdate, onCheckedChange = {
                        autoUpdate = it
                        appPrefs.edit().putBoolean("auto_update", it).apply()
                        showAppSelection = it
                        if (it) {
                            UpdateCheckWorker.schedule(context)
                        } else {
                            UpdateCheckWorker.cancel(context)
                        }
                    })
                })

                AnimatedVisibility(visible = showAppSelection && autoUpdate) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        val allSelected = installedStoreApps.isNotEmpty() && selectedApps.keys.size == installedStoreApps.size
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        installedStoreApps.forEach { selectedApps[it.first] = true }
                                    } else {
                                        selectedApps.clear()
                                    }
                                    appPrefs.edit().putStringSet("auto_update_selected_apps", selectedApps.keys.toSet()).apply()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select All", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${selectedApps.keys.size}/${installedStoreApps.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (installedStoreApps.isEmpty()) {
                            Text(
                                "No store apps installed yet. Apps downloaded from APK Store will appear here.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            installedStoreApps.forEach { (pkg, name, ver) ->
                                val isSelected = selectedApps.containsKey(pkg)
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isSelected) selectedApps.remove(pkg) else selectedApps[pkg] = true
                                            appPrefs.edit().putStringSet("auto_update_selected_apps", selectedApps.keys.toSet()).apply()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedApps[pkg] = true else selectedApps.remove(pkg)
                                            appPrefs.edit().putStringSet("auto_update_selected_apps", selectedApps.keys.toSet()).apply()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("v$ver  \u2022  $pkg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                SettingRow(title = "Silent auto-update", subtitle = "Download & install updates in background", trailing = {
                    Switch(checked = silentAutoUpdate, onCheckedChange = {
                        silentAutoUpdate = it
                        appPrefs.edit().putBoolean("silent_auto_update", it).apply()
                    })
                })
                if (silentAutoUpdate) {
                    SettingRow(title = "Run silent update now", subtitle = "Check and update all apps silently", trailing = {
                        TextButton(onClick = {
                            val intent = Intent(context, SilentAutoUpdateService::class.java).apply {
                                action = SilentAutoUpdateService.ACTION_CHECK_AND_UPDATE
                            }
                            context.startForegroundService(intent)
                        }) { Text("Run", color = MaterialTheme.colorScheme.primary) }
                    })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = "Notifications") {
                SettingRow(title = "Download notifications", subtitle = "Show download progress", trailing = {
                    Switch(checked = downloadNotifications, onCheckedChange = { downloadNotifications = it; appPrefs.edit().putBoolean("download_notifications", it).apply() })
                })
                SettingRow(title = "Update notifications", subtitle = "Notify when updates available", trailing = {
                    Switch(checked = updateNotifications, onCheckedChange = { updateNotifications = it; appPrefs.edit().putBoolean("update_notifications", it).apply() })
                })
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = "Security") {
                SettingRow(
                    title = "Install method",
                    subtitle = when (installMethod) {
                        "root" -> "Root (silent)"
                        "silent" -> "Silent Install (Wireless ADB)"
                        else -> "System installer"
                    },
                    trailing = {
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    onClick = { showInstallMethodDialog = true }
                )
                SettingRow(title = "Unknown sources", subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context.packageManager.canRequestPackageInstalls()) "Enabled" else "Not enabled", trailing = {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = Uri.parse("package:${context.packageName}"); flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }) { Text("Open", color = MaterialTheme.colorScheme.primary) }
                })
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = "Storage") {
                SettingRow(title = "Clear cache", subtitle = "Cache size: ${formatSize(cacheSize)}", trailing = {
                    TextButton(onClick = { context.cacheDir?.let { deleteDir(it) }; context.externalCacheDir?.let { deleteDir(it) }; cacheSize = 0L }) { Text("Clear", color = MaterialTheme.colorScheme.primary) }
                })
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = "About") {
                SettingRow(title = "Version", subtitle = BuildConfig.VERSION_NAME, trailing = {})
                SettingRow(title = "Privacy Policy", subtitle = "", trailing = { Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apkstore.app/privacy"))) })
                SettingRow(title = "Terms of Service", subtitle = "", trailing = { Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apkstore.app/terms"))) })
            }
        }
    }

    if (showInstallMethodDialog) {
        AlertDialog(
            onDismissRequest = { showInstallMethodDialog = false },
            title = { Text("Install Method", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Choose how apps should be installed:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    val methods = listOf(
                        Triple("root", "Root (silent)", "Requires rooted device"),
                        Triple("silent", "Silent Install (Wireless ADB)", "Built-in — no external app needed"),
                        Triple("system", "System installer", "Works on all devices")
                    )
                    methods.forEach { (key, name, desc) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                installMethod = key
                                appPrefs.edit().putString("install_method", key).apply()
                                showInstallMethodDialog = false
                            }.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = installMethod == key,
                                onClick = {
                                    installMethod = key
                                    appPrefs.edit().putString("install_method", key).apply()
                                    showInstallMethodDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { showInstallMethodDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ThemeOption(modifier: Modifier, label: String, index: Int, isSelected: Boolean, bgColor: Color, accentColor: Color, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(if (isSelected) Modifier.border(2.dp, accentColor, RoundedCornerShape(12.dp)) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(accentColor))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(4.dp)) { content() }
        }
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, trailing: @Composable () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) { Spacer(modifier = Modifier.height(2.dp)); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing()
    }
}

private fun getDirSize(file: java.io.File): Long { var size = 0L; file.listFiles()?.forEach { f -> if (f.isDirectory) size += getDirSize(f) else size += f.length() }; return size }
private fun deleteDir(file: java.io.File) { file.listFiles()?.forEach { deleteDir(it) }; file.delete() }
private fun formatSize(size: Long): String = when { size >= 1_073_741_824 -> String.format("%.1f GB", size / 1_073_741_824.0); size >= 1_048_576 -> String.format("%.1f MB", size / 1_048_576.0); size >= 1024 -> String.format("%.1f KB", size / 1024.0); else -> "$size B" }
