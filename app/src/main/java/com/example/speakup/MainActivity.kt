package com.example.speakup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.model.UserProfile
import com.example.speakup.data.model.WeeklyStats
import com.example.speakup.data.repository.SessionRepository
import com.example.speakup.data.repository.UserRepository
import com.example.speakup.ui.navigation.Screen
import com.example.speakup.ui.screens.*
import com.example.speakup.ui.theme.BackgroundLight
import com.example.speakup.ui.theme.SpeakUpTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionRepository = SessionRepository(this)
        val userRepository = UserRepository(this)

        setContent {
            SpeakUpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    SpeakUpApp(
                        sessionRepository = sessionRepository,
                        userRepository = userRepository
                    )
                }
            }
        }
    }
}

@Composable
fun SpeakUpApp(
    sessionRepository: SessionRepository,
    userRepository: UserRepository
) {
    val coroutineScope = rememberCoroutineScope()

    // Screen navigation stack
    var currentScreen by remember {
        mutableStateOf<Screen>(
            when {
                !userRepository.hasCompletedOnboarding() -> Screen.Onboarding
                !userRepository.isUserLoggedIn() -> Screen.Login
                else -> Screen.Dashboard
            }
        )
    }

    // App Data State
    var userProfile by remember { mutableStateOf(UserProfile()) }
    var weeklyStats by remember {
        mutableStateOf(
            WeeklyStats(
                trainingTotals = 10,
                improvementPercent = 54,
                fluidityPercent = 92,
                averagePaceWpm = 126,
                highestScore = 95,
                personalBestSessionName = "Thursday session",
                averageScore = 88
            )
        )
    }
    var recentSessions by remember { mutableStateOf<List<PracticeSession>>(emptyList()) }
    var latestSession by remember { mutableStateOf<PracticeSession?>(null) }

    fun refreshDashboardData() {
        coroutineScope.launch {
            userProfile = userRepository.getUserProfile()
            weeklyStats = sessionRepository.getWeeklyStats()
            val all = sessionRepository.getAllSessions()
            recentSessions = all
            latestSession = all.firstOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshDashboardData()
    }

    // Temporary session parameters during recording workflow
    var sessionLanguage by remember { mutableStateOf("English") }
    var sessionPracticeType by remember { mutableStateOf("Open Presentation (No Limit)") }
    var recordedAudioPath by remember { mutableStateOf<String?>(null) }
    var recordedDurationSeconds by remember { mutableIntStateOf(0) }
    var recordedRecognizedText by remember { mutableStateOf("") }
    var evaluationSessionId by remember { mutableLongStateOf(0L) }

    // Registration survey state
    var regUsername by remember { mutableStateOf("Imran") }
    var regEmail by remember { mutableStateOf("imran@student.ub.ac.id") }
    var regPassword by remember { mutableStateOf("password123") }
    var regRole by remember { mutableStateOf("Student") }
    var regConfidence by remember { mutableStateOf("Moderately") }
    var regChallenge by remember { mutableStateOf("Speaking Flawlessly") }
    var regLanguage by remember { mutableStateOf("English") }
    var regScenario by remember { mutableStateOf("Job Interview") }
    var regFrequency by remember { mutableStateOf("Daily") }
    var regTone by remember { mutableStateOf("Encouraging") }

    // Handle System Back Press
    BackHandler(enabled = currentScreen !is Screen.Dashboard && currentScreen !is Screen.Onboarding) {
        currentScreen = when (currentScreen) {
            is Screen.Login -> Screen.Onboarding
            is Screen.Register -> Screen.Login
            is Screen.SkillSurvey -> Screen.Register
            is Screen.PreferencesSurvey -> Screen.SkillSurvey
            is Screen.CreatingAccount -> Screen.PreferencesSurvey
            is Screen.Welcome -> Screen.Dashboard
            is Screen.SessionSetup -> Screen.Dashboard
            is Screen.Recording -> Screen.SessionSetup
            is Screen.Processing -> Screen.Dashboard
            is Screen.SessionEvaluation -> Screen.Dashboard
            is Screen.History -> Screen.Dashboard
            is Screen.Profile -> Screen.Dashboard
            is Screen.ChangePassword -> Screen.Profile
            is Screen.ForgotPassword -> Screen.Profile
            else -> Screen.Dashboard
        }
    }

    when (val screen = currentScreen) {
        is Screen.Onboarding -> {
            OnboardingScreen(
                onContinue = {
                    userRepository.setCompletedOnboarding(true)
                    currentScreen = Screen.Login
                }
            )
        }

        is Screen.Login -> {
            LoginScreen(
                initialEmail = regEmail,
                onLogin = { email, pass, onError ->
                    coroutineScope.launch {
                        val res = userRepository.signIn(email, pass)
                        if (res.isSuccess) {
                            refreshDashboardData()
                            currentScreen = Screen.Dashboard
                        } else {
                            onError(res.errorMessage ?: "Email atau password salah")
                        }
                    }
                },
                onNavigateToRegister = {
                    currentScreen = Screen.Register
                },
                onNavigateToForgotPassword = {
                    currentScreen = Screen.ForgotPassword
                },
                onDemoLogin = {
                    userRepository.setLoggedIn(true)
                    refreshDashboardData()
                    currentScreen = Screen.Dashboard
                }
            )
        }

        is Screen.Register -> {
            RegisterScreen(
                onBack = { currentScreen = Screen.Login },
                onNext = { username, email, password ->
                    regUsername = username
                    regEmail = email
                    regPassword = password
                    currentScreen = Screen.SkillSurvey
                }
            )
        }

        is Screen.SkillSurvey -> {
            SkillSurveyScreen(
                onBack = { currentScreen = Screen.Register },
                onNext = { role, confidence, challenge ->
                    regRole = role
                    regConfidence = confidence
                    regChallenge = challenge
                    currentScreen = Screen.PreferencesSurvey
                }
            )
        }

        is Screen.PreferencesSurvey -> {
            PreferencesSurveyScreen(
                onBack = { currentScreen = Screen.SkillSurvey },
                onComplete = { lang, scenario, freq, tone ->
                    regLanguage = lang
                    regScenario = scenario
                    regFrequency = freq
                    regTone = tone
                    currentScreen = Screen.CreatingAccount
                }
            )
        }

        is Screen.CreatingAccount -> {
            CreatingAccountScreen(
                onExecuteSignUp = {
                    val res = userRepository.signUp(
                        email = regEmail,
                        pass = regPassword,
                        name = regUsername,
                        role = regRole,
                        confidence = regConfidence,
                        challenge = regChallenge,
                        language = regLanguage,
                        scenario = regScenario,
                        frequency = regFrequency,
                        tone = regTone
                    )
                    Pair(res.isSuccess, res.errorMessage)
                },
                onSuccess = {
                    refreshDashboardData()
                    currentScreen = Screen.Welcome
                },
                onBackToRegister = {
                    currentScreen = Screen.Register
                }
            )
        }

        is Screen.Welcome -> {
            WelcomeScreen(
                username = regUsername,
                onContinue = {
                    refreshDashboardData()
                    currentScreen = Screen.Dashboard
                }
            )
        }

        is Screen.Dashboard -> {
            DashboardScreen(
                userProfile = userProfile,
                latestSession = latestSession,
                recentSessions = recentSessions,
                weeklyStats = weeklyStats,
                onStartNewSession = {
                    sessionLanguage = userProfile.mainLanguage
                    currentScreen = Screen.SessionSetup
                },
                onSessionClick = { id ->
                    evaluationSessionId = id
                    currentScreen = Screen.SessionEvaluation(id)
                },
                onNavigateToHistory = {
                    currentScreen = Screen.History
                },
                onNavigateToProfile = {
                    currentScreen = Screen.Profile
                }
            )
        }

        is Screen.SessionSetup -> {
            SessionSetupScreen(
                initialLanguage = userProfile.mainLanguage,
                onBack = { currentScreen = Screen.Dashboard },
                onStartRecording = { lang, type ->
                    sessionLanguage = lang
                    sessionPracticeType = type
                    currentScreen = Screen.Recording
                }
            )
        }

        is Screen.Recording -> {
            RecordingScreen(
                language = sessionLanguage,
                practiceType = sessionPracticeType,
                onCancel = { currentScreen = Screen.SessionSetup },
                onRecordingFinished = { audioPath, duration, recognizedTranscript ->
                    recordedAudioPath = audioPath
                    recordedDurationSeconds = duration
                    recordedRecognizedText = recognizedTranscript
                    currentScreen = Screen.Processing
                }
            )
        }

        is Screen.Processing -> {
            ProcessingScreen(
                audioPath = recordedAudioPath,
                durationSeconds = recordedDurationSeconds,
                language = sessionLanguage,
                practiceType = sessionPracticeType,
                recognizedTranscript = recordedRecognizedText,
                onComplete = { newSessionId ->
                    evaluationSessionId = newSessionId
                    refreshDashboardData()
                    currentScreen = Screen.SessionEvaluation(newSessionId)
                },
                onErrorRetry = {
                    currentScreen = Screen.Processing
                },
                onCancel = {
                    currentScreen = Screen.Dashboard
                }
            )
        }

        is Screen.SessionEvaluation -> {
            SessionEvaluationScreen(
                sessionId = evaluationSessionId,
                onClose = {
                    refreshDashboardData()
                    currentScreen = Screen.Dashboard
                },
                onPracticeAgain = {
                    currentScreen = Screen.SessionSetup
                },
                onSaveFinished = {
                    refreshDashboardData()
                    currentScreen = Screen.Dashboard
                }
            )
        }

        is Screen.History -> {
            HistoryScreen(
                onNavigateToHome = {
                    refreshDashboardData()
                    currentScreen = Screen.Dashboard
                },
                onStartNewSession = {
                    sessionLanguage = userProfile.mainLanguage
                    currentScreen = Screen.SessionSetup
                },
                onSessionClick = { id ->
                    evaluationSessionId = id
                    currentScreen = Screen.SessionEvaluation(id)
                }
            )
        }

        is Screen.Profile -> {
            ProfileScreen(
                userProfile = userProfile,
                onBack = {
                    refreshDashboardData()
                    currentScreen = Screen.Dashboard
                },
                onNavigateToChangePassword = {
                    currentScreen = Screen.ChangePassword
                },
                onProfileUpdated = { updated ->
                    userProfile = updated
                },
                onLogout = {
                    coroutineScope.launch {
                        userRepository.signOut()
                        recentSessions = emptyList()
                        latestSession = null
                        currentScreen = Screen.Login
                    }
                }
            )
        }

        is Screen.ChangePassword -> {
            ChangePasswordScreen(
                onBack = { currentScreen = Screen.Profile },
                onNavigateToForgotPassword = { currentScreen = Screen.ForgotPassword }
            )
        }

        is Screen.ForgotPassword -> {
            ForgotPasswordScreen(
                onBack = { currentScreen = Screen.Profile },
                onSuccess = { currentScreen = Screen.Profile }
            )
        }
    }
}
