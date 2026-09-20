package com.apps.apkstore.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.apps.apkstore.data.repository.AppRepository
import com.apps.apkstore.ui.auth.LoginScreen
import com.apps.apkstore.ui.auth.InstallMethodScreen
import com.apps.apkstore.ui.theme.*
import com.apps.apkstore.utils.ConfigManager
import com.apps.apkstore.utils.SelfUpdateManager
import com.apps.apkstore.utils.SupabaseConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: AppRepository

    private val notifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var deepLinkAppId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        deepLinkAppId.value = extractAppIdFromIntent(intent)

        SupabaseConfig.initAuthConfig(this)
        ConfigManager.fetchAndApplyConfig(this)

        SelfUpdateManager.checkForUpdate(this) {
            setContent {
                val prefs = remember { getSharedPreferences("zoro_prefs", Context.MODE_PRIVATE) }
                val appPrefs = remember { getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE) }
                val themeMode = remember { mutableIntStateOf(appPrefs.getInt("theme_mode", 0)) }
                var loggedIn by remember { mutableStateOf(prefs.getBoolean("logged_in", false)) }
                var installMethodChosen by remember { mutableStateOf(appPrefs.getBoolean("install_method_chosen", false)) }

                val onThemeChanged: (Int) -> Unit = { newTheme ->
                    themeMode.intValue = newTheme
                    appPrefs.edit().putInt("theme_mode", newTheme).apply()
                }

                ProvideAppTheme(themeMode = themeMode.intValue, onThemeChanged = onThemeChanged) {
                    var unknownSourcesOk by remember { mutableStateOf(checkUnknownSources(this@MainActivity)) }

                    LaunchedEffect(Unit) {
                        unknownSourcesOk = checkUnknownSources(this@MainActivity)
                    }

                    if (!unknownSourcesOk) {
                        UnknownSourcesBlockingScreen(onOpenSettings = {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        })
                    } else if (!loggedIn) {
                        LoginScreen(
                            repository = repository,
                            onLoginSuccess = {
                                loggedIn = true
                            },
                            onBack = { finish() }
                        )
                    } else if (!installMethodChosen) {
                        InstallMethodScreen(
                            onMethodChosen = { method ->
                                appPrefs.edit().putString("install_method", method).apply()
                                appPrefs.edit().putBoolean("install_method_chosen", true).apply()
                                installMethodChosen = true
                            }
                        )
                    } else {
                        val currentDeepLink by deepLinkAppId
                        MainScaffold(
                            onLogout = { loggedIn = false },
                            onThemeChanged = onThemeChanged,
                            currentThemeMode = themeMode.intValue,
                            deepLinkAppId = currentDeepLink
                        )
                        LaunchedEffect(currentDeepLink) {
                            if (currentDeepLink != null) {
                                deepLinkAppId.value = null
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkAppId.value = extractAppIdFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("zoro_prefs", Context.MODE_PRIVATE)
        val appPrefs = getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE)
        val loggedIn = prefs.getBoolean("logged_in", false)
        val unknownOk = checkUnknownSources(this)
        val themeMode = appPrefs.getInt("theme_mode", 0)

        setContent {
            ProvideAppTheme(themeMode = themeMode, onThemeChanged = { newTheme ->
                appPrefs.edit().putInt("theme_mode", newTheme).apply()
            }) {
                var loggedInState by remember { mutableStateOf(loggedIn) }
                var unknownOkState by remember { mutableStateOf(unknownOk) }
                var installMethodChosenState by remember { mutableStateOf(appPrefs.getBoolean("install_method_chosen", false)) }

                if (!unknownOkState) {
                    UnknownSourcesBlockingScreen(onOpenSettings = {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    })
                } else if (!loggedInState) {
                        LoginScreen(repository = repository, onLoginSuccess = { loggedInState = true }, onBack = { finish() })
                    } else if (!installMethodChosenState) {
                        InstallMethodScreen(
                            onMethodChosen = { method ->
                                appPrefs.edit().putString("install_method", method).apply()
                                appPrefs.edit().putBoolean("install_method_chosen", true).apply()
                                installMethodChosenState = true
                            }
                        )
                    } else {
                        val appPrefs2 = getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE)
                        val tm = remember { mutableIntStateOf(appPrefs2.getInt("theme_mode", 0)) }
                        val currentDeepLink by deepLinkAppId
                        ProvideAppTheme(themeMode = tm.intValue, onThemeChanged = { newTheme ->
                            tm.intValue = newTheme
                            appPrefs2.edit().putInt("theme_mode", newTheme).apply()
                        }) {
                            MainScaffold(onLogout = { loggedInState = false }, onThemeChanged = { newTheme ->
                                tm.intValue = newTheme
                                appPrefs2.edit().putInt("theme_mode", newTheme).apply()
                            }, currentThemeMode = tm.intValue, deepLinkAppId = currentDeepLink)
                        }
                    }
            }
        }
    }

    private fun checkUnknownSources(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun extractAppIdFromIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val host = uri.host ?: return null
        if (host != "apk-store-topaz.vercel.app") return null

        val path = uri.path ?: return null
        val query = uri.query

        // Format: /app-name/version/share?{appId}
        if (path.endsWith("/share") && !query.isNullOrEmpty()) {
            return query
        }

        // Format: /app/{appId}
        if (path.startsWith("/app/")) {
            return path.removePrefix("/app/").takeIf { it.isNotEmpty() }
        }

        return null
    }
}

@Composable
fun UnknownSourcesBlockingScreen(onOpenSettings: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.InstallMobile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(28.dp))
            Text("Permission Required", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "APK Store needs permission to install apps.\n\nPlease enable \"Allow from this source\" in the next screen to continue.",
                fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), textAlign = TextAlign.Center, lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Enable Permission", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Go back to this app after enabling", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        }
    }
}
