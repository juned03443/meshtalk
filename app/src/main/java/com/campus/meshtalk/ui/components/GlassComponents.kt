package com.campus.meshtalk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.campus.meshtalk.ui.theme.MeshTalkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Standard MeshTalk root background:
 * - Solid base color (#04060A in dark, #DFE9EC in light)
 * - Two glowing accent blobs (Teal in top-start, Purple in bottom-end) heavily blurred
 * - Optional HazeState integration for backdrop blur
 */
@Composable
fun MeshGlassScaffold(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = MeshTalkTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        // Glowing background blobs layer (zIndex lowest)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(-1f)
                .then(if (hazeState != null && !theme.reduceTransparency) Modifier.haze(hazeState) else Modifier)
        ) {
            // Teal blob (top-start corner ~180dp circle, heavy blur ~50dp)
            Box(
                modifier = Modifier
                    .offset(x = (-40).dp, y = (-40).dp)
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(theme.blobTealColor)
                    .blur(50.dp)
            )

            // Purple blob (bottom-end corner ~160dp-180dp circle, heavy blur ~50dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 30.dp)
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(theme.blobPurpleColor)
                    .blur(50.dp)
            )
        }

        // Main content layer
        content()
    }
}

/**
 * Frosted glass card with 24dp (or custom) rounded corners, 1dp subtle border,
 * semi-transparent background fill or solid fallback when transparency is reduced.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    hazeState: HazeState? = null,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MeshTalkTheme.colors.border,
    fillColor: Color = if (MeshTalkTheme.colors.reduceTransparency)
        MeshTalkTheme.colors.surfaceSolidFallback
    else
        MeshTalkTheme.colors.surfaceFill,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    val hazeChildModifier = if (hazeState != null && !MeshTalkTheme.colors.reduceTransparency) {
        Modifier.hazeChild(
            state = hazeState,
            shape = shape,
            blurRadius = MeshTalkTheme.colors.blurRadius
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .then(hazeChildModifier)
            .background(fillColor, shape)
            .border(borderWidth, borderColor, shape)
            .then(clickModifier)
    ) {
        content()
    }
}

/**
 * Chat bubble following MeshTalk visual specs:
 * Sent bubble: 135-deg Teal->Purple gradient, 18dp corner radius (asymmetric tip), 1dp border
 * Received bubble: translucent white (12% dark / 60% light), 18dp corner radius
 */
@Composable
fun GlassBubble(
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = MeshTalkTheme.colors

    // Asymmetric corners per visual spec: Sent: top-left 18, top-right 18, bottom-left 18, bottom-right 2
    // Received: top-left 18, top-right 18, bottom-right 18, bottom-left 2
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 2.dp, bottomEnd = 18.dp)
    }

    val hazeModifier = if (hazeState != null && !theme.reduceTransparency) {
        Modifier.hazeChild(
            state = hazeState,
            shape = bubbleShape,
            blurRadius = 10.dp
        )
    } else Modifier

    val bubbleModifier = if (isOutgoing) {
        modifier
            .clip(bubbleShape)
            .then(hazeModifier)
            .background(theme.sentBubbleGradient, bubbleShape)
            .border(1.dp, theme.sentBubbleBorder, bubbleShape)
    } else {
        val fill = if (theme.reduceTransparency) theme.surfaceSolidFallback else theme.receivedBubbleFill
        modifier
            .clip(bubbleShape)
            .then(hazeModifier)
            .background(fill, bubbleShape)
            .border(1.dp, theme.receivedBubbleBorder, bubbleShape)
    }

    Box(
        modifier = bubbleModifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        content()
    }
}

/**
 * Primary action button or send button with Teal->Purple gradient,
 * 16dp rounded or circle shape.
 */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val theme = MeshTalkTheme.colors
    val disabledAlpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .alpha(disabledAlpha)
            .clip(shape)
            .background(theme.primaryActionButtonGradient, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Circular send / action button
 */
@Composable
fun CircularGradientIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = MeshTalkTheme.colors
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(theme.primaryActionButtonGradient, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Online status dot:
 * Dark mode: Teal dot with soft glowing backdrop (8dp blur/glow)
 * Light mode: Solid dark-teal #0F6E56 (no glow)
 */
@Composable
fun OnlineStatusIndicator(
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    size: Dp = 8.dp
) {
    val theme = MeshTalkTheme.colors
    if (!isOnline) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(theme.textMuted)
        )
        return
    }

    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        if (theme.isDark && !theme.reduceTransparency) {
            Box(
                modifier = Modifier
                    .size(size * 1.8f)
                    .clip(CircleShape)
                    .background(theme.onlineGlow)
                    .blur(4.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(theme.onlineStatus)
        )
    }
}
