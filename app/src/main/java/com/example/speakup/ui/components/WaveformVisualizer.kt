package com.example.speakup.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.speakup.ui.theme.AccentCyan
import com.example.speakup.ui.theme.PrimaryDarkNavy

@Composable
fun WaveformVisualizer(
    isRecording: Boolean,
    amplitude: Int = 0,
    modifier: Modifier = Modifier,
    barCount: Int = 7,
    maxHeight: Dp = 80.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")

    val baseHeights = listOf(35.dp, 55.dp, 75.dp, 90.dp, 65.dp, 70.dp, 40.dp)
    val barColors = listOf(
        AccentCyan,
        PrimaryDarkNavy,
        AccentCyan,
        AccentCyan,
        AccentCyan,
        PrimaryDarkNavy,
        AccentCyan
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val color = barColors[i % barColors.size]
            val baseH = baseHeights[i % baseHeights.size]

            val animScale by if (isRecording) {
                infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 350 + (i * 70), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "barScale$i"
                )
            } else {
                rememberInfiniteTransition(label = "static$i").animateFloat(
                    initialValue = 0.8f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(tween(1000)),
                    label = "staticScale$i"
                )
            }

            // Factor in microphone amplitude if available
            val ampFactor = if (amplitude > 0) (amplitude / 32768f).coerceIn(0.1f, 1.2f) else 1.0f
            val currentHeight = baseH * animScale * ampFactor

            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(currentHeight.coerceAtLeast(14.dp))
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
    }
}
