package com.campus.meshtalk.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campus.meshtalk.ui.components.GlassCard
import com.campus.meshtalk.ui.components.MeshGlassScaffold
import com.campus.meshtalk.ui.theme.MeshTalkTheme
import com.campus.meshtalk.ui.theme.TealBlob
import com.campus.meshtalk.ui.theme.PurpleBlob
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val fadeAnim = remember { Animatable(0f) }
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by pulseAnim.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseR"
    )
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseA"
    )

    val hazeState = remember { HazeState() }
    val theme = MeshTalkTheme.colors

    LaunchedEffect(Unit) {
        fadeAnim.animateTo(1f, animationSpec = tween(800))
        delay(1800)
        onFinished()
    }

    MeshGlassScaffold(hazeState = hazeState) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Pulse rings
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxR = size.minDimension / 2
                drawCircle(
                    color = TealBlob.copy(alpha = pulseAlpha),
                    radius = maxR * pulseRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            GlassCard(
                modifier = Modifier
                    .padding(32.dp)
                    .alpha(fadeAnim.value),
                shape = RoundedCornerShape(24.dp),
                hazeState = hazeState
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mesh icon inline
                    Canvas(modifier = Modifier.size(80.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val r = size.minDimension / 2 * 0.8f
                        drawCircle(color = TealBlob, radius = 10.dp.toPx(), center = center)
                        for (i in 0..2) {
                            val angle = Math.toRadians((i * 120 - 90).toDouble())
                            val pos = Offset(
                                (center.x + r * cos(angle)).toFloat(),
                                (center.y + r * sin(angle)).toFloat()
                            )
                            drawCircle(color = PurpleBlob, radius = 7.dp.toPx(), center = pos)
                            drawLine(TealBlob.copy(alpha = 0.7f), center, pos, strokeWidth = 2.dp.toPx())
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "MeshTalk",
                        style = MaterialTheme.typography.headlineLarge,
                        color = theme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Offline Mesh Messaging",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TealBlob,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
