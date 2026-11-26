package com.example.speakup.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.speakup.ui.theme.*

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onContinue()
    }

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Center Branding Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo Icon
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = PrimaryBlue)
                        .clip(RoundedCornerShape(26.dp))
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "SpeakUp Logo",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "YOUR POCKET VOICE MENTOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "SpeakUp",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Master your speaking pace, eliminate filler words, and build confidence with gentle AI coaching.",
                    fontSize = 15.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Permission Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceWhite,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEBF5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Security",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Microphone Access",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Required to record your speech sessions",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "100% Privacy Protected",
                            fontSize = 12.sp,
                            color = TextMuted
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEBF5FF))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Required",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }
                    }
                }
            }

            // Bottom CTA Button
            Button(
                onClick = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPerm) {
                        onContinue()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = PrimaryBlue),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    text = "Allow & Enter Dashboard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
