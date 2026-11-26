package com.example.speakup.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.repository.SessionRepository
import com.example.speakup.data.repository.UserRepository
import com.example.speakup.service.AiFeedbackEngine
import com.example.speakup.service.FillerWordAnalyzer
import com.example.speakup.service.PacingCalculator
import com.example.speakup.service.SpeechToTextEngine
import com.example.speakup.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProcessingScreen(
    audioPath: String?,
    durationSeconds: Int,
    language: String,
    practiceType: String,
    recognizedTranscript: String = "",
    onComplete: (sessionId: Long) -> Unit,
    onErrorRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userRepo = remember { UserRepository(context) }
    val sessionRepo = remember { SessionRepository(context) }
    val sttEngine = remember { SpeechToTextEngine(context) }
    val aiEngine = remember { AiFeedbackEngine { userRepo.getGeminiApiKey() } }

    var currentStep by remember { mutableIntStateOf(1) }
    var isDone by remember { mutableStateOf(false) }
    var createdSessionId by remember { mutableLongStateOf(0L) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showNoSpeechDialog by remember { mutableStateOf(false) }
    var showGeminiErrorDialog by remember { mutableStateOf(false) }
    var showSilentAudioDialog by remember { mutableStateOf(false) }
    var showApiKeyInputDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(userRepo.getGeminiApiKey()) }
    var sttErrorCode by remember { mutableStateOf<String?>(null) }
    var sttErrorMessage by remember { mutableStateOf<String?>(null) }
    var allowSimulationFallback by remember { mutableStateOf(false) }
    var processingTrigger by remember { mutableIntStateOf(0) }

    var sttSubtitle by remember { mutableStateOf("Processing audio...") }
    var fillerSubtitle by remember { mutableStateOf("Calculating filler words...") }
    var pacingSubtitle by remember { mutableStateOf("Analyzing pacing...") }
    var grammarSubtitle by remember { mutableStateOf("Evaluating grammar...") }
    var feedbackSubtitle by remember { mutableStateOf("Preparing feedback...") }

    // Execute processing flow
    LaunchedEffect(processingTrigger) {
        try {
            // Step 1: STT
            delay(800)
            val audioFile = if (!audioPath.isNullOrEmpty()) File(audioPath) else null
            val sttResult = sttEngine.transcribeAudio(
                audioFile = audioFile,
                language = language,
                durationSeconds = durationSeconds,
                providedTranscript = recognizedTranscript,
                apiKey = userRepo.getGeminiApiKey(),
                allowSimulationFallback = allowSimulationFallback
            )
            if (!sttResult.isSuccess) {
                sttErrorCode = sttResult.errorCode
                sttErrorMessage = sttResult.errorMessage
                when (sttResult.errorCode) {
                    "GEMINI_ERROR" -> showGeminiErrorDialog = true
                    "AUDIO_SILENT", "AUDIO_EMPTY" -> showSilentAudioDialog = true
                    "NO_SPEECH_NO_API_KEY" -> showNoSpeechDialog = true
                    else -> showErrorDialog = true
                }
                return@LaunchedEffect
            }
            val transcript = sttResult.transcript
            val wordCount = sttResult.wordCount
            sttSubtitle = "$wordCount words recognized"
            currentStep = 2

            // Step 2: Filler Words Analysis
            delay(700)
            val fillerAnalysis = FillerWordAnalyzer.analyze(transcript, language)
            val fillerCount = fillerAnalysis.fillerCount
            fillerSubtitle = "$fillerCount Filler Words Detected"
            currentStep = 3

            // Step 3: Pacing Calculation
            delay(600)
            val pacingResult = PacingCalculator.calculate(wordCount, durationSeconds)
            val wpm = pacingResult.wpm
            pacingSubtitle = "$wpm WPM Calculated"
            currentStep = 4

            // Step 4: Grammar Evaluation
            delay(800)
            val aiResult = aiEngine.evaluateGrammarAndStructure(transcript, language)
            grammarSubtitle = "Grammar Checked"
            currentStep = 5

            // Step 5: Final Feedback Compilation & Persistence
            delay(600)
            feedbackSubtitle = "Feedback ready"

            // Compute balanced, realistic overall score:
            // 35% Filler Word Fluency (fluency & control)
            // 40% Grammar, Vocabulary & Substance (clarity & coherence)
            // 25% Speech Pacing & Tempo (WPM)
            val fillerScore = fillerAnalysis.fillerScore
            val grammarScore = aiResult.grammarScore
            val pacingScore = pacingResult.pacingScore

            val rawOverall = (fillerScore * 0.35f) + (grammarScore * 0.40f) + (pacingScore * 0.25f)
            val overallScore = kotlin.math.round(rawOverall).toInt().coerceIn(15, 98)

            val scoreLabel = when {
                overallScore >= 90 -> "EXCELLENT"
                overallScore >= 80 -> "GREAT"
                overallScore >= 70 -> "GOOD"
                overallScore >= 55 -> "FAIR"
                overallScore >= 40 -> "NEEDS IMPROVEMENT"
                else -> "POOR"
            }

            // Adjust delivery status based on comprehensive metrics
            val deliveryStatus = when {
                fillerAnalysis.fillerPercentage > 15f -> "High Filler Pauses"
                fillerAnalysis.fillerPercentage > 8f -> "Hesitant & Fillers"
                pacingScore < 65 -> pacingResult.deliveryStatus
                overallScore >= 88 -> "Steady & Composed"
                else -> pacingResult.deliveryStatus
            }

            val dateFormat = SimpleDateFormat("MMM d", Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val now = Date()

            val defaultTitle = if (practiceType.startsWith("Elevator")) {
                "Brief Interview Introduction"
            } else {
                if (language.startsWith("Indo", ignoreCase = true)) "Latihan Presentasi Singkat" else "Speech Practice Session"
            }

            val newSession = PracticeSession(
                title = defaultTitle,
                timestamp = System.currentTimeMillis(),
                formattedDate = dateFormat.format(now),
                formattedTime = timeFormat.format(now),
                durationSeconds = durationSeconds,
                language = language,
                practiceType = practiceType,
                audioPath = audioPath,
                transcript = transcript,
                wordCount = wordCount,
                wpm = wpm,
                wpmCategory = pacingResult.category,
                fillerCount = fillerCount,
                fillerPercentage = fillerAnalysis.fillerPercentage,
                fillerStatus = fillerAnalysis.status,
                fillerDetailsJson = fillerAnalysis.fillerDetailsJson,
                overallScore = overallScore,
                grammarScore = aiResult.grammarScore,
                scoreLabel = scoreLabel,
                deliveryStatus = deliveryStatus,
                feedbackOriginal = aiResult.originalSentence,
                feedbackCorrection = aiResult.correctedSentence,
                feedbackReason = aiResult.reason,
                feedbackSummary = aiResult.summaryFeedback
            )

            val newId = sessionRepo.insertSession(newSession)
            createdSessionId = newId
            currentStep = 6
            isDone = true

        } catch (e: Exception) {
            e.printStackTrace()
            showErrorDialog = true
        }
    }

    if (showApiKeyInputDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyInputDialog = false },
            title = { Text("Masukkan Gemini API Key", fontWeight = FontWeight.Bold, color = PrimaryNavyText) },
            text = {
                Column {
                    Text(
                        "Masukkan API Key gratis dari Google AI Studio (aistudio.google.com) agar rekaman audio Anda ditranskripsi otomatis oleh model Gemini AI:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = apiKeyInput.trim()
                        if (trimmed.isNotBlank()) {
                            userRepo.setGeminiApiKey(trimmed)
                            showApiKeyInputDialog = false
                            showNoSpeechDialog = false
                            processingTrigger++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Transkripsi dengan AI", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyInputDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    if (showGeminiErrorDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text("Kendala Gemini AI Transcription", fontWeight = FontWeight.Bold, color = PrimaryNavyText)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Gagal mentranskripsi file suara lewat Gemini AI:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sttErrorMessage ?: "API Key tidak valid atau batas kuota tercapai.",
                            fontSize = 12.sp,
                            color = Color(0xFFB91C1C),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        "Pastikan API Key Anda aktif di Google AI Studio (aistudio.google.com). Anda dapat mengubah API Key sekarang atau menggunakan simulasi untuk melanjutkan evaluasi.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            showGeminiErrorDialog = false
                            showApiKeyInputDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Perbaiki API Key", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            showGeminiErrorDialog = false
                            processingTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Coba Lagi", color = PrimaryNavyText)
                    }

                    TextButton(
                        onClick = {
                            showGeminiErrorDialog = false
                            allowSimulationFallback = true
                            processingTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Gunakan Contoh Simulasi", color = PrimaryBlue)
                    }

                    TextButton(
                        onClick = {
                            showGeminiErrorDialog = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Batal", color = TextMuted)
                    }
                }
            },
            dismissButton = null,
            containerColor = SurfaceWhite
        )
    }

    if (showSilentAudioDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text("Audio Tidak Berisi Suara", fontWeight = FontWeight.Bold, color = PrimaryNavyText)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "File rekaman suara berhasil dibuat, namun tidak ada gelombang ucapan manusia yang tertangkap (audio hening / 0 dB).",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "💡 Tip Emulator Android Studio:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                            Text(
                                "Buka menu titik tiga (...) pada toolbar emulator > tab 'Microphone' > aktifkan 'Virtual microphone uses host audio input' dan pastikan mic laptop/PC Anda tidak di-mute.",
                                fontSize = 11.sp,
                                color = PrimaryDarkNavy
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            showSilentAudioDialog = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Rekam Ulang", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            showSilentAudioDialog = false
                            allowSimulationFallback = true
                            processingTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Gunakan Contoh Simulasi", color = PrimaryNavyText)
                    }

                    TextButton(
                        onClick = {
                            showSilentAudioDialog = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Batal", color = TextMuted)
                    }
                }
            },
            dismissButton = null,
            containerColor = SurfaceWhite
        )
    }

    if (showNoSpeechDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text("Tidak Ada Ucapan Terdeteksi", fontWeight = FontWeight.Bold, color = PrimaryNavyText)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mikrofon belum menangkap suara yang jelas, dan pengenal suara perangkat (SpeechRecognizer) belum mendeteksi kata-kata.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        "Pilihan yang dapat Anda lakukan:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavyText
                    )
                    Text("1. Masukkan Gemini API Key agar audio rekaman ditranskripsi otomatis lewat cloud AI.", fontSize = 12.sp, color = PrimaryBlue)
                    Text("2. Coba rekam ulang dengan berbicara lebih dekat ke mikrofon.", fontSize = 12.sp, color = TextSecondary)
                    Text("3. Gunakan simulasi contoh jika Anda ingin melihat alur evaluasi.", fontSize = 12.sp, color = TextMuted)
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            showApiKeyInputDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Gunakan Gemini AI Audio", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            showNoSpeechDialog = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rekam Ulang", color = PrimaryNavyText)
                    }

                    TextButton(
                        onClick = {
                            showNoSpeechDialog = false
                            allowSimulationFallback = true
                            processingTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Gunakan Contoh Simulasi", color = TextMuted)
                    }
                }
            },
            dismissButton = null,
            containerColor = SurfaceWhite
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text("Koneksi/API bermasalah", fontWeight = FontWeight.Bold, color = PrimaryNavyText)
            },
            text = {
                Text(
                    "Terjadi kendala saat memproses rekaman atau memanggil API. Anda dapat mencoba kembali atau membatalkan proses.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showErrorDialog = false
                        onErrorRetry()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Coba Lagi", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showErrorDialog = false
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mic Icon inside round box
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFEBF5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Processing",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = if (isDone) "Your Evaluation is Ready" else "Analyzing Your Speech...",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )

                if (isDone) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All step is done",
                        fontSize = 15.sp,
                        color = TextSecondary
                    )
                }
            }

            // Steps Checklist Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Speech-to-Text
                ProcessingStepItem(
                    stepNumber = "1",
                    title = "Speech-to-Text (STT)",
                    subtitle = sttSubtitle,
                    isCompleted = currentStep > 1
                )

                // 2. Filler Word Analysis
                AnimatedVisibility(
                    visible = currentStep >= 2,
                    enter = fadeIn(tween(400)) + slideInVertically()
                ) {
                    ProcessingStepItem(
                        stepNumber = "2",
                        title = "Filler Word Analysis",
                        subtitle = fillerSubtitle,
                        isCompleted = currentStep > 2
                    )
                }

                // 3. AI Pacing
                AnimatedVisibility(
                    visible = currentStep >= 3,
                    enter = fadeIn(tween(400)) + slideInVertically()
                ) {
                    ProcessingStepItem(
                        stepNumber = "3",
                        title = "AI Pacing",
                        subtitle = pacingSubtitle,
                        isCompleted = currentStep > 3
                    )
                }

                // 4. Grammar Evaluation
                AnimatedVisibility(
                    visible = currentStep >= 4,
                    enter = fadeIn(tween(400)) + slideInVertically()
                ) {
                    ProcessingStepItem(
                        stepNumber = "4",
                        title = "Grammar Evaluation",
                        subtitle = grammarSubtitle,
                        isCompleted = currentStep > 4
                    )
                }

                // 5. Feedback
                AnimatedVisibility(
                    visible = currentStep >= 5,
                    enter = fadeIn(tween(400)) + slideInVertically()
                ) {
                    ProcessingStepItem(
                        stepNumber = "5",
                        title = "Feedback",
                        subtitle = feedbackSubtitle,
                        isCompleted = currentStep > 5
                    )
                }
            }

            // Bottom Button (Active when done)
            if (isDone) {
                Button(
                    onClick = { onComplete(createdSessionId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = PrimaryBlue),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(
                        text = "Go to Session Evaluation",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(54.dp))
            }
        }
    }
}

@Composable
fun ProcessingStepItem(
    stepNumber: String,
    title: String,
    subtitle: String,
    isCompleted: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF0F7FF),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) PrimaryBlue else Color(0xFFD6E4F0)),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "$stepNumber. $title",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MetricPaceColor
                )
            }
        }
    }
}
