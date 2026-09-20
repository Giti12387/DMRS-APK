package com.apps.apkstore.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apps.apkstore.BuildConfig
import com.apps.apkstore.ui.theme.*

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToUpdates: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("zoro_prefs", Context.MODE_PRIVATE)
    val userEmail = prefs.getString("user_email", "user@email.com") ?: "user@email.com"
    val userName = userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(userName.first().toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Primary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(userName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(userEmail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        ProfileSection(title = "My Apps") {
            ProfileMenuItem(icon = Icons.Outlined.Update, title = "Updates", subtitle = "Check for app updates", onClick = onNavigateToUpdates)
            ProfileMenuItem(icon = Icons.Outlined.CloudDownload, title = "Downloads", subtitle = "View download history", onClick = onNavigateToDownloads)
        }

        ProfileSection(title = "Preferences") {
            ProfileMenuItem(icon = Icons.Outlined.Settings, title = "Settings", subtitle = "Notifications, auto-update, storage", onClick = onNavigateToSettings)
            ProfileMenuItem(icon = Icons.Outlined.Security, title = "Unknown Sources", subtitle = "Allow installation from this source", onClick = {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            })
        }

        ProfileSection(title = "About") {
            ProfileMenuItem(icon = Icons.Outlined.Info, title = "About APK Store", subtitle = "Version ${BuildConfig.VERSION_NAME}", onClick = {})
            ProfileMenuItem(icon = Icons.Outlined.Description, title = "Terms of Service", subtitle = "", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://apkstore.app/terms"))
                context.startActivity(intent)
            })
            ProfileMenuItem(icon = Icons.Outlined.PrivacyTip, title = "Privacy Policy", subtitle = "", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://apkstore.app/privacy"))
                context.startActivity(intent)
            })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable {
                    prefs.edit().clear().apply()
                    onLogout()
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Logout, null, tint = Error, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sign Out", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Error)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(content = content)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
    }
}
