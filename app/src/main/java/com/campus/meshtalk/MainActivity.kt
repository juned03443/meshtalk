package com.campus.meshtalk

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campus.meshtalk.data.preferences.ThemeMode
import com.campus.meshtalk.data.preferences.UserPreferencesRepository
import com.campus.meshtalk.ui.*
import com.campus.meshtalk.ui.theme.*
import com.campus.meshtalk.util.PermissionHelper
import kotlinx.coroutines.launch

enum class AppScreen {
    SPLASH,
    IDENTITY_SETUP,
    PERMISSIONS,
    PROFILE_SETUP,
    MAIN
}

enum class MainTab {
    CHATS,
    RADAR,
    DIAGNOSTICS,
    SETTINGS
}

class MainActivity : FragmentActivity() {

    private var permissionCallback: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            startMeshServices()
            permissionCallback?.invoke()
        } else {
            Toast.makeText(this, "Permissions required for mesh network", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MeshTalkApp
        val prefsRepo = UserPreferencesRepository(this)

        setContent {
            val themeMode by prefsRepo.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val reduceTransparency by prefsRepo.reduceTransparencyFlow.collectAsState(initial = false)
            val isProfileSetup by app.profileRepository.isProfileSetup.collectAsState(initial = false)

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            MeshTalkTheme(
                darkTheme = isDark,
                reduceTransparency = reduceTransparency
            ) {
                var currentScreen by remember {
                    mutableStateOf(AppScreen.SPLASH)
                }

                when (currentScreen) {
                    AppScreen.SPLASH -> {
                        SplashScreen(onFinished = {
                            currentScreen = if (!isProfileSetup) {
                                AppScreen.IDENTITY_SETUP
                            } else if (!PermissionHelper.hasAllPermissions(this@MainActivity)) {
                                AppScreen.PERMISSIONS
                            } else {
                                startMeshServices()
                                AppScreen.MAIN
                            }
                        })
                    }

                    AppScreen.IDENTITY_SETUP -> {
                        val setupViewModel: SetupViewModel = viewModel(
                            factory = SetupViewModel.Factory(this@MainActivity, app.keyPair)
                        )
                        IdentitySetupFlow(
                            viewModel = setupViewModel,
                            onFinished = {
                                val profile = setupViewModel.uiState.value
                                app.saveProfile(
                                    profile.displayName.trim(),
                                    profile.statusText.trim(),
                                    profile.avatarPath
                                )
                                currentScreen = if (!PermissionHelper.hasAllPermissions(this@MainActivity)) {
                                    AppScreen.PERMISSIONS
                                } else {
                                    startMeshServices()
                                    AppScreen.MAIN
                                }
                            }
                        )
                    }

                    AppScreen.PROFILE_SETUP -> {
                        ProfileSetupScreen(
                            initialNickname = app.nickname,
                            initialStatus = app.userStatus,
                            initialAvatarUri = app.avatarUri,
                            identiconSeed = app.meshRouter.myPublicKeyBase64,
                            isEditMode = false,
                            onComplete = { name, status, avatarUri ->
                                app.saveProfile(name, status, avatarUri)
                                currentScreen = if (!PermissionHelper.hasAllPermissions(this@MainActivity)) {
                                    AppScreen.PERMISSIONS
                                } else {
                                    startMeshServices()
                                    AppScreen.MAIN
                                }
                            }
                        )
                    }

                    AppScreen.PERMISSIONS -> {
                        if (PermissionHelper.hasAllPermissions(this@MainActivity)) {
                            startMeshServices()
                            currentScreen = AppScreen.MAIN
                        } else {
                            PermissionScreen(
                                onRequestPermissions = {
                                    permissionCallback = {
                                        currentScreen = AppScreen.MAIN
                                    }
                                    permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                                }
                            )
                        }
                    }

                    AppScreen.MAIN -> {
                        MainAppContent(
                            app = app,
                            prefsRepo = prefsRepo,
                            currentThemeMode = themeMode,
                            reduceTransparency = reduceTransparency
                        )
                    }
                }
            }
        }
    }

    private fun startMeshServices() {
        if (PermissionHelper.hasNearbyPermissions(this)) {
            com.campus.meshtalk.service.MeshService.start(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not call nearbyManager.stopAll() here. The mesh must keep running in background foreground service.
    }
}

@Composable
private fun MainAppContent(
    app: MeshTalkApp,
    prefsRepo: UserPreferencesRepository,
    currentThemeMode: ThemeMode,
    reduceTransparency: Boolean
) {
    val theme = MeshTalkTheme.colors
    var currentTab by remember { mutableStateOf(MainTab.CHATS) }
    var activePeerId by remember { mutableStateOf<String?>(null) }
    var activePeerName by remember { mutableStateOf("") }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showIdentityScreen by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val connectedPeers by app.nearbyManager.connectedPeers.collectAsState()
    val conversations by app.database.messageDao().getLatestMessagePerPeer().collectAsState(initial = emptyList())

    // ContactsViewModel
    val contactsViewModel: ContactsViewModel = viewModel(
        factory = ContactsViewModel.Factory(
            repository = app.contactRepository,
            myMeshId = app.meshTalkId,
            meshRouter = app.meshRouter,
            nearbyPeopleRepo = app.nearbyPeopleRepository
        )
    )
    val contactsUiState by contactsViewModel.uiState.collectAsState()
    val contacts by contactsViewModel.contacts.collectAsState()
    val incomingRequests by contactsViewModel.incomingRequests.collectAsState()
    val outgoingRequests by contactsViewModel.outgoingRequests.collectAsState()
    val incomingBadgeCount by contactsViewModel.incomingBadgeCount.collectAsState()

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        contactsViewModel.events.collect { event ->
            when (event) {
                is ContactsEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is ContactsEvent.RequestSent -> {}
                is ContactsEvent.RequestAccepted -> {}
                is ContactsEvent.AutoAccepted -> {}
            }
        }
    }

    // If AddContact screen is open, show it
    if (showAddContact) {
        val nearbyPeople by contactsViewModel.nearbyPeople.collectAsState()
        AddContactScreen(
            myMeshId = app.meshTalkId,
            uiState = contactsUiState,
            contacts = contacts,
            incomingRequests = incomingRequests,
            outgoingRequests = outgoingRequests,
            incomingBadgeCount = incomingBadgeCount,
            connectedPeers = connectedPeers,
            nearbyPeople = nearbyPeople,
            onMeshIdInputChanged = contactsViewModel::onMeshIdInputChanged,
            onSendRequest = contactsViewModel::sendRequest,
            onSendRequestToPerson = contactsViewModel::sendRequestToNearbyPerson,
            onAcceptRequest = contactsViewModel::acceptRequest,
            onDeleteRequest = contactsViewModel::deleteRequest,
            onCancelRequest = contactsViewModel::cancelRequest,
            onOpenChat = { peerId, peerName ->
                showAddContact = false
                activePeerId = peerId
                activePeerName = peerName
            },
            onRestartDiscovery = {
                app.nearbyManager.restartMesh()
            },
            onBack = { showAddContact = false }
        )
        return
    }

    if (showIdentityScreen) {
        com.campus.meshtalk.ui.IdentityScreen(
            meshId = app.meshTalkId,
            publicKey = app.meshRouter.myPublicKeyBase64,
            nickname = app.nickname,
            onBack = { showIdentityScreen = false }
        )
        return
    }

    // If we have an active chat open, show the chat screen
    if (activePeerId != null) {
        val chatViewModel: ChatViewModel = viewModel(
            key = activePeerId,
            factory = ChatViewModel.Factory(
                peerId = activePeerId!!,
                meshRouter = app.meshRouter,
                nearbyManager = app.nearbyManager,
                messageDao = app.database.messageDao(),
                contactDao = app.database.contactDao()
            )
        )
        val messages by chatViewModel.messages.collectAsState()

        // Wrap in glass scaffold
        com.campus.meshtalk.ui.components.MeshGlassScaffold {
            // Reuse the old ChatScreen logic inline with glass components
            val chatConnectedPeers by chatViewModel.connectedPeers.collectAsState()
            var inputText by remember { mutableStateOf("") }
            var selectedHopLimit by remember { mutableStateOf(4) }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()

            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            androidx.compose.material3.Scaffold(
                containerColor = theme.background,
                topBar = {
                    com.campus.meshtalk.ui.components.GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .statusBarsPadding(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { activePeerId = null }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    "Back",
                                    tint = theme.textPrimary
                                )
                            }
                            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activePeerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.textPrimary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    com.campus.meshtalk.ui.components.OnlineStatusIndicator(
                                        isOnline = chatConnectedPeers.isNotEmpty(),
                                        size = 7.dp
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (chatConnectedPeers.isNotEmpty()) "${chatConnectedPeers.size} Peers (Mesh Active)" else "Scanning...",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    com.campus.meshtalk.ui.components.GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .navigationBarsPadding(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Message...", color = theme.textMuted) },
                                modifier = Modifier.weight(1f),
                                maxLines = 4,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary,
                                    cursorColor = TealBlob
                                )
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
                            com.campus.meshtalk.ui.components.CircularGradientIconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        chatViewModel.sendMessage(inputText, selectedHopLimit)
                                        inputText = ""
                                    }
                                },
                                size = 44.dp,
                                enabled = inputText.isNotBlank()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = theme.textMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                            Text("No messages yet", color = theme.textMuted)
                            Text("Send the first mesh message!", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages, key = { it.messageId }) { msg ->
                            val isOutgoing = msg.isOutgoing
                            val alignment = if (isOutgoing) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start

                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = alignment
                            ) {
                                if (!isOutgoing) {
                                    Text(
                                        text = msg.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealBlob,
                                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                                    )
                                }
                                com.campus.meshtalk.ui.components.GlassBubble(
                                    isOutgoing = isOutgoing,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    androidx.compose.foundation.layout.Column {
                                        Text(
                                            text = msg.content,
                                            color = theme.textPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.foundation.layout.Row(
                                            modifier = Modifier.align(androidx.compose.ui.Alignment.End),
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp)),
                                                color = theme.textSecondary,
                                                fontSize = 10.sp
                                            )
                                            if (isOutgoing) {
                                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                                                StatusIcon(msg.status)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Profile edit overlay
    if (showProfileEdit) {
        ProfileSetupScreen(
            initialNickname = app.nickname,
            initialStatus = app.userStatus,
            initialAvatarUri = app.avatarUri,
            identiconSeed = app.meshRouter.myPublicKeyBase64,
            isEditMode = true,
            onComplete = { name, status, avatarUri ->
                app.saveProfile(name, status, avatarUri)
                showProfileEdit = false
            },
            onCancel = { showProfileEdit = false }
        )
        return
    }

    // Main tabbed view
    Scaffold(
        containerColor = theme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            com.campus.meshtalk.ui.components.GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = theme.textPrimary
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.CHATS,
                        onClick = { currentTab = MainTab.CHATS },
                        icon = { Icon(Icons.Default.Chat, "Chats") },
                        label = { Text("Chats") },
                        colors = glassNavItemColors()
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.RADAR,
                        onClick = { currentTab = MainTab.RADAR },
                        icon = { Icon(Icons.Default.Radar, "Radar") },
                        label = { Text("Nearby") },
                        colors = glassNavItemColors()
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.DIAGNOSTICS,
                        onClick = { currentTab = MainTab.DIAGNOSTICS },
                        icon = { Icon(Icons.Default.GraphicEq, "Topology") },
                        label = { Text("Topology") },
                        colors = glassNavItemColors()
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.SETTINGS,
                        onClick = { currentTab = MainTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings") },
                        colors = glassNavItemColors()
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                MainTab.CHATS -> {
                    // Use legacy ChatDashboardScreen with old signature since data layer unchanged
                    LegacyChatDashboard(
                        conversations = conversations,
                        connectedPeers = connectedPeers,
                        onSelectConversation = { peerId, peerName ->
                            activePeerId = peerId
                            activePeerName = peerName
                        },
                        onNewChat = { showAddContact = true },
                        onToggleTheme = {
                            scope.launch {
                                prefsRepo.setThemeMode(
                                    if (theme.isDark) ThemeMode.LIGHT else ThemeMode.DARK
                                )
                            }
                        }
                    )
                }
                MainTab.RADAR -> {
                    LegacyRadarScreen(
                        connectedPeers = connectedPeers,
                        onSelectPeer = { peerId, peerName ->
                            activePeerId = peerId
                            activePeerName = peerName
                        }
                    )
                }
                MainTab.DIAGNOSTICS -> {
                    DiagnosticsScreen(
                        nearbyManager = app.nearbyManager,
                        meshId = app.meshTalkId,
                        onBack = { currentTab = MainTab.CHATS }
                    )
                }
                MainTab.SETTINGS -> {
                    val scanMode by app.batteryPrefsRepo.scanModeFlow.collectAsState(initial = com.campus.meshtalk.data.preferences.BleScanMode.BALANCED)
                    val relayForOthers by app.batteryPrefsRepo.relayForOthersFlow.collectAsState(initial = true)
                    val relayOnlyWhileCharging by app.batteryPrefsRepo.relayOnlyWhileChargingFlow.collectAsState(initial = false)
                    val blockedUsers by app.database.blockedUserDao().getAllBlockedUsers().collectAsState(initial = emptyList())

                    SettingsScreen(
                        meshId = app.meshTalkId,
                        nickname = app.nickname,
                        avatarUri = app.avatarUri,
                        identiconSeed = app.meshRouter.myPublicKeyBase64,
                        currentThemeMode = currentThemeMode,
                        reduceTransparency = reduceTransparency,
                        currentScanMode = scanMode,
                        relayForOthers = relayForOthers,
                        relayOnlyWhileCharging = relayOnlyWhileCharging,
                        blockedUsers = blockedUsers,
                        onThemeModeChanged = { mode ->
                            scope.launch { prefsRepo.setThemeMode(mode) }
                        },
                        onReduceTransparencyChanged = { reduce ->
                            scope.launch { prefsRepo.setReduceTransparency(reduce) }
                        },
                        onScanModeChanged = { mode ->
                            scope.launch { app.batteryPrefsRepo.setScanMode(mode) }
                        },
                        onRelayForOthersChanged = { enabled ->
                            scope.launch { app.batteryPrefsRepo.setRelayForOthers(enabled) }
                        },
                        onRelayOnlyWhileChargingChanged = { enabled ->
                            scope.launch { app.batteryPrefsRepo.setRelayOnlyWhileCharging(enabled) }
                        },
                        onUnblockUser = { meshIdToUnblock ->
                            scope.launch { app.database.blockedUserDao().unblockUser(meshIdToUnblock) }
                        },
                        onEditProfile = { showProfileEdit = true },
                        onViewIdentity = { showIdentityScreen = true },
                        onDiagnostics = { currentTab = MainTab.DIAGNOSTICS },
                        onResetIdentity = {
                            scope.launch {
                                app.resetIdentityAndData()
                                currentTab = MainTab.CHATS
                            }
                        },
                        onBack = { currentTab = MainTab.CHATS }
                    )
                }
            }
        }
    }
}

@Composable
private fun glassNavItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = TealBlob,
    selectedTextColor = TealBlob,
    indicatorColor = TealBlob.copy(alpha = 0.15f),
    unselectedIconColor = MeshTalkTheme.colors.textMuted,
    unselectedTextColor = MeshTalkTheme.colors.textMuted
)

@Composable
private fun StatusIcon(status: com.campus.meshtalk.data.local.entity.MessageStatus) {
    val theme = MeshTalkTheme.colors
    when (status) {
        com.campus.meshtalk.data.local.entity.MessageStatus.PENDING -> Icon(Icons.Default.Schedule, "Pending", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
        com.campus.meshtalk.data.local.entity.MessageStatus.SENDING -> Icon(Icons.Default.Check, "Sending", tint = theme.textSecondary, modifier = Modifier.size(12.dp))
        com.campus.meshtalk.data.local.entity.MessageStatus.RELAYED -> Icon(Icons.Default.Sync, "Relayed", tint = TealBlob, modifier = Modifier.size(12.dp))
        com.campus.meshtalk.data.local.entity.MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, "Delivered", tint = TealBlob, modifier = Modifier.size(14.dp))
        com.campus.meshtalk.data.local.entity.MessageStatus.FAILED -> Icon(Icons.Default.Error, "Failed", tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
    }
}

/**
 * Legacy wrapper: renders ChatDashboardScreen using the existing data model
 * with glassmorphism visuals.
 */
@Composable
private fun LegacyChatDashboard(
    conversations: List<com.campus.meshtalk.data.local.entity.MessageEntity>,
    connectedPeers: Map<String, com.campus.meshtalk.mesh.MeshPeer>,
    onSelectConversation: (peerId: String, peerName: String) -> Unit,
    onNewChat: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val theme = MeshTalkTheme.colors
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = if (searchQuery.isBlank()) conversations
    else conversations.filter {
        it.senderName.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
    }

    com.campus.meshtalk.ui.components.MeshGlassScaffold(hazeState = hazeState) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Glass top bar
            com.campus.meshtalk.ui.components.GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            text = "MeshTalk",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = theme.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (theme.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (theme.isDark) "Switch to light theme" else "Switch to dark theme",
                                tint = theme.textPrimary
                            )
                        }
                        com.campus.meshtalk.ui.components.OnlineStatusIndicator(
                            isOnline = connectedPeers.isNotEmpty(),
                            size = 8.dp
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (connectedPeers.isNotEmpty()) "${connectedPeers.size} peers" else "Scanning...",
                            color = if (connectedPeers.isNotEmpty()) TealBlob else theme.textMuted,
                            fontSize = 12.sp
                        )
                    }

                    // Search bar
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search chats...", color = theme.textMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.border,
                            unfocusedBorderColor = theme.border,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = TealBlob
                        )
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Forum, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(64.dp))
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        Text("No conversations yet", color = theme.textPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("Go to Radar to find nearby peers", color = theme.textSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.peerId }) { msg ->
                        val isBroadcast = msg.peerId == com.campus.meshtalk.data.model.MeshPacket.DESTINATION_BROADCAST
                        val displayName = if (isBroadcast) "Campus Broadcast"
                        else if (msg.isOutgoing) "Peer: ${msg.peerId.take(8)}"
                        else msg.senderName
                        val isOnline = connectedPeers.containsKey(msg.peerId)

                        com.campus.meshtalk.ui.components.GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            hazeState = hazeState,
                            onClick = {
                                onSelectConversation(msg.peerId, displayName)
                            }
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Box {
                                    com.campus.meshtalk.ui.components.MeshAvatar(
                                        size = 48.dp,
                                        identiconSeed = msg.peerId,
                                        displayName = displayName
                                    )
                                    if (!isBroadcast) {
                                        com.campus.meshtalk.ui.components.OnlineStatusIndicator(
                                            isOnline = isOnline,
                                            size = 8.dp,
                                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                                        )
                                    }
                                }

                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(14.dp))

                                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        color = theme.textPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (msg.isOutgoing) "You: ${msg.content}" else msg.content,
                                        color = theme.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB
        com.campus.meshtalk.ui.components.CircularGradientIconButton(
            onClick = onNewChat,
            size = 56.dp,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(20.dp)
                .navigationBarsPadding()
        ) {
            Icon(Icons.Default.PersonAdd, "Add Contact", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Legacy wrapper: renders RadarScreen with glassmorphism, matching old signature.
 */
@Composable
private fun LegacyRadarScreen(
    connectedPeers: Map<String, com.campus.meshtalk.mesh.MeshPeer>,
    onSelectPeer: (peerId: String, peerName: String) -> Unit
) {
    val theme = MeshTalkTheme.colors
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

    val sweepAnim = rememberInfiniteTransition(label = "sweep")
    val sweepAngle by sweepAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Sweep"
    )

    com.campus.meshtalk.ui.components.MeshGlassScaffold(hazeState = hazeState) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            com.campus.meshtalk.ui.components.GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                hazeState = hazeState
            ) {
                Text(
                    text = "Nearby & Discovery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Radar Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    val center =
                        androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                    val maxRadius = (size.minDimension / 2) * 0.9f

                    drawCircle(
                        color = TealBlob.copy(alpha = 0.15f),
                        radius = maxRadius * 0.33f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = TealBlob.copy(alpha = 0.25f),
                        radius = maxRadius * 0.66f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = TealBlob.copy(alpha = 0.4f),
                        radius = maxRadius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(color = TealBlob, radius = 7.dp.toPx(), center = center)

                    val rad = Math.toRadians(sweepAngle.toDouble())
                    val sweepEnd = androidx.compose.ui.geometry.Offset(
                        (center.x + maxRadius * kotlin.math.cos(rad)).toFloat(),
                        (center.y + maxRadius * kotlin.math.sin(rad)).toFloat()
                    )
                    drawLine(
                        color = TealBlob.copy(alpha = 0.5f),
                        start = center,
                        end = sweepEnd,
                        strokeWidth = 2.dp.toPx()
                    )

                    val peerList = connectedPeers.values.toList()
                    peerList.forEachIndexed { index, _ ->
                        val angle = (index * (360.0 / peerList.size.coerceAtLeast(1))).toDouble()
                        val dist = maxRadius * 0.6f
                        val blipPos = androidx.compose.ui.geometry.Offset(
                            (center.x + dist * kotlin.math.cos(Math.toRadians(angle))).toFloat(),
                            (center.y + dist * kotlin.math.sin(Math.toRadians(angle))).toFloat()
                        )
                        drawCircle(color = PurpleBlob, radius = 6.dp.toPx(), center = blipPos)
                        drawCircle(
                            color = PurpleBlob.copy(alpha = 0.3f),
                            radius = 12.dp.toPx(),
                            center = blipPos
                        )
                    }
                }

                Text(
                    text = if (connectedPeers.isNotEmpty()) "${connectedPeers.size} Peers in Range" else "Searching...",
                    color = if (connectedPeers.isNotEmpty()) TealBlob else theme.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            // Peer list
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                // Broadcast channel
                item {
                    com.campus.meshtalk.ui.components.GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        hazeState = hazeState,
                        onClick = {
                            onSelectPeer(
                                com.campus.meshtalk.data.model.MeshPacket.DESTINATION_BROADCAST,
                                "Campus Broadcast"
                            )
                        }
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VolumeUp, null, tint = TealBlob)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Campus Broadcast",
                                    color = theme.textPrimary,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Mesh flooding to all nodes",
                                    color = theme.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted)
                        }
                    }
                }

                if (connectedPeers.isEmpty()) {
                    item {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.WifiFind,
                                null,
                                tint = theme.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                            Text("No peers found yet", color = theme.textMuted, fontSize = 14.sp)
                            Text(
                                "Keep Bluetooth & Wi-Fi active",
                                color = theme.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(connectedPeers.values.toList()) { peer ->
                        com.campus.meshtalk.ui.components.GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            hazeState = hazeState,
                            onClick = { onSelectPeer(peer.meshTalkId, peer.deviceName) }
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                com.campus.meshtalk.ui.components.MeshAvatar(
                                    size = 40.dp,
                                    identiconSeed = peer.meshTalkId,
                                    displayName = peer.deviceName
                                )
                                androidx.compose.foundation.layout.Spacer(
                                    modifier = Modifier.width(
                                        12.dp
                                    )
                                )
                                androidx.compose.foundation.layout.Column(
                                    modifier = Modifier.weight(
                                        1f
                                    )
                                ) {
                                    Text(
                                        peer.deviceName,
                                        color = theme.textPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        com.campus.meshtalk.ui.components.OnlineStatusIndicator(
                                            isOnline = true,
                                            size = 6.dp
                                        )
                                        androidx.compose.foundation.layout.Spacer(
                                            modifier = Modifier.width(
                                                4.dp
                                            )
                                        )
                                        Text("Direct Range", color = TealBlob, fontSize = 11.sp)
                                        Text(
                                            " • ${peer.endpointId}",
                                            color = theme.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}


