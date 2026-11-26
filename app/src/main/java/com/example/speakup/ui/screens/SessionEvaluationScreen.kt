package com.example.speakup.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.repository.SessionRepository
import com.example.speakup.service.AudioPlaybackService
import com.example.speakup.ui.components.AudioPlayerBar
import com.example.speakup.ui.components.HighlightedTranscriptText
import com.example.speakup.ui.components.PacingGaugeBar
import com.example.speakup.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SessionEvaluationScreen(
    sessionId: Long,
    onClose: () -> Unit,
    onPracticeAgain: () -> Unit,
    onSaveFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val playbackService = remember { AudioPlaybackService(context) }
    val coroutineScope = rememberCoroutineScope()

    var session by remember { mutableStateOf<PracticeSession?>(null) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }
    var isFeedbackExpanded by remember { mutableStateOf(false) }

    val isPlaying by playbackService.isPlaying.collectAsState()
    val currentPositionMs by playbackService.currentPositionMs.collectAsState()
    val totalDurationMs by playbackService.totalDurationMs.collectAsState()

    LaunchedEffect(sessionId) {
        val s = sessionRepo.getSessionById(sessionId) ?: sessionRepo.getLatestSession()
        session = s
        if (s != null) {
            titleInput = s.title
            playbackService.loadAudio(s.audioPath, s.durationSeconds)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playbackService.release()
        }
    }

    if (isEditingTitle) {
        AlertDialog(
            onDismissRequest = { isEditingTitle = false },
            title = { Text("Edit Session Title", fontWeight = FontWeight.Bold, color = PrimaryNavyText) },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = titleInput.trim()
                        if (trimmed.isNotBlank()) {
                            coroutineScope.launch {
                                sessionRepo.updateSessionTitle(sessionId, trimmed)
                                session = session?.copy(title = trimmed)
                            }
                        }
                        isEditingTitle = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditingTitle = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    val currentSession = session

    Scaffold(
        containerColor = BackgroundLight,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceWhite,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Practice Again Button
                    Button(
                        onClick = onPracticeAgain,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = PrimaryBlue),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text(
                            text = "Practice Again",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Save Button
                    Surface(
                        modifier = Modifier
                            .weight(0.9f)
                            .height(52.dp)
                            .clickable {
                                Toast.makeText(context, "Session saved successfully!", Toast.LENGTH_SHORT).show()
                                onSaveFinished()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "Save",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (currentSession == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Bar: Close 'X' and Editable Title
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SurfaceWhite)
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = PrimaryNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isEditingTitle = true }
                        ) {
                            Text(
                                text = currentSession.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Title",
                                tint = PrimaryNavyText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Card 1: Overall Session Score
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = SurfaceWhite,
                        shadowElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Overall Session Score",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE0F2FE))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = currentSession.wpmCategory,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val badgeColor = when {
                                currentSession.overallScore >= 80 -> PrimaryDarkNavy
                                currentSession.overallScore >= 60 -> Color(0xFFD97706)
                                else -> Color(0xFFDC2626)
                            }
                            val labelColor = when {
                                currentSession.overallScore >= 80 -> PrimaryBlue
                                currentSession.overallScore >= 60 -> Color(0xFFD97706)
                                else -> Color(0xFFDC2626)
                            }
                            val headline = when {
                                currentSession.overallScore >= 90 -> "Outstanding Delivery!"
                                currentSession.overallScore >= 80 -> "Impressive Delivery!"
                                currentSession.overallScore >= 70 -> "Solid Practice Session!"
                                currentSession.overallScore >= 55 -> "Room for Improvement"
                                else -> "Needs Focused Practice"
                            }
                            val subtitle = when {
                                currentSession.fillerPercentage > 15f -> "Tingkat kata pengisi sangat tinggi (${currentSession.fillerPercentage}%). Ganti kata pengisi dengan jeda hening sejenak."
                                currentSession.fillerPercentage > 7f -> "Terdeteksi ${currentSession.fillerCount} kata pengisi (${currentSession.fillerPercentage}%). Kurangi pengulangan kata untuk kesan profesional."
                                currentSession.wpm < 110 -> "Tempo bicara Anda lambat (${currentSession.wpm} WPM). Tingkatkan ritme bicara dan keyakinan artikulasi."
                                currentSession.wpm > 165 -> "Tempo bicara Anda terlalu cepat (${currentSession.wpm} WPM). Berikan jeda nafas antar kalimat."
                                else -> "Tempo bicara dan artikulasi Anda seimbang pada ${currentSession.wpm} WPM."
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Score Badge
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(badgeColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${currentSession.overallScore}",
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentSession.scoreLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = labelColor
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = headline,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavyText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subtitle,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Card 2: Speaking Pace (WPM)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = SurfaceWhite,
                        shadowElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Speaking Pace (WPM)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavyText
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE0F2FE))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = currentSession.wpmCategory,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${currentSession.wpm} ",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryNavyText
                                )
                                Text(
                                    text = "Words Per Minute",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            PacingGaugeBar(wpm = currentSession.wpm)
                        }
                    }
                }

                // Card 3: Transcript Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = SurfaceWhite,
                        shadowElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Transcript",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavyText
                                )

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Transcript", currentSession.transcript)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Transcript copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = PrimaryNavyText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Audio Player Bar
                            AudioPlayerBar(
                                isPlaying = isPlaying,
                                currentPositionMs = currentPositionMs,
                                totalDurationMs = totalDurationMs,
                                onPlayPauseToggle = {
                                    if (isPlaying) playbackService.pause() else playbackService.play()
                                },
                                onSeek = { pos -> playbackService.seekTo(pos) }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Highlighted Transcript
                            HighlightedTranscriptText(
                                transcript = currentSession.transcript,
                                language = currentSession.language
                            )
                        }
                    }
                }

                // Card 4: AI Feedback Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = SurfaceWhite,
                        shadowElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Feedback",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Strikethrough original sentence
                            if (currentSession.feedbackOriginal.isNotBlank()) {
                                Text(
                                    text = currentSession.feedbackOriginal,
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    textDecoration = TextDecoration.LineThrough
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Corrected sentence in bold navy
                            Text(
                                text = currentSession.feedbackCorrection,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reason
                            Text(
                                text = "Reason: ${currentSession.feedbackReason}",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = TextSecondary
                            )

                            if (isFeedbackExpanded && currentSession.feedbackSummary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Coach Note: ${currentSession.feedbackSummary}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryBlue
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable { isFeedbackExpanded = !isFeedbackExpanded }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = if (isFeedbackExpanded) "Read Less" else "Read More",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
