package com.example.speakup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.ui.theme.*

@Composable
fun PacingGaugeBar(
    wpm: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Multi-segment horizontal bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // < 110 slow (Yellow)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(PaceSlow.copy(alpha = if (wpm < 110) 1.0f else 0.45f))
                )
                Spacer(modifier = Modifier.width(3.dp))
                // 110 - 150 ideal (Blue)
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .background(PaceIdeal.copy(alpha = if (wpm in 110..150) 1.0f else 0.45f))
                )
                Spacer(modifier = Modifier.width(3.dp))
                // > 150 fast (Pink)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(PaceFast.copy(alpha = if (wpm > 150) 1.0f else 0.45f))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Segment labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<110 slow",
                fontSize = 12.sp,
                color = if (wpm < 110) PaceSlow else TextMuted,
                fontWeight = if (wpm < 110) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Start
            )

            Text(
                text = "110–150 Ideal",
                fontSize = 12.sp,
                color = if (wpm in 110..150) PrimaryBlue else TextMuted,
                fontWeight = if (wpm in 110..150) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            Text(
                text = ">150 Fast",
                fontSize = 12.sp,
                color = if (wpm > 150) PaceFast else TextMuted,
                fontWeight = if (wpm > 150) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End
            )
        }
    }
}
