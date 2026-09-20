package com.apps.apkstore.ui.auth

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apps.apkstore.service.SilentInstallManager
import com.apps.apkstore.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallMethodScreen(onMethodChosen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMethod by remember { mutableStateOf<String?>(null) }
    var deviceSupports by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var adbConnected by remember { mutableStateOf(false) }
    var checkingAdb by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        deviceSupports = mapOf(
            "root" to checkRoot(),
            "system" to true
        )
        adbConnected = try {
            SilentInstallManager.checkAdbConnection()
        } catch (e: Exception) { false }
        deviceSupports = deviceSupports + ("silent" to adbConnected)
        checkingAdb = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Choose Install Method",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Select how APK Store should install apps on your device.\nYou can change this later in Settings.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            InstallMethodCard(
                title = "Root Install",
                subtitle = "Silent install via root access",
                description = "Installs apps silently in background using root (su) command. Fastest method, no dialogs.",
                icon = Icons.Filled.PhoneAndroid,
                iconColor = Color(0xFF00C896),
                isAvailable = deviceSupports["root"] == true,
                notAvailableReason = if (deviceSupports["root"] == false) "Device is not rooted" else "Ready to use",
                isSelected = selectedMethod == "root",
                onClick = { selectedMethod = "root" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstallMethodCard(
                title = "Silent Install (Wireless ADB)",
                subtitle = "No external app needed \u2014 built into APK Store",
                description = if (adbConnected) "Wireless ADB connected. Apps will install silently."
                else "Built-in ADB client. One-time setup: enable Wireless Debugging & pair. No Shizuku app needed!",
                icon = Icons.Filled.Code,
                iconColor = Color(0xFF4488FF),
                isAvailable = adbConnected,
                notAvailableReason = if (checkingAdb) "Checking..."
                    else if (adbConnected) "Connected \u2014 ready"
                    else "Tap to set up Wireless ADB",
                isSelected = selectedMethod == "silent",
                onClick = {
                    selectedMethod = "silent"
                    if (!adbConnected) {
                        showPairingDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstallMethodCard(
                title = "System Installer",
                subtitle = "Standard Android install dialog",
                description = "Opens the system package installer. You tap Install each time. Works on all devices.",
                icon = Icons.Filled.Apps,
                iconColor = Color(0xFFFFAA33),
                isAvailable = true,
                notAvailableReason = "Always available",
                isSelected = selectedMethod == "system",
                onClick = { selectedMethod = "system" }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (selectedMethod != null) {
                        onMethodChosen(selectedMethod!!)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                enabled = selectedMethod != null
            ) {
                Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedMethod == "root" && deviceSupports["root"] == false) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Warning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Root not available", fontSize = 13.sp, color = Warning, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Will fall back to system installer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (selectedMethod == "silent" && !adbConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showPairingDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4488FF))
                ) {
                    Icon(Icons.Filled.Link, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Up Wireless ADB", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (selectedMethod != null && deviceSupports[selectedMethod] == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Method ready \u2014 apps will install silently",
                    fontSize = 12.sp,
                    color = Success.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showPairingDialog) {
        WirelessAdbPairingDialog(
            onDismiss = { showPairingDialog = false },
            onPaired = {
                adbConnected = true
                deviceSupports = deviceSupports + ("silent" to true)
                showPairingDialog = false
            },
            context = context
        )
    }
}

@Composable
fun WirelessAdbPairingDialog(
    onDismiss: () -> Unit,
    onPaired: () -> Unit,
    context: android.content.Context
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var pairingCode by remember { mutableStateOf("") }
    var portStr by remember { mutableStateOf("") }
    var hostStr by remember { mutableStateOf("127.0.0.1") }
    var pairing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!pairing) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                when (step) {
                    1 -> "Step 1: Enable Wireless Debugging"
                    2 -> "Step 2: Enter Pairing Code"
                    else -> ""
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    1 -> {
                        Text(
                            "Follow these steps (one-time setup):",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        StepItem("1", "Go to Settings > Developer Options")
                        StepItem("2", "Enable \"Wireless Debugging\"")
                        StepItem("3", "Tap \"Pair device with pairing code\"")
                        StepItem("4", "Note the IP address, port & 6-digit code")
                        StepItem("5", "Come back here and tap Next")

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4488FF))
                        ) {
                            Icon(Icons.Filled.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Developer Options")
                        }
                    }

                    2 -> {
                        Text(
                            "Enter the pairing details from Wireless Debugging:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = portStr,
                            onValueChange = { portStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Pairing Port (e.g. 37755)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { if (it.length <= 6) pairingCode = it.filter { c -> c.isDigit() } },
                            label = { Text("6-Digit Pairing Code") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            supportingText = { Text("${pairingCode.length}/6") }
                        )

                        if (error != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    error!!,
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 12.sp,
                                    color = Error
                                )
                            }
                        }

                        if (success) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Connected! You can now install apps silently.", fontSize = 12.sp, color = Success)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == 1) {
                    TextButton(onClick = { step = 2 }) {
                        Text("Next")
                    }
                }
                if (step == 2) {
                    TextButton(
                        onClick = {
                            if (portStr.isBlank() || pairingCode.length != 6) {
                                error = "Enter valid port and 6-digit code"
                                return@TextButton
                            }
                            pairing = true
                            error = null
                            scope.launch {
                                try {
                                    val port = portStr.toInt()
                                    val result = SilentInstallManager.pairDevice(hostStr, port, pairingCode)
                                    pairing = false
                                    if (result) {
                                        success = true
                                        kotlinx.coroutines.delay(1500)
                                        onPaired()
                                    } else {
                                        error = "Pairing failed. Check port & code, then try again."
                                    }
                                } catch (e: Exception) {
                                    pairing = false
                                    error = "Error: ${e.message}"
                                }
                            }
                        },
                        enabled = !pairing && pairingCode.length == 6 && portStr.isNotBlank()
                    ) {
                        if (pairing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pairing...")
                        } else {
                            Text("Pair & Connect")
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (!pairing) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun StepItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun InstallMethodCard(
    title: String,
    subtitle: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isAvailable: Boolean,
    notAvailableReason: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), lineHeight = 18.sp)

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isAvailable) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        null,
                        tint = if (isAvailable) Success else Warning,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        notAvailableReason ?: "",
                        fontSize = 11.sp,
                        color = if (isAvailable) Success else Warning
                    )
                }
            }
        }
    }
}

private fun checkRoot(): Boolean {
    return try {
        val process = Runtime.getRuntime().exec("which su")
        val reader = process.inputStream.bufferedReader()
        val result = reader.readLine()
        process.waitFor()
        !result.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }
}
