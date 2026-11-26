package com.example.speakup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.ui.theme.*

@Composable
fun SessionSetupScreen(
    initialLanguage: String = "English",
    onBack: () -> Unit,
    onStartRecording: (language: String, practiceType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedLanguage by remember(initialLanguage) { mutableStateOf(initialLanguage) }
    var selectedPracticeType by remember { mutableStateOf("Open Presentation (No Limit)") }

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top App Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceWhite)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = PrimaryNavyText
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = strings.sessionSetupTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavyText
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.size(42.dp))
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 1. Select Practice Language
                Text(
                    text = strings.selectLanguageTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // English Card
                    val isEn = selectedLanguage.equals("English", ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedLanguage = "English" },
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceWhite,
                        border = if (isEn) androidx.compose.foundation.BorderStroke(2.dp, PrimaryBlue) else null,
                        shadowElevation = if (isEn) 6.dp else 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Text(text = "🇬🇧", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = strings.langEnglish,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.langEnglishSub,
                                fontSize = 12.sp,
                                color = MetricPaceColor
                            )
                        }
                    }

                    // Indonesian Card
                    val isId = selectedLanguage.equals("Indonesian", ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedLanguage = "Indonesian" },
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceWhite,
                        border = if (isId) androidx.compose.foundation.BorderStroke(2.dp, PrimaryBlue) else null,
                        shadowElevation = if (isId) 6.dp else 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Text(text = "🇮🇩", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = strings.langIndonesian,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.langIndonesianSub,
                                fontSize = 12.sp,
                                color = MetricPaceColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 2. Practice Type
                Text(
                    text = strings.selectPracticeTypeTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Option 1: Open Presentation
                val isOpen = selectedPracticeType.startsWith("Open")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPracticeType = "Open Presentation (No Limit)" },
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = if (isOpen) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else null,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = strings.openPresentation,
                        fontSize = 14.sp,
                        fontWeight = if (isOpen) FontWeight.Bold else FontWeight.Medium,
                        color = if (isOpen) PrimaryNavyText else TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Elevator Pitch
                val isPitch = selectedPracticeType.startsWith("Elevator")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPracticeType = "Elevator Pitch (1 Minute)" },
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = if (isPitch) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else null,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = strings.elevatorPitch,
                        fontSize = 14.sp,
                        fontWeight = if (isPitch) FontWeight.Bold else FontWeight.Medium,
                        color = if (isPitch) PrimaryNavyText else TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Helpful Speaking Tip Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFEF9EE),
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Lightbulb,
                                contentDescription = "Tip",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.helpfulTipTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = strings.helpfulTipDesc,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF92400E).copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Bottom Mic Button with "Tap when ready"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(16.dp, CircleShape, spotColor = PrimaryBlue)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .clickable { onStartRecording(selectedLanguage, selectedPracticeType) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = strings.startRecordingButton,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = strings.tapWhenReady,
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }
    }
}
