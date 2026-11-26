package com.example.speakup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.ui.theme.AccentCyan
import com.example.speakup.ui.theme.PrimaryBlue
import com.example.speakup.ui.theme.PrimaryDarkNavy
import com.example.speakup.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AudioPlayerBar(
    isPlaying: Boolean,
    currentPositionMs: Int,
    totalDurationMs: Int,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = if (totalDurationMs > 0) totalDurationMs else 22000
    val progress = (currentPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF0F7FF))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play / Pause Icon Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PrimaryDarkNavy)
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Slider / Progress Track
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        onSeek((newProgress * totalMs).toInt())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = Color(0xFFD6E4F0)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                )
            }

            // Timestamps
            val currentSec = currentPositionMs / 1000
            val totalSec = totalMs / 1000
            val currentStr = String.format(Locale.US, "%02d:%02d", currentSec / 60, currentSec % 60)
            val totalStr = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)

            Text(
                text = "$currentStr / $totalStr",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryBlue,
                modifier = Modifier.padding(start = 50.dp)
            )
        }
    }
}
