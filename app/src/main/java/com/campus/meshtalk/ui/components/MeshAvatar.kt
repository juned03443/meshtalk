package com.campus.meshtalk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.campus.meshtalk.ui.theme.ElectricTeal
import com.campus.meshtalk.ui.theme.MeshSurface
import com.campus.meshtalk.ui.theme.TextMuted
import java.io.File
import java.security.MessageDigest

@Composable
fun MeshAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    avatarUri: String? = null,
    identiconSeed: String? = null,
    displayName: String = "",
    placeholderIconTint: Color = TextMuted
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MeshSurface),
        contentAlignment = Alignment.Center
    ) {
        val hasCustomImage = !avatarUri.isNullOrBlank() && File(avatarUri).exists()

        if (hasCustomImage) {
            // Priority 1: Custom photo
            AsyncImage(
                model = File(avatarUri!!),
                contentDescription = displayName.ifBlank { "Avatar" },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (!identiconSeed.isNullOrBlank()) {
            // Priority 2: Identicon fallback generated deterministically from seed
            IdenticonAvatar(
                seed = identiconSeed,
                modifier = Modifier.fillMaxSize()
            )
        } else if (displayName.isNotBlank()) {
            // Display name initials fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElectricTeal.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.take(2).uppercase(),
                    color = ElectricTeal,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = (size.value * 0.4f).sp
                )
            }
        } else {
            // Priority 3: Default placeholder icon
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = placeholderIconTint,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

/**
 * 5x5 symmetric identicon rendered on Canvas using SHA-256 of the seed.
 */
@Composable
fun IdenticonAvatar(
    seed: String,
    modifier: Modifier = Modifier
) {
    val (primaryColor, bgColor, grid) = remember(seed) {
        generateIdenticonData(seed)
    }

    Canvas(modifier = modifier.background(bgColor)) {
        val cellSize = size.width / 5f
        for (row in 0 until 5) {
            for (col in 0 until 5) {
                if (grid[row][col]) {
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(col * cellSize, row * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}

private data class IdenticonData(
    val primaryColor: Color,
    val backgroundColor: Color,
    val grid: Array<BooleanArray>
)

private fun generateIdenticonData(seed: String): IdenticonData {
    val md = MessageDigest.getInstance("SHA-256")
    val hash = md.digest(seed.toByteArray(Charsets.UTF_8))

    // Derive hue from first 2 bytes
    val hue = ((hash[0].toInt() and 0xFF) * 256 + (hash[1].toInt() and 0xFF)) % 360
    val primaryColor = Color.hsl(hue.toFloat(), 0.7f, 0.55f)
    val bgColor = Color.hsl(hue.toFloat(), 0.25f, 0.15f)

    // 5x5 symmetric grid (columns 0, 1, 2 determine 3, 4)
    val grid = Array(5) { BooleanArray(5) }
    var byteIdx = 2
    for (row in 0 until 5) {
        for (col in 0..2) {
            val bit = (hash[byteIdx % hash.size].toInt() shr (col % 8)) and 1 == 1
            grid[row][col] = bit
            grid[row][4 - col] = bit // mirror horizontally
            byteIdx++
        }
    }

    return IdenticonData(primaryColor, bgColor, grid)
}
