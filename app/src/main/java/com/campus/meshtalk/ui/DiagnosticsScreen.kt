package com.campus.meshtalk.ui

import android.os.Build
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campus.meshtalk.mesh.NearbyMeshManager
import com.campus.meshtalk.ui.components.*
import com.campus.meshtalk.ui.theme.MeshTalkTheme
import com.campus.meshtalk.ui.theme.TealBlob
import com.campus.meshtalk.ui.theme.PurpleBlob
import com.campus.meshtalk.util.PermissionHelper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dev.chrisbanes.haze.HazeState

@Composable
fun DiagnosticsScreen(
    nearbyManager: NearbyMeshManager,
    meshId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors

    val isAdvertising by nearbyManager.isAdvertising.collectAsState()
    val isDiscovering by nearbyManager.isDiscovering.collectAsState()
    val lastError by nearbyManager.lastError.collectAsState()
    val connectedPeers by nearbyManager.connectedPeers.collectAsState()
    val discoveredEndpoints by nearbyManager.discoveredEndpoints.collectAsState()
    val inFlightRequests by nearbyManager.inFlightRequests.collectAsState()

    val isBluetoothOn = remember { PermissionHelper.isBluetoothEnabled(context) }
    val isLocationOn = remember { PermissionHelper.isLocationEnabled(context) }
    val gmsStatus = remember {
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (code == ConnectionResult.SUCCESS) "Available (SUCCESS)" else "Unavailable ($code)"
    }

    MeshGlassScaffold(hazeState = hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Glass header
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                    }
                    Text(
                        text = "Mesh Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = "Advertising",
                    value = if (isAdvertising) "ON" else "OFF",
                    color = if (isAdvertising) TealBlob else Color(0xFFFF5252),
                    hazeState = hazeState,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Discovery",
                    value = if (isDiscovering) "ON" else "OFF",
                    color = if (isDiscovering) TealBlob else Color(0xFFFF5252),
                    hazeState = hazeState,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Peers",
                    value = connectedPeers.size.toString(),
                    color = PurpleBlob,
                    hazeState = hazeState,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mesh State Card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Mesh State", color = theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    DiagRow("Local MT ID", meshId)
                    DiagRow("Last Error", lastError ?: "None")
                    DiagRow("In-Flight Requests", inFlightRequests.size.toString())
                    DiagRow("Discovered Endpoints", discoveredEndpoints.size.toString())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connected Peers Details
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Connected Peers (${connectedPeers.size})", color = theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (connectedPeers.isEmpty()) {
                        Text("No peers currently connected", color = theme.textMuted, fontSize = 13.sp)
                    } else {
                        connectedPeers.forEach { (mtId, peer) ->
                            DiagRow(peer.endpointId, mtId)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Discovered Endpoints
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Discovered in Range (${discoveredEndpoints.size})", color = theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (discoveredEndpoints.isEmpty()) {
                        Text("Scanning for nearby mesh devices...", color = theme.textMuted, fontSize = 13.sp)
                    } else {
                        discoveredEndpoints.forEach { (endpointId, info) ->
                            DiagRow(endpointId, info.endpointName)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // System & Radio State
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("System & Radio State", color = theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    DiagRow("Bluetooth Enabled", if (isBluetoothOn) "YES" else "NO")
                    DiagRow("Location Enabled", if (isLocationOn) "YES" else "NO")
                    DiagRow("Google Play Services", gmsStatus)
                    DiagRow("Android OS", "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Restart Mesh Button
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                GradientButton(
                    onClick = { nearbyManager.restartMesh() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restart Mesh", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val theme = MeshTalkTheme.colors

    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        hazeState = hazeState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = theme.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    val theme = MeshTalkTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = theme.textSecondary, fontSize = 13.sp)
        Text(value, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
