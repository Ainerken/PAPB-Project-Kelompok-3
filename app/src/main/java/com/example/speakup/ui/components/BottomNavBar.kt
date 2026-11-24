package com.example.speakup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.ui.theme.PrimaryBlue
import com.example.speakup.ui.theme.SurfaceWhite
import com.example.speakup.ui.theme.TextMuted

@Composable
fun BottomNavBar(
    selectedTab: String, // "home" or "history"
    onTabSelected: (String) -> Unit,
    onMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Base bar surface
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            color = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Tab
                val isHome = selectedTab == "home"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected("home") }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Home",
                        tint = if (isHome) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "Home",
                        fontSize = 11.sp,
                        fontWeight = if (isHome) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHome) PrimaryBlue else TextMuted
                    )
                }

                Spacer(modifier = Modifier.width(60.dp))

                // History Tab
                val isHistory = selectedTab == "history"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected("history") }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isHistory) Icons.Filled.Schedule else Icons.Outlined.Schedule,
                        contentDescription = "History",
                        tint = if (isHistory) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "History",
                        fontSize = 11.sp,
                        fontWeight = if (isHistory) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHistory) PrimaryBlue else TextMuted
                    )
                }
            }
        }

        // Center Floating Mic Button with Glow Shadow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-6).dp)
                .size(62.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = PrimaryBlue,
                    ambientColor = PrimaryBlue
                )
                .background(PrimaryBlue, shape = CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onMicClicked() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Record",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
