package com.example.speakup.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.ui.theme.AccentCyan
import com.example.speakup.ui.theme.PrimaryBlue
import com.example.speakup.ui.theme.PrimaryDarkNavy
import com.example.speakup.ui.theme.PrimaryNavyText

@Composable
fun CircularScoreGauge(
    score: Int,
    label: String = "GREAT",
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "scoreGaugeAnim"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthOuter = 16f
            val strokeWidthMiddle = 14f
            val strokeWidthInner = 12f

            val center = Offset(this.size.width / 2, this.size.height / 2)

            // Outer Arc (Accent Cyan)
            val outerRadius = (this.size.width / 2) - strokeWidthOuter
            drawArc(
                color = AccentCyan.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = strokeWidthOuter, cap = StrokeCap.Round)
            )
            drawArc(
                color = AccentCyan,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f * 0.92f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = strokeWidthOuter, cap = StrokeCap.Round)
            )

            // Middle Arc (Primary Blue)
            val middleRadius = outerRadius - 16f
            drawArc(
                color = PrimaryBlue.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - middleRadius, center.y - middleRadius),
                size = Size(middleRadius * 2, middleRadius * 2),
                style = Stroke(width = strokeWidthMiddle, cap = StrokeCap.Round)
            )
            drawArc(
                color = PrimaryBlue,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f * 0.88f,
                useCenter = false,
                topLeft = Offset(center.x - middleRadius, center.y - middleRadius),
                size = Size(middleRadius * 2, middleRadius * 2),
                style = Stroke(width = strokeWidthMiddle, cap = StrokeCap.Round)
            )

            // Inner Arc (Dark Navy)
            val innerRadius = middleRadius - 14f
            drawArc(
                color = PrimaryDarkNavy.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = strokeWidthInner, cap = StrokeCap.Round)
            )
            drawArc(
                color = PrimaryDarkNavy,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f * 0.82f,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = strokeWidthInner, cap = StrokeCap.Round)
            )
        }

        // Inner Text Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$score",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavyText
            )
            Spacer(modifier = Modifier.height(2.dp))
            val pillBg = when {
                score >= 80 -> Color(0xFFE0F2FE)
                score >= 60 -> Color(0xFFFEF3C7)
                else -> Color(0xFFFEE2E2)
            }
            val pillText = when {
                score >= 80 -> PrimaryBlue
                score >= 60 -> Color(0xFFD97706)
                else -> Color(0xFFDC2626)
            }

            Box(
                modifier = Modifier
                    .background(pillBg, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = pillText,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
