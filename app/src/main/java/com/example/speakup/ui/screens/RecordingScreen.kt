package com.example.speakup.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.service.AudioRecordingService
import com.example.speakup.service.RealtimeSpeechRecognizer
import com.example.speakup.ui.components.WaveformVisualizer
import com.example.speakup.ui.theme.*
import java.util.Locale

@Composable
fun RecordingScreen(
    language: String,
    practiceType: String,
    onCancel: () -> Unit,
    onRecordingFinished: (audioPath: String?, durationSeconds: Int, recognizedTranscript: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordingService = remember { AudioRecordingService(context) }
    val speechRecognizer = remember { RealtimeSpeechRecognizer(context) }

    val durationSeconds by recordingService.recordingDurationSeconds.collectAsState()
    val amplitude by recordingService.amplitudeFlow.collectAsState()
    val liveTranscript by speechRecognizer.liveTranscript.collectAsState()
    val isRecognizing by speechRecognizer.isRecognizing.collectAsState()
    val recognizerError by speechRecognizer.recognizerError.collectAsState()

    var showTooShortDialog by remember { mutableStateOf(false) }
    var isFinishedSuccessfully by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val started = recordingService.startRecording()
        if (!started) {
            Toast.makeText(context, "Tidak dapat mengakses mikrofon", Toast.LENGTH_SHORT).show()
            onCancel()
        } else {
            speechRecognizer.startListening(language)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isFinishedSuccessfully) {
                recordingService.cancelRecording()
                speechRecognizer.cancelListening()
            }
        }
    }

    if (showTooShortDialog) {
        AlertDialog(
            onDismissRequest = { showTooShortDialog = false },
            title = {
                Text("Rekaman Terlalu Pendek", fontWeight = FontWeight.Bold, color = PrimaryNavyText)
            },
            text = {
                Text(
                    "Harap berbicara minimal 5 detik agar algoritma dapat menganalisis kecepatan dan kata pengisi dengan akurat.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showTooShortDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Lanjutkan Bicara", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTooShortDialog = false
                        isFinishedSuccessfully = false
                        recordingService.cancelRecording()
                        speechRecognizer.cancelListening()
                        onCancel()
                    }
                ) {
                    Text("Batal", color = TextMuted)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Back & Language Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SurfaceWhite)
                        .clickable {
                            isFinishedSuccessfully = false
                            recordingService.cancelRecording()
                            speechRecognizer.cancelListening()
                            onCancel()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Cancel",
                        tint = PrimaryNavyText
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceWhite)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (language.startsWith("Indo", ignoreCase = true)) "🇮🇩 " else "🇬🇧 ",
                        fontSize = 16.sp
                    )
                    Text(
                        text = language,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavyText
                    )
                }
            }

            // Center Recording Information & Visualizer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Recording Voice... Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFEE2E2))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording Voice...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Digital Timer
                val minutes = durationSeconds / 60
                val seconds = durationSeconds % 60
                val timerText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                Text(
                    text = timerText,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = PrimaryNavyText,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Minimum 5s Requirement Badge
                val isMinimumReached = durationSeconds >= 5
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isMinimumReached) Color(0xFFD1FAE5) else Color(0xFFFEE2E2))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isMinimumReached) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMinimumReached) "Minimum 5s reached" else "Minimum 5s required",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMinimumReached) Color(0xFF065F46) else Color(0xFFB91C1C)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Dynamic Audio Waveform Visualizer
                WaveformVisualizer(
                    isRecording = true,
                    amplitude = amplitude,
                    barCount = 7,
                    maxHeight = 84.dp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Live Spoken Words Preview (Real Speech Recognition!)
                if (liveTranscript.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFEBF5FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Listening",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Terdengar: \"$liveTranscript\"",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryDarkNavy,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (isRecognizing) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE0F2FE))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "● Mikrofon siap, silakan mulai berbicara...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue
                        )
                    }
                } else if (recognizerError != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "● Merekam suara (audio akan diproses via AI)...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309)
                        )
                    }
                } else {
                    // Motivational Quote
                    Text(
                        text = "\"Stay relaxed. Articulate your ideas naturally as if speaking to a supportive colleague.\"",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // Bottom Stop Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = CircleShape,
                            spotColor = Color(0xFFE11D48),
                            ambientColor = Color(0xFFE11D48)
                        )
                        .clip(CircleShape)
                        .background(Color(0xFFE11D48))
                        .clickable {
                            if (durationSeconds < 5) {
                                showTooShortDialog = true
                            } else {
                                isFinishedSuccessfully = true
                                val recognizedText = speechRecognizer.stopListening()
                                val result = recordingService.stopRecording()
                                onRecordingFinished(result.filePath, durationSeconds, recognizedText)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Tap the red circle when finished",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }
    }
}
