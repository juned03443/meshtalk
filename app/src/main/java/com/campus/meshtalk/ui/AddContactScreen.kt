package com.campus.meshtalk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campus.meshtalk.data.local.entity.ConnectionRequestEntity
import com.campus.meshtalk.data.local.entity.ContactEntity
import com.campus.meshtalk.data.local.entity.RequestDirection
import com.campus.meshtalk.data.local.entity.RequestStatus
import com.campus.meshtalk.mesh.MeshPeer
import com.campus.meshtalk.ui.components.*
import com.campus.meshtalk.ui.theme.MeshTalkTheme
import com.campus.meshtalk.ui.theme.TealBlob
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

enum class AddContactTab(val title: String) {
    PEOPLE_NEARBY("People Nearby"),
    ADD_BY_ID("Add by ID"),
    REQUESTS("Requests"),
    CONTACTS("Contacts")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    myMeshId: String,
    uiState: ContactsUiState,
    contacts: List<ContactEntity>,
    incomingRequests: List<ConnectionRequestEntity>,
    outgoingRequests: List<ConnectionRequestEntity>,
    incomingBadgeCount: Int,
    connectedPeers: Map<String, MeshPeer>,
    nearbyPeople: List<com.campus.meshtalk.data.repository.NearbyPerson> = emptyList(),
    onMeshIdInputChanged: (String) -> Unit,
    onSendRequest: () -> Unit,
    onSendRequestToPerson: (com.campus.meshtalk.data.repository.NearbyPerson) -> Unit = {},
    onAcceptRequest: (requestId: String) -> Unit,
    onDeleteRequest: (requestId: String) -> Unit,
    onCancelRequest: (requestId: String) -> Unit,
    onOpenChat: (peerId: String, peerName: String) -> Unit = { _, _ -> },
    onRestartDiscovery: () -> Unit = {},
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AddContactTab.PEOPLE_NEARBY) }
    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors
    val context = LocalContext.current

    MeshGlassScaffold(hazeState = hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Glass Header
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.textPrimary
                        )
                    }
                    Text(
                        text = "Add Contact",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary
                    )
                }
            }

            // Tab Bar
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                hazeState = hazeState
            ) {
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = theme.textPrimary,
                    indicator = { tabPositions ->
                        if (selectedTab.ordinal < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                color = theme.onlineStatus,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {}
                ) {
                    AddContactTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            selectedContentColor = theme.textPrimary,
                            unselectedContentColor = theme.textSecondary,
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = tab.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) theme.textPrimary else theme.textSecondary,
                                        maxLines = 1
                                    )
                                    // Badge on Requests tab
                                    if (tab == AddContactTab.REQUESTS && incomingBadgeCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Badge(
                                            containerColor = TealBlob,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = incomingBadgeCount.toString(),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                AddContactTab.PEOPLE_NEARBY -> {
                    PeopleNearbyTab(
                        nearbyPeople = nearbyPeople,
                        hazeState = hazeState,
                        onSendRequest = onSendRequestToPerson,
                        onAccept = onAcceptRequest,
                        onDecline = onDeleteRequest,
                        onCancel = onCancelRequest,
                        onOpenChat = onOpenChat,
                        onRefresh = onRestartDiscovery
                    )
                }
                AddContactTab.ADD_BY_ID -> {
                    AddByIdTab(
                        myMeshId = myMeshId,
                        uiState = uiState,
                        hazeState = hazeState,
                        onMeshIdInputChanged = onMeshIdInputChanged,
                        onSendRequest = onSendRequest,
                        context = context
                    )
                }
                AddContactTab.REQUESTS -> {
                    RequestsTab(
                        incomingRequests = incomingRequests,
                        outgoingRequests = outgoingRequests,
                        hazeState = hazeState,
                        onAccept = onAcceptRequest,
                        onDelete = onDeleteRequest,
                        onCancel = onCancelRequest,
                        context = context
                    )
                }
                AddContactTab.CONTACTS -> {
                    ContactsTab(
                        contacts = contacts,
                        connectedPeers = connectedPeers,
                        hazeState = hazeState
                    )
                }
            }
        }
    }
}

// ─── Tab 0: People Nearby ────────────────────────────────────────────────────

