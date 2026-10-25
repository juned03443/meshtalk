package com.campus.meshtalk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Specific visual token specifications from HTML & design instructions
val DarkBackground = Color(0xFF04060A)
val LightBackground = Color(0xFFDFE9EC)

val TealBlob = Color(0xFF2DD4BF)
val PurpleBlob = Color(0xFFA855F7)

val DarkPrimaryText = Color(0xFFFFFFFF)
val DarkSecondaryText = Color(0xFFFFFFFF).copy(alpha = 0.60f)

val LightPrimaryText = Color(0xFF0D2B3D)
val LightSecondaryText = Color(0xFF0D2B3D).copy(alpha = 0.45f)

val OnlineTealDark = Color(0xFF2DD4BF)
val OnlineTealLight = Color(0xFF0F6E56)

// Backward-compatibility legacy palette references
val MeshBackground = DarkBackground
val MeshSurface = Color(0xFF161B22)
val MeshSurfaceVariant = Color(0xFF21262D)
val ElectricTeal = Color(0xFF2DD4BF)
val EmeraldGreen = Color(0xFF00E676)
val WarningAmber = Color(0xFFFFB300)
val ErrorRed = Color(0xFFFF5252)
val MeshBorder = Color(0xFF30363D)
val TextPrimary = DarkPrimaryText
val TextSecondary = DarkSecondaryText
val TextMuted = Color(0xFF8B949E)
val BubbleOutgoing = Color(0xFF1F6FEB)
val BubbleIncoming = Color(0xFF21262D)

@Immutable
data class MeshTalkGlassColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceFill: Color,
    val surfaceSolidFallback: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onlineStatus: Color,
    val onlineGlow: Color,
    val receivedBubbleFill: Color,
    val receivedBubbleBorder: Color,
    val receivedBubbleText: Color,
    val sentBubbleBorder: Color,
    val sentBubbleText: Color,
    val sentBubbleGradient: Brush,
    val primaryActionButtonGradient: Brush,
    val blobTealColor: Color,
    val blobPurpleColor: Color,
    val blurRadius: Dp = 22.dp,
    val reduceTransparency: Boolean = false
)

val LocalMeshTalkColors = staticCompositionLocalOf<MeshTalkGlassColors> {
    error("No MeshTalkGlassColors provided")
}

val LocalReduceTransparency = compositionLocalOf { false }

fun meshTalkDarkGlassColors(reduceTransparency: Boolean = false): MeshTalkGlassColors {
    return MeshTalkGlassColors(
        isDark = true,
        background = DarkBackground,
        surfaceFill = Color.White.copy(alpha = 0.08f),
        surfaceSolidFallback = Color(0xFF10141D),
        border = Color.White.copy(alpha = 0.22f),
        textPrimary = DarkPrimaryText,
        textSecondary = DarkSecondaryText,
        textMuted = Color.White.copy(alpha = 0.45f),
        onlineStatus = OnlineTealDark,
        onlineGlow = OnlineTealDark.copy(alpha = 0.8f),
        receivedBubbleFill = Color.White.copy(alpha = 0.12f),
        receivedBubbleBorder = Color.White.copy(alpha = 0.20f),
        receivedBubbleText = DarkPrimaryText,
        sentBubbleBorder = TealBlob.copy(alpha = 0.40f),
        sentBubbleText = DarkPrimaryText,
        sentBubbleGradient = Brush.linearGradient(
            colors = listOf(
                TealBlob.copy(alpha = 0.50f),
                PurpleBlob.copy(alpha = 0.40f)
            )
        ),
        primaryActionButtonGradient = Brush.linearGradient(
            colors = listOf(
                TealBlob,
                PurpleBlob
            )
        ),
        blobTealColor = TealBlob.copy(alpha = 0.35f),
        blobPurpleColor = PurpleBlob.copy(alpha = 0.30f),
        blurRadius = 22.dp,
        reduceTransparency = reduceTransparency
    )
}

fun meshTalkLightGlassColors(reduceTransparency: Boolean = false): MeshTalkGlassColors {
    return MeshTalkGlassColors(
        isDark = false,
        background = LightBackground,
        surfaceFill = Color.White.copy(alpha = 0.50f),
        surfaceSolidFallback = Color(0xFFEFF5F7),
        border = Color.White.copy(alpha = 0.60f),
        textPrimary = LightPrimaryText,
        textSecondary = LightSecondaryText,
        textMuted = LightPrimaryText.copy(alpha = 0.35f),
        onlineStatus = OnlineTealLight,
        onlineGlow = Color.Transparent,
        receivedBubbleFill = Color.White.copy(alpha = 0.60f),
        receivedBubbleBorder = LightPrimaryText.copy(alpha = 0.08f),
        receivedBubbleText = LightPrimaryText,
        sentBubbleBorder = TealBlob.copy(alpha = 0.40f),
        sentBubbleText = LightPrimaryText,
        sentBubbleGradient = Brush.linearGradient(
            colors = listOf(
                TealBlob.copy(alpha = 0.38f),
                PurpleBlob.copy(alpha = 0.30f)
            )
        ),
        primaryActionButtonGradient = Brush.linearGradient(
            colors = listOf(
                TealBlob,
                PurpleBlob
            )
        ),
        blobTealColor = TealBlob.copy(alpha = 0.50f),
        blobPurpleColor = PurpleBlob.copy(alpha = 0.40f),
        blurRadius = 22.dp,
        reduceTransparency = reduceTransparency
    )
}

@Composable
fun MeshTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit
) {
    val glassColors = remember(darkTheme, reduceTransparency) {
        if (darkTheme) meshTalkDarkGlassColors(reduceTransparency)
        else meshTalkLightGlassColors(reduceTransparency)
    }

    val materialColors = remember(darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                background = DarkBackground,
                surface = Color(0xFF10141D),
                primary = TealBlob,
                secondary = PurpleBlob,
                onBackground = DarkPrimaryText,
                onSurface = DarkPrimaryText
            )
        } else {
            lightColorScheme(
                background = LightBackground,
                surface = Color(0xFFEFF5F7),
                primary = TealBlob,
                secondary = PurpleBlob,
                onBackground = LightPrimaryText,
                onSurface = LightPrimaryText
            )
        }
    }

    CompositionLocalProvider(
        LocalMeshTalkColors provides glassColors,
        LocalReduceTransparency provides reduceTransparency
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}

object MeshTalkTheme {
    val colors: MeshTalkGlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMeshTalkColors.current

    val reduceTransparency: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReduceTransparency.current
}
