package com.campus.meshtalk.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campus.meshtalk.data.local.entity.BlockedUserEntity
import com.campus.meshtalk.data.preferences.BleScanMode
import com.campus.meshtalk.data.preferences.ThemeMode
import com.campus.meshtalk.ui.components.*
import com.campus.meshtalk.ui.theme.MeshTalkTheme
import com.campus.meshtalk.ui.theme.TealBlob
import com.campus.meshtalk.ui.theme.PurpleBlob
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    meshId: String,
    nickname: String,
    avatarUri: String?,
    identiconSeed: String?,
    currentThemeMode: ThemeMode,
    reduceTransparency: Boolean,
    currentScanMode: BleScanMode = BleScanMode.BALANCED,
    relayForOthers: Boolean = true,
    relayOnlyWhileCharging: Boolean = false,
    blockedUsers: List<BlockedUserEntity> = emptyList(),
    onThemeModeChanged: (ThemeMode) -> Unit,
    onReduceTransparencyChanged: (Boolean) -> Unit,
    onScanModeChanged: (BleScanMode) -> Unit = {},
    onRelayForOthersChanged: (Boolean) -> Unit = {},
    onRelayOnlyWhileChargingChanged: (Boolean) -> Unit = {},
    onUnblockUser: (String) -> Unit = {},
    onEditProfile: () -> Unit,
    onViewIdentity: () -> Unit,
    onDiagnostics: () -> Unit,
    onResetIdentity: () -> Unit,
    onBack: () -> Unit
) {
    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors
    var showThemeDialog by remember { mutableStateOf(false) }
    var showScanModeDialog by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

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
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Profile card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                hazeState = hazeState,
                onClick = onEditProfile
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MeshAvatar(
                        size = 56.dp,
                        avatarUri = avatarUri,
                        identiconSeed = identiconSeed,
                        displayName = nickname
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nickname, color = theme.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Mesh ID: ${meshId.take(16)}...", color = theme.textSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.textMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance section
            SectionHeader("Appearance")

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column {
                    // Theme mode
                    SettingsRow(
                        icon = Icons.Default.DarkMode,
                        iconTint = PurpleBlob,
                        title = "Theme",
                        subtitle = when (currentThemeMode) {
                            ThemeMode.SYSTEM -> "Follow system"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.LIGHT -> "Light"
                        },
                        onClick = { showThemeDialog = true }
                    )

                    HorizontalDivider(color = theme.border, modifier = Modifier.padding(horizontal = 16.dp))

                    // Reduce transparency toggle
                    SettingsToggleRow(
                        icon = Icons.Default.Accessibility,
                        iconTint = TealBlob,
                        title = "Reduce Transparency",
                        subtitle = "Use solid backgrounds instead of blur (saves battery on older devices)",
                        checked = reduceTransparency,
                        onCheckedChange = onReduceTransparencyChanged
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Network & Identity section
            SectionHeader("Network & Identity")

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Fingerprint,
                        iconTint = TealBlob,
                        title = "View Identity & Keys",
                        subtitle = "Your public key and node fingerprint",
                        onClick = onViewIdentity
                    )

                    HorizontalDivider(color = theme.border, modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsRow(
                        icon = Icons.Default.BugReport,
                        iconTint = Color(0xFFFFB300),
                        title = "Diagnostics",
                        subtitle = "Mesh connectivity stats and logs",
                        onClick = onDiagnostics
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mesh & Battery section
            SectionHeader("Mesh & Battery")

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.BatteryChargingFull,
                        iconTint = TealBlob,
                        title = "Scan & Relay Mode",
                        subtitle = when (currentScanMode) {
                            BleScanMode.LOW_POWER -> "Low Power (Scans every ~30s, minimal battery impact)"
                            BleScanMode.BALANCED -> "Balanced (Scans every ~15s, estimated ~1-2% / hr)"
                            BleScanMode.HIGH_PERFORMANCE -> "High Performance (Continuous discovery, highest responsive mesh)"
                        },
                        onClick = { showScanModeDialog = true }
                    )

                    HorizontalDivider(color = theme.border, modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsToggleRow(
                        icon = Icons.Default.Sync,
                        iconTint = PurpleBlob,
                        title = "Relay for Others",
                        subtitle = "Forward encrypted packets between distant peers",
                        checked = relayForOthers,
                        onCheckedChange = onRelayForOthersChanged
                    )

                    HorizontalDivider(color = theme.border, modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsToggleRow(
                        icon = Icons.Default.Power,
                        iconTint = TealBlob,
                        title = "Relay Only While Charging",
                        subtitle = "Conserves battery by relaying only when connected to power",
                        checked = relayOnlyWhileCharging,
                        onCheckedChange = onRelayOnlyWhileChargingChanged
                    )

                    HorizontalDivider(color = theme.border, modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsRow(
                        icon = Icons.Default.Block,
                        iconTint = Color(0xFFFF5252),
                        title = "Blocked Users",
                        subtitle = if (blockedUsers.isEmpty()) "No blocked mesh IDs" else "${blockedUsers.size} blocked node(s)",
                        onClick = { showBlockedUsersDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Danger zone
            SectionHeader("Danger Zone")

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState,
                borderColor = Color(0xFFFF5252).copy(alpha = 0.3f)
            ) {
                SettingsRow(
                    icon = Icons.Default.DeleteForever,
                    iconTint = Color(0xFFFF5252),
                    title = "Reset Identity",
                    subtitle = "Generates a new keypair. All contacts lost.",
                    titleColor = Color(0xFFFF5252),
                    onClick = { showResetConfirm = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Theme picker dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme", color = theme.textPrimary) },
            containerColor = if (theme.reduceTransparency) theme.surfaceSolidFallback else theme.background.copy(alpha = 0.95f),
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentThemeMode == mode,
                                onClick = {
                                    onThemeModeChanged(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = TealBlob)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (mode) {
                                    ThemeMode.SYSTEM -> "Follow System"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.LIGHT -> "Light"
                                },
                                color = theme.textPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel", color = TealBlob)
                }
            }
        )
    }

    // Scan mode picker dialog
    if (showScanModeDialog) {
        AlertDialog(
            onDismissRequest = { showScanModeDialog = false },
            title = { Text("Scan & Relay Mode", color = theme.textPrimary) },
            containerColor = if (theme.reduceTransparency) theme.surfaceSolidFallback else theme.background.copy(alpha = 0.95f),
            text = {
                Column {
                    BleScanMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentScanMode == mode,
                                onClick = {
                                    onScanModeChanged(mode)
                                    showScanModeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = TealBlob)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when (mode) {
                                        BleScanMode.LOW_POWER -> "Low Power"
                                        BleScanMode.BALANCED -> "Balanced"
                                        BleScanMode.HIGH_PERFORMANCE -> "High Performance"
                                    },
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (mode) {
                                        BleScanMode.LOW_POWER -> "Scan interval ~30s (Max battery savings)"
                                        BleScanMode.BALANCED -> "Scan interval ~15s (Recommended)"
                                        BleScanMode.HIGH_PERFORMANCE -> "Continuous radio activity (Faster message hops)"
                                    },
                                    color = theme.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScanModeDialog = false }) {
                    Text("Cancel", color = TealBlob)
                }
            }
        )
    }

    // Blocked users management dialog
    if (showBlockedUsersDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedUsersDialog = false },
            title = { Text("Blocked Users", color = theme.textPrimary) },
            containerColor = if (theme.reduceTransparency) theme.surfaceSolidFallback else theme.background.copy(alpha = 0.95f),
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("You have not blocked any users.", color = theme.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(blockedUsers, key = { it.meshTalkId }) { blocked ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = blocked.nickname.ifBlank { blocked.meshTalkId },
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = blocked.meshTalkId,
                                        color = theme.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                TextButton(
                                    onClick = { onUnblockUser(blocked.meshTalkId) }
                                ) {
                                    Text("Unblock", color = TealBlob, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedUsersDialog = false }) {
                    Text("Close", color = TealBlob)
                }
            }
        )
    }

    // Reset confirmation
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Identity?", color = Color(0xFFFF5252)) },
            containerColor = if (theme.reduceTransparency) theme.surfaceSolidFallback else theme.background.copy(alpha = 0.95f),
            text = {
                Text(
                    "This will generate a new keypair and delete ALL contacts and messages. This action cannot be undone.",
                    color = theme.textPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetIdentity()
                }) {
                    Text("Reset", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val theme = MeshTalkTheme.colors
    Text(
        text = title,
        color = theme.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    titleColor: Color = MeshTalkTheme.colors.textPrimary,
    onClick: () -> Unit
) {
    val theme = MeshTalkTheme.colors

    ListItem(
        headlineContent = { Text(title, color = titleColor, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let { { Text(it, color = theme.textSecondary, fontSize = 12.sp) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = iconTint) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.textMuted) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = MeshTalkTheme.colors

    ListItem(
        headlineContent = { Text(title, color = theme.textPrimary, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let { { Text(it, color = theme.textSecondary, fontSize = 12.sp) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = iconTint) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TealBlob,
                    uncheckedThumbColor = theme.textMuted,
                    uncheckedTrackColor = theme.surfaceFill
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