@Composable
private fun PeopleNearbyTab(
    nearbyPeople: List<com.campus.meshtalk.data.repository.NearbyPerson>,
    hazeState: HazeState,
    onSendRequest: (com.campus.meshtalk.data.repository.NearbyPerson) -> Unit,
    onAccept: (requestId: String) -> Unit,
    onDecline: (requestId: String) -> Unit,
    onCancel: (requestId: String) -> Unit,
    onOpenChat: (peerId: String, peerName: String) -> Unit,
    onRefresh: () -> Unit
) {
    val theme = MeshTalkTheme.colors

    if (nearbyPeople.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.WifiFind,
                    contentDescription = null,
                    tint = theme.textMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Searching for nearby peers...",
                    color = theme.textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Keep Bluetooth & Wi-Fi active. Nearby MeshTalk users will appear here.",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                GradientButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restart Discovery", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${nearbyPeople.size} PEOPLE NEARBY",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textSecondary,
                    letterSpacing = 1.sp
                )
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TealBlob,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", color = TealBlob, fontSize = 12.sp)
                }
            }
        }

        items(nearbyPeople, key = { it.meshId }) { person ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                hazeState = hazeState
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        MeshAvatar(
                            size = 46.dp,
                            identiconSeed = person.meshId,
                            displayName = person.displayName
                        )
                        OnlineStatusIndicator(
                            isOnline = person.isConnected,
                            size = 8.dp,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = person.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = person.meshId,
                            color = TealBlob,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (person.isConnected) "Connected" else "Discovered",
                            color = if (person.isConnected) TealBlob else theme.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    when (person.relation) {
                        com.campus.meshtalk.data.repository.PersonRelation.NONE -> {
                            GradientButton(
                                onClick = { onSendRequest(person) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        com.campus.meshtalk.data.repository.PersonRelation.REQUEST_SENT -> {
                            Text("Pending", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        com.campus.meshtalk.data.repository.PersonRelation.REQUEST_RECEIVED -> {
                            GradientButton(
                                onClick = { /* accept handled elsewhere */ },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Accept", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        com.campus.meshtalk.data.repository.PersonRelation.CONTACT -> {
                            TextButton(onClick = { onOpenChat(person.meshId, person.displayName) }) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = TealBlob,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chat", color = TealBlob, fontSize = 12.sp)
                            }
                        }
                        com.campus.meshtalk.data.repository.PersonRelation.BLOCKED -> {
                            // Don't show blocked people, but handle the case
                        }
                    }
                }
            }
        }
    }
}

// ─── Tab 1: Add by ID ───────────────────────────────────────────────────────

@Composable
private fun AddByIdTab(
    myMeshId: String,
    uiState: ContactsUiState,
    hazeState: HazeState,
    onMeshIdInputChanged: (String) -> Unit,
    onSendRequest: () -> Unit,
    context: Context
) {
    val theme = MeshTalkTheme.colors
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // My ID card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = TealBlob,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "My MeshTalk ID",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ID display
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        hazeState = hazeState
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = myMeshId,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TealBlob,
                                modifier = Modifier.weight(1f),
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Copy and Share buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GradientButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("MeshTalk ID", myMeshId))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        GradientButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Add me on MeshTalk! My ID: $myMeshId")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share MeshTalk ID"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Share your ID with others so they can add you as a contact",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Add by ID form
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = TealBlob,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send Connection Request",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Enter a peer's MeshTalk ID to send an encrypted connection request across the mesh network.",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ID Input with auto-formatting
                    OutlinedTextField(
                        value = uiState.formattedMeshId.ifEmpty { "MT-" },
                        onValueChange = { onMeshIdInputChanged(it) },
                        label = { Text("MeshTalk ID") },
                        placeholder = { Text("MT-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        isError = uiState.validationError != null,
                        supportingText = if (uiState.validationError != null) {
                            {
                                Text(
                                    text = uiState.validationError,
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp
                                )
                            }
                        } else if (uiState.isValidFormat) {
                            {
                                Text(
                                    text = "Valid MeshTalk ID format",
                                    color = TealBlob,
                                    fontSize = 12.sp
                                )
                            }
                        } else null,
                        trailingIcon = {
                            if (uiState.isValidFormat && uiState.validationError == null) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Valid",
                                    tint = TealBlob
                                )
                            } else if (uiState.validationError != null) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF5252)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                focusManager.clearFocus()
                                onSendRequest()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedBorderColor = if (uiState.validationError != null) Color(0xFFFF5252) else theme.onlineStatus,
                            unfocusedBorderColor = theme.border,
                            focusedLabelColor = theme.onlineStatus,
                            unfocusedLabelColor = theme.textSecondary,
                            cursorColor = TealBlob
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GradientButton(
                        onClick = {
                            focusManager.clearFocus()
                            onSendRequest()
                        },
                        enabled = uiState.isValidFormat && !uiState.isLoading && uiState.validationError == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Send Request",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─── Tab 2: Requests ────────────────────────────────────────────────────────

@Composable
private fun RequestsTab(
    incomingRequests: List<ConnectionRequestEntity>,
    outgoingRequests: List<ConnectionRequestEntity>,
    hazeState: HazeState,
    onAccept: (requestId: String) -> Unit,
    onDelete: (requestId: String) -> Unit,
    onCancel: (requestId: String) -> Unit,
    context: Context
) {
    val theme = MeshTalkTheme.colors
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PendingActions,
                    contentDescription = null,
                    tint = theme.textMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No pending requests",
                    color = theme.textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Incoming and outgoing requests will appear here",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Incoming section
        if (incomingRequests.isNotEmpty()) {
            item {
                Text(
                    text = "INCOMING REQUESTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            items(incomingRequests, key = { it.requestId }) { req ->
                IncomingRequestCard(
                    request = req,
                    hazeState = hazeState,
                    dateFormat = dateFormat,
                    onAccept = {
                        // Haptic feedback
                        triggerHaptic(context)
                        onAccept(req.requestId)
                    },
                    onDelete = { onDelete(req.requestId) }
                )
            }
        }

        // Outgoing section
        if (outgoingRequests.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "OUTGOING REQUESTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            items(outgoingRequests, key = { it.requestId }) { req ->
                OutgoingRequestCard(
                    request = req,
                    hazeState = hazeState,
                    dateFormat = dateFormat,
                    onCancel = { onCancel(req.requestId) }
                )
            }
        }
    }
}

@Composable
private fun IncomingRequestCard(
    request: ConnectionRequestEntity,
    hazeState: HazeState,
    dateFormat: SimpleDateFormat,
    onAccept: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = MeshTalkTheme.colors

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        hazeState = hazeState
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeshAvatar(
                    size = 48.dp,
                    identiconSeed = request.fromPublicKey.ifBlank { request.fromMeshId },
                    displayName = request.fromNickname
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.fromNickname.ifBlank { "Mesh Peer" },
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request.fromMeshId,
                        color = TealBlob,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = dateFormat.format(Date(request.timestamp)),
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color(0xFFFF5252), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                GradientButton(
                    onClick = onAccept,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Accept",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun OutgoingRequestCard(
    request: ConnectionRequestEntity,
    hazeState: HazeState,
    dateFormat: SimpleDateFormat,
    onCancel: () -> Unit
) {
    val theme = MeshTalkTheme.colors

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        hazeState = hazeState
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MeshAvatar(
                size = 44.dp,
                identiconSeed = request.toMeshId,
                displayName = request.toMeshId
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.toMeshId,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pending",
                        color = Color(0xFFFFB300),
                        fontSize = 12.sp
                    )
                    Text(
                        text = " • ${dateFormat.format(Date(request.timestamp))}",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFFFF5252), fontSize = 12.sp)
            }
        }
    }
}

// ─── Tab 3: Contacts List ───────────────────────────────────────────────────

@Composable
private fun ContactsTab(
    contacts: List<ContactEntity>,
    connectedPeers: Map<String, MeshPeer>,
    hazeState: HazeState
) {
    val theme = MeshTalkTheme.colors

    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = theme.textMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No contacts yet",
                    color = theme.textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Add contacts by their MeshTalk ID or accept incoming requests",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(contacts, key = { it.deviceId }) { contact ->
            val isOnline = connectedPeers.containsKey(contact.deviceId)

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                hazeState = hazeState
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        MeshAvatar(
                            size = 46.dp,
                            identiconSeed = contact.publicKeyBase64.ifBlank { contact.meshTalkId },
                            displayName = contact.nickname
                        )
                        OnlineStatusIndicator(
                            isOnline = isOnline,
                            size = 8.dp,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = contact.meshTalkId,
                            color = TealBlob,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (isOnline) {
                        Text(
                            "Online",
                            color = TealBlob,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─── Haptic Helper ──────────────────────────────────────────────────────────

private fun triggerHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    } catch (_: Exception) { }
}
