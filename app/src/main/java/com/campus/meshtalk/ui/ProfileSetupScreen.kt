package com.campus.meshtalk.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campus.meshtalk.ui.components.*
import com.campus.meshtalk.ui.theme.*
import com.campus.meshtalk.util.ImageUtils
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    initialNickname: String,
    initialStatus: String = "",
    initialAvatarUri: String? = null,
    identiconSeed: String? = null,
    isEditMode: Boolean = false,
    onComplete: (nickname: String, status: String, avatarUri: String?) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors

    var nickname by remember { mutableStateOf(initialNickname) }
    var status by remember { mutableStateOf(initialStatus) }
    var currentAvatarUri by remember { mutableStateOf(initialAvatarUri) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var showAvatarBottomSheet by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingImage = true
            coroutineScope.launch {
                val result = ImageUtils.processAndSaveAvatar(
                    context = context,
                    sourceUri = uri,
                    oldAvatarPath = currentAvatarUri
                )
                isProcessingImage = false
                result.onSuccess { savedPath ->
                    currentAvatarUri = savedPath
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        "Failed to load image: ${error.localizedMessage ?: "Unknown error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    MeshGlassScaffold(hazeState = hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            if (isEditMode) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    hazeState = hazeState
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onCancel != null) {
                            IconButton(onClick = onCancel) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                            }
                        }
                        Text(
                            "Edit Profile",
                            color = theme.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Set Up Your Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This is how others see you on the mesh network",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main profile card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                hazeState = hazeState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar with edit badge
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clickable { showAvatarBottomSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        MeshAvatar(
                            size = 110.dp,
                            avatarUri = currentAvatarUri,
                            identiconSeed = identiconSeed,
                            displayName = nickname
                        )

                        if (isProcessingImage) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = TealBlob,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(theme.primaryActionButtonGradient)
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change photo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showAvatarBottomSheet = true }) {
                        Text("Change Photo", color = TealBlob, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Nickname
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = {
                            if (it.length <= 30) {
                                nickname = it
                                if (it.isNotBlank()) nameError = null
                            }
                        },
                        label = { Text("Display Name (required)") },
                        placeholder = { Text("e.g. Rahul, Alex...") },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                nameError?.let { Text(it, color = Color(0xFFFF5252)) } ?: Spacer(Modifier.width(1.dp))
                                Text("${nickname.length}/30", color = theme.textMuted)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealBlob,
                            unfocusedBorderColor = theme.border,
                            focusedLabelColor = TealBlob,
                            unfocusedLabelColor = theme.textSecondary,
                            cursorColor = TealBlob,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status
                    OutlinedTextField(
                        value = status,
                        onValueChange = { if (it.length <= 80) status = it },
                        label = { Text("Status / Bio (optional)") },
                        placeholder = { Text("e.g. Available on campus mesh") },
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = "${status.length}/80",
                                color = theme.textMuted,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealBlob,
                            unfocusedBorderColor = theme.border,
                            focusedLabelColor = TealBlob,
                            unfocusedLabelColor = theme.textSecondary,
                            cursorColor = TealBlob,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            GradientButton(
                onClick = {
                    val trimmed = nickname.trim()
                    if (trimmed.isEmpty()) {
                        nameError = "Display name cannot be empty"
                        return@GradientButton
                    }
                    onComplete(trimmed, status.trim(), currentAvatarUri)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = nickname.isNotBlank() && !isProcessingImage
            ) {
                Text(
                    text = if (isEditMode) "Save Changes" else "Finish Setup",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Bottom sheet for avatar options
    if (showAvatarBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarBottomSheet = false },
            containerColor = if (theme.reduceTransparency) theme.surfaceSolidFallback else theme.background.copy(alpha = 0.95f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Profile Photo",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ListItem(
                    headlineContent = { Text("Choose from Gallery", color = theme.textPrimary) },
                    leadingContent = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TealBlob)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAvatarBottomSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Use Default Identicon", color = theme.textPrimary) },
                    supportingContent = { Text("Algorithmic avatar from your public key", color = theme.textSecondary, fontSize = 12.sp) },
                    leadingContent = {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = PurpleBlob)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAvatarBottomSheet = false
                            ImageUtils.deleteAvatarFile(currentAvatarUri)
                            currentAvatarUri = null
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (!currentAvatarUri.isNullOrBlank()) {
                    ListItem(
                        headlineContent = { Text("Remove Photo", color = Color(0xFFFF5252)) },
                        leadingContent = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showAvatarBottomSheet = false
                                ImageUtils.deleteAvatarFile(currentAvatarUri)
                                currentAvatarUri = null
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
