package com.campus.meshtalk.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.campus.meshtalk.ui.components.*
import com.campus.meshtalk.ui.theme.MeshTalkTheme
import com.campus.meshtalk.ui.theme.TealBlob
import com.campus.meshtalk.ui.theme.PurpleBlob
import com.campus.meshtalk.util.PermissionHelper
import dev.chrisbanes.haze.HazeState

@Composable
fun PermissionScreen(
    onRequestPermissions: () -> Unit,
    missingPermissions: List<String> = emptyList()
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors

    var hasRequestedOnce by remember { mutableStateOf(false) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }
    var bluetoothEnabled by remember { mutableStateOf(PermissionHelper.isBluetoothEnabled(context)) }
    var locationEnabled by remember { mutableStateOf(PermissionHelper.isLocationEnabled(context)) }

    fun refreshHardwareState() {
        bluetoothEnabled = PermissionHelper.isBluetoothEnabled(context)
        locationEnabled = PermissionHelper.isLocationEnabled(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasRequestedOnce = true
        refreshHardwareState()

        val allGranted = PermissionHelper.hasNearbyPermissions(context)
        if (allGranted) {
            onRequestPermissions()
        } else {
            val anyShouldRationale = activity?.let { act ->
                PermissionHelper.getNearbyPermissions().any { perm ->
                    ActivityCompat.shouldShowRequestPermissionRationale(act, perm)
                }
            } ?: false
            isPermanentlyDenied = !anyShouldRationale
        }
    }

    LaunchedEffect(Unit) {
        refreshHardwareState()
        if (PermissionHelper.hasNearbyPermissions(context)) {
            onRequestPermissions()
        }
    }

    MeshGlassScaffold(hazeState = hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                hazeState = hazeState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = TealBlob,
                        modifier = Modifier.size(54.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isPermanentlyDenied) "Permission Required" else "Mesh Setup Permissions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isPermanentlyDenied) {
                            "Nearby devices permission was permanently denied. Please enable it in Android Settings to discover mesh peers."
                        } else {
                            "MeshTalk operates offline by creating a peer-to-peer mesh between nearby phones."
                        },
                        color = theme.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    PermissionGroupItem(
                        icon = Icons.Default.Bluetooth,
                        title = "Nearby Devices & Bluetooth",
                        description = "Find and talk to people around you without internet",
                        isGranted = PermissionHelper.hasNearbyPermissions(context),
                        hazeState = hazeState
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionGroupItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        description = "Alerts for incoming mesh messages and peer requests",
                        isGranted = PermissionHelper.hasNotificationPermission(context),
                        hazeState = hazeState
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionGroupItem(
                        icon = Icons.Default.QrCodeScanner,
                        title = "Camera (On demand)",
                        description = "Used only when scanning contact QR codes (requested later)",
                        isGranted = PermissionHelper.hasCameraPermission(context),
                        hazeState = hazeState
                    )

                    if (!bluetoothEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ServiceStatusAlert(
                            message = "Bluetooth is turned off. Turn it on to join the mesh.",
                            buttonLabel = "Enable Bluetooth",
                            onClick = {
                                PermissionHelper.openBluetoothSettings(context)
                                refreshHardwareState()
                            },
                            hazeState = hazeState
                        )
                    }

                    if (!locationEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ServiceStatusAlert(
                            message = "Location service is turned off. Required on this Android version for BLE.",
                            buttonLabel = "Enable Location",
                            onClick = {
                                PermissionHelper.openLocationSettings(context)
                                refreshHardwareState()
                            },
                            hazeState = hazeState
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isPermanentlyDenied) {
                        GradientButton(
                            onClick = { PermissionHelper.openAppSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Open App Settings",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        GradientButton(
                            onClick = {
                                permissionLauncher.launch(PermissionHelper.getRequiredStartupPermissions())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                if (hasRequestedOnce) "Retry Permissions" else "Grant Permissions",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    hazeState: HazeState
) {
    val theme = MeshTalkTheme.colors

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        hazeState = hazeState
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isGranted) TealBlob else PurpleBlob,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(description, color = theme.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = TealBlob,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ServiceStatusAlert(
    message: String,
    buttonLabel: String,
    onClick: () -> Unit,
    hazeState: HazeState
) {
    val theme = MeshTalkTheme.colors

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        hazeState = hazeState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(message, color = Color(0xFFFFB74D), fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(buttonLabel, color = TealBlob, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
