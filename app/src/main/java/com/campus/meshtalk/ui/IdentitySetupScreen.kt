package com.campus.meshtalk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campus.meshtalk.ui.components.GlassCard
import com.campus.meshtalk.ui.components.GradientButton
import com.campus.meshtalk.ui.components.MeshAvatar
import com.campus.meshtalk.ui.components.MeshGlassScaffold
import com.campus.meshtalk.ui.theme.MeshTalkTheme
import com.campus.meshtalk.ui.theme.TealBlob
import com.campus.meshtalk.util.ImageUtils
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@Composable
fun IdentitySetupFlow(
    viewModel: SetupViewModel,
    onFinished: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnimatedContent(targetState = state.route, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "setupRoute") { route ->
        when (route) {
            SetupRoute.IDENTITY -> IdentityIntroScreen(state.meshTalkId, viewModel::continueToProfile)
            SetupRoute.PROFILE -> SetupProfileScreen(state, viewModel, onFinished)
        }
    }
}

@Composable
private fun IdentityIntroScreen(meshTalkId: String, onContinue: () -> Unit) {
    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors
    val context = LocalContext.current
    MeshGlassScaffold(hazeState = hazeState) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), hazeState) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(82.dp).clip(CircleShape).background(theme.primaryActionButtonGradient), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Hub, null, tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("MeshTalk", style = MaterialTheme.typography.headlineMedium, color = theme.textPrimary, fontWeight = FontWeight.Bold)
                    Text("Your offline identity", color = theme.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(28.dp))
                    Text("Your MeshTalk ID", color = theme.textSecondary, fontSize = 13.sp)
                    Text(meshTalkId.ifBlank { "Creating identity…" }, color = theme.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MeshTalk ID", meshTalkId))
                    }, enabled = meshTalkId.isNotBlank()) { Icon(Icons.Default.ContentCopy, "Copy MeshTalk ID", tint = TealBlob) }
                    Text("This is your permanent MeshTalk ID. Share it so others can add you.", color = theme.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            GradientButton(onContinue, Modifier.fillMaxWidth().height(52.dp), enabled = meshTalkId.isNotBlank()) { Text("Continue", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SetupProfileScreen(state: SetupUiState, viewModel: SetupViewModel, onFinished: () -> Unit) {
    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            processing = true
            scope.launch {
                ImageUtils.processAndSaveAvatar(context, uri, state.avatarPath).onSuccess(viewModel::updateAvatar).onFailure { imageError = "Could not use that image. Your identicon is still active." }
                processing = false
            }
        }
    }
    MeshGlassScaffold(hazeState = hazeState) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Set up your profile", style = MaterialTheme.typography.headlineSmall, color = theme.textPrimary, fontWeight = FontWeight.Bold)
            Text(state.meshTalkId, color = TealBlob, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))
            GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), hazeState) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(108.dp).clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                        MeshAvatar(size = 108.dp, avatarUri = state.avatarPath, identiconSeed = state.meshTalkId, displayName = state.displayName)
                        Box(Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(theme.primaryActionButtonGradient), contentAlignment = Alignment.Center) { Icon(Icons.Default.CameraAlt, "Choose avatar", tint = Color.White, modifier = Modifier.size(18.dp)) }
                        if (processing) CircularProgressIndicator(color = TealBlob)
                    }
                    Text("Tap avatar to choose a photo", color = theme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    imageError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(state.displayName, viewModel::updateName, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true, isError = state.nameError != null, supportingText = { Text(state.nameError ?: "${state.displayName.length}/30", color = if (state.nameError != null) MaterialTheme.colorScheme.error else theme.textMuted) }, shape = RoundedCornerShape(16.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next), colors = setupTextFieldColors())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(state.statusText, viewModel::updateStatus, Modifier.fillMaxWidth(), label = { Text("Status (optional)") }, singleLine = true, supportingText = { Text("${state.statusText.length}/80", color = theme.textMuted) }, shape = RoundedCornerShape(16.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done), colors = setupTextFieldColors())
                }
            }
            Spacer(Modifier.weight(1f))
            GradientButton({ viewModel.finish(onFinished) }, Modifier.fillMaxWidth().height(52.dp), enabled = !processing) { Text("Finish", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun setupTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TealBlob, unfocusedBorderColor = MeshTalkTheme.colors.border,
    focusedTextColor = MeshTalkTheme.colors.textPrimary, unfocusedTextColor = MeshTalkTheme.colors.textPrimary,
    focusedLabelColor = TealBlob, unfocusedLabelColor = MeshTalkTheme.colors.textSecondary, cursorColor = TealBlob
)
