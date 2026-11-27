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
    userMainLanguage: String = "English",
    recognizedTranscript: String = "",
    onComplete: (sessionId: Long) -> Unit,
    onErrorRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
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

    var sttSubtitle by remember { mutableStateOf(if (strings.isIndonesian) "Memproses audio..." else "Processing audio...") }
    var fillerSubtitle by remember { mutableStateOf(if (strings.isIndonesian) "Menghitung kata pengisi..." else "Calculating filler words...") }
    var pacingSubtitle by remember { mutableStateOf(if (strings.isIndonesian) "Menganalisis tempo..." else "Analyzing pacing...") }
    var grammarSubtitle by remember { mutableStateOf(if (strings.isIndonesian) "Mengevaluasi tata bahasa..." else "Evaluating grammar...") }
    var feedbackSubtitle by remember { mutableStateOf(if (strings.isIndonesian) "Menyusun saran..." else "Preparing feedback...") }

    // Execute processing flow
    LaunchedEffect(processingTrigger) {
        currentStep = 1
        isDone = false
        sttErrorCode = null
        sttErrorMessage = null
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
                    "GEMINI_ERROR", "GEMINI_NETWORK_ERROR", "GEMINI_BUSY" -> showGeminiErrorDialog = true
                    "AUDIO_SILENT", "AUDIO_EMPTY" -> showSilentAudioDialog = true
                    "NO_SPEECH_NO_API_KEY" -> showNoSpeechDialog = true
                    else -> showErrorDialog = true
                }
                return@LaunchedEffect
            }
            val transcript = sttResult.transcript
            val wordCount = sttResult.wordCount
            val actualMainLang = if (userMainLanguage.isNotBlank()) userMainLanguage else userRepo.getUserProfile().mainLanguage
            val isMainIndonesian = actualMainLang.startsWith("Indo", ignoreCase = true)

            sttSubtitle = if (isMainIndonesian) "$wordCount kata dikenali" else "$wordCount words recognized"
            currentStep = 2

            // Step 2: Filler Words Analysis
            delay(700)
            val fillerAnalysis = FillerWordAnalyzer.analyze(transcript, language)
            val fillerCount = fillerAnalysis.fillerCount
            fillerSubtitle = if (isMainIndonesian) "$fillerCount Kata Pengisi Terdeteksi" else "$fillerCount Filler Words Detected"
            currentStep = 3

            // Step 3: Pacing Calculation
            delay(600)
            val pacingResult = PacingCalculator.calculate(wordCount, durationSeconds, actualMainLang)
            val wpm = pacingResult.wpm
            pacingSubtitle = if (isMainIndonesian) "$wpm WPM Dihitung" else "$wpm WPM Calculated"
            currentStep = 4

            // Step 4: Grammar Evaluation
            delay(800)
            val aiResult = aiEngine.evaluateGrammarAndStructure(
                transcript = transcript,
                language = language,
                userMainLanguage = actualMainLang
            )
            grammarSubtitle = if (isMainIndonesian) "Tata Bahasa & Saran AI Siap" else "Grammar Checked"
            currentStep = 5

            // Step 5: Final Feedback Compilation & Persistence
            delay(600)
            feedbackSubtitle = if (isMainIndonesian) "Hasil evaluasi siap" else "Feedback ready"

            // Compute balanced, realistic overall score:
            // 35% Filler Word Fluency (fluency & control)
            // 40% Grammar, Vocabulary & Substance (clarity & coherence)
            // 25% Speech Pacing & Tempo (WPM)
            val fillerScore = fillerAnalysis.fillerScore
            val grammarScore = aiResult.grammarScore
            val pacingScore = pacingResult.pacingScore

            val rawOverall = (fillerScore * 0.35f) + (grammarScore * 0.40f) + (pacingScore * 0.25f)
            val overallScore = kotlin.math.round(rawOverall).toInt().coerceIn(15, 98)

            // Overall score label follows the user's primary/main language
            val scoreLabel = if (isMainIndonesian) {
                when {
                    overallScore >= 90 -> "SANGAT BAIK"
                    overallScore >= 80 -> "BAGUS"
                    overallScore >= 70 -> "CUKUP BAIK"
                    overallScore >= 55 -> "CUKUP"
                    overallScore >= 40 -> "PERLU DITINGKATKAN"
                    else -> "KURANG"
                }
            } else {
                when {
                    overallScore >= 90 -> "EXCELLENT"
                    overallScore >= 80 -> "GREAT"
                    overallScore >= 70 -> "GOOD"
                    overallScore >= 55 -> "FAIR"
                    overallScore >= 40 -> "NEEDS IMPROVEMENT"
                    else -> "POOR"
                }
            }

            // Adjust delivery status based on comprehensive metrics following user's main language
            val deliveryStatus = if (isMainIndonesian) {
                when {
                    fillerAnalysis.fillerPercentage > 15f -> "Banyak Kata Pengisi"
                    fillerAnalysis.fillerPercentage > 8f -> "Ragu & Sering Filler"
                    pacingScore < 65 -> pacingResult.deliveryStatusIndonesian
                    overallScore >= 88 -> "Stabil & Percaya Diri"
                    else -> pacingResult.deliveryStatusIndonesian
                }
            } else {
                when {
                    fillerAnalysis.fillerPercentage > 15f -> "High Filler Pauses"
                    fillerAnalysis.fillerPercentage > 8f -> "Hesitant & Fillers"
                    pacingScore < 65 -> pacingResult.deliveryStatus
                    overallScore >= 88 -> "Steady & Composed"
                    else -> pacingResult.deliveryStatus
                }
            }

            val dateFormat = SimpleDateFormat("MMM d", if (isMainIndonesian) Locale("id", "ID") else Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val now = Date()

            val defaultTitle = if (practiceType.startsWith("Elevator")) {
                if (isMainIndonesian) "Perkenalan Singkat (Elevator Pitch)" else "Brief Interview Introduction"
            } else {
                if (isMainIndonesian) "Latihan Presentasi Singkat" else "Speech Practice Session"
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
            title = {
                Text(
                    if (strings.isIndonesian) "Masukkan Gemini API Key" else "Enter Gemini API Key",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )
            },
            text = {
                Column {
                    Text(
                        if (strings.isIndonesian)
                            "Masukkan API Key gratis dari Google AI Studio (aistudio.google.com) agar rekaman audio Anda ditranskripsi otomatis oleh model Gemini AI:"
                        else
                            "Enter your free Google AI Studio API Key (aistudio.google.com) so your recording is transcribed automatically by Gemini AI:",
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
                    Text(
                        if (strings.isIndonesian) "Transkripsi dengan AI" else "Transcribe with AI",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyInputDialog = false }) {
                    Text(strings.cancel, color = TextMuted)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    if (showGeminiErrorDialog) {
        val isTransientOrNetwork = sttErrorCode == "GEMINI_NETWORK_ERROR" || sttErrorCode == "GEMINI_BUSY"
        val hintText = when (sttErrorCode) {
            "GEMINI_NETWORK_ERROR" -> if (strings.isIndonesian)
                "Periksa koneksi internet / Wi-Fi / jaringan emulator Anda, lalu tekan Coba Lagi. Anda juga bisa menggunakan simulasi untuk melanjutkan evaluasi."
            else
                "Check your device/emulator internet connection and DNS, then tap Retry. You can also use a sample simulation to continue."
            "GEMINI_BUSY" -> if (strings.isIndonesian)
                "Server Gemini sedang sibuk sementara. Tunggu beberapa detik lalu tekan Coba Lagi, atau gunakan simulasi untuk melanjutkan evaluasi."
            else
                "Gemini servers are temporarily busy. Wait a few seconds and tap Retry, or use a sample simulation to continue."
            else -> if (strings.isIndonesian)
                "Pastikan API Key Anda aktif di Google AI Studio (aistudio.google.com). Anda dapat mengubah API Key sekarang atau menggunakan simulasi untuk melanjutkan evaluasi."
            else
                "Ensure your API Key is active in Google AI Studio (aistudio.google.com). You can update your API Key now or use a sample simulation to continue."
        }

        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    if (strings.isIndonesian) "Kendala Gemini AI Transcription" else "Gemini AI Transcription Issue",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (strings.isIndonesian)
                            "Gagal mentranskripsi file suara lewat Gemini AI:"
                        else
                            "Failed to transcribe audio via Gemini AI:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sttErrorMessage ?: if (strings.isIndonesian)
                                "API Key tidak valid atau batas kuota tercapai."
                            else
                                "Invalid API Key or quota limit reached.",
                            fontSize = 12.sp,
                            color = Color(0xFFB91C1C),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = hintText,
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
                    if (isTransientOrNetwork) {
                        Button(
                            onClick = {
                                showGeminiErrorDialog = false
                                processingTrigger++
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(strings.retry, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                showGeminiErrorDialog = false
                                showApiKeyInputDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (strings.isIndonesian) "Perbaiki API Key" else "Update API Key",
                                color = PrimaryNavyText
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                showGeminiErrorDialog = false
                                showApiKeyInputDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(
                                if (strings.isIndonesian) "Perbaiki API Key" else "Update API Key",
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                showGeminiErrorDialog = false
                                processingTrigger++
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.retry, color = PrimaryNavyText)
                        }
                    }

                    TextButton(
                        onClick = {
                            showGeminiErrorDialog = false
                            allowSimulationFallback = true
                            processingTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (strings.isIndonesian) "Gunakan Contoh Simulasi" else "Use Sample Simulation",
                            color = PrimaryBlue
                        )
                    }

                    TextButton(
                        onClick = {
                            showGeminiErrorDialog = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.cancel, color = TextMuted)
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
                Text(
                    if (strings.isIndonesian) "Audio Tidak Berisi Suara" else "No Speech Audio Captured",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (strings.isIndonesian)
                            "File rekaman suara berhasil dibuat, namun tidak ada gelombang ucapan manusia yang tertangkap (audio hening / 0 dB)."
                        else
                            "The audio file was created, but no human speech was detected (silent audio / 0 dB).",
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
                                if (strings.isIndonesian) "💡 Tip Emulator Android Studio:" else "💡 Android Studio Emulator Tip:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                            Text(
                                if (strings.isIndonesian)
                                    "Buka menu titik tiga (...) pada toolbar emulator > tab 'Microphone' > aktifkan 'Virtual microphone uses host audio input' dan pastikan mic laptop/PC Anda tidak di-mute."
                                else
                                    "Open the '...' menu on the emulator toolbar > 'Microphone' tab > enable 'Virtual microphone uses host audio input' and check that your PC microphone is unmuted.",
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
                        Text(
                            if (strings.isIndonesian) "Rekam Ulang" else "Record Again",
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showSilentAudioDialog = false
                            allowSimulationFallback = true
                            processingTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (strings.isIndonesian) "Gunakan Contoh Simulasi" else "Use Sample Simulation",
                            color = PrimaryNavyText
                        )
                    }

                    TextButton(
                        onClick = {
                            showSilentAudioDialog = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.cancel, color = TextMuted)
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
                Text(
                    if (strings.isIndonesian) "Tidak Ada Ucapan Terdeteksi" else "No Speech Detected",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (strings.isIndonesian)
                            "Mikrofon belum menangkap suara yang jelas, dan pengenal suara perangkat (SpeechRecognizer) belum mendeteksi kata-kata."
                        else
                            "The microphone hasn't captured clear speech yet, and the device SpeechRecognizer didn't detect words.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        if (strings.isIndonesian) "Pilihan yang dapat Anda lakukan:" else "Options you can take:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavyText
                    )
                    Text(
                        if (strings.isIndonesian)
                            "1. Masukkan Gemini API Key agar audio rekaman ditranskripsi otomatis lewat cloud AI."
                        else
                            "1. Enter a Gemini API Key to transcribe the recorded audio via cloud AI.",
                        fontSize = 12.sp,
                        color = PrimaryBlue
                    )
                    Text(
                        if (strings.isIndonesian)
                            "2. Coba rekam ulang dengan berbicara lebih dekat ke mikrofon."
                        else
                            "2. Try recording again while speaking closer to the microphone.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        if (strings.isIndonesian)
                            "3. Gunakan simulasi contoh jika Anda ingin melihat alur evaluasi."
                        else
                            "3. Use a sample simulation if you want to preview the evaluation flow.",
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
                            showApiKeyInputDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text(
                            if (strings.isIndonesian) "Gunakan Gemini AI Audio" else "Use Gemini AI Audio",
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showNoSpeechDialog = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (strings.isIndonesian) "Rekam Ulang" else "Record Again",
                            color = PrimaryNavyText
                        )
                    }

                    TextButton(
                        onClick = {
                            showNoSpeechDialog = false
                            allowSimulationFallback = true
                            processingTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (strings.isIndonesian) "Gunakan Contoh Simulasi" else "Use Sample Simulation",
                            color = TextMuted
                        )
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
                Text(
                    if (strings.isIndonesian) "Koneksi/API bermasalah" else "Connection / API Issue",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )
            },
            text = {
                Text(
                    sttErrorMessage ?: if (strings.isIndonesian)
                        "Terjadi kendala saat memproses rekaman atau memanggil API. Anda dapat mencoba kembali atau membatalkan proses."
                    else
                        "An issue occurred while processing the recording or calling the API. You can retry or cancel.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showErrorDialog = false
                        processingTrigger++
                        onErrorRetry()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(strings.retry, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showErrorDialog = false
                        onCancel()
                    }
                ) {
                    Text(strings.cancel, color = TextMuted)
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
                    text = if (isDone) {
                        if (strings.isIndonesian) "Evaluasi Anda Sudah Siap" else "Your Evaluation is Ready"
                    } else {
                        strings.analyzingSpeech
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavyText
                )

                if (isDone) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (strings.isIndonesian) "Semua langkah selesai" else "All step is done",
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
                    title = strings.stepStt,
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
                        title = strings.stepFiller,
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
                        title = strings.stepPacing,
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
                        title = strings.stepGrammar,
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
                        title = strings.stepFeedback,
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
                        text = if (strings.isIndonesian) "Lihat Hasil Evaluasi" else "Go to Session Evaluation",
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
