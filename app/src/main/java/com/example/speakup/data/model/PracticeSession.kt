package com.example.speakup.data.model

data class PracticeSession(
    val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDate: String,
    val formattedTime: String,
    val durationSeconds: Int,
    val language: String, // "English" or "Indonesian"
    val practiceType: String, // "Open Presentation (No Limit)" or "Elevator Pitch (1 Minute)"
    val audioPath: String? = null,
    val transcript: String,
    val wordCount: Int,
    val wpm: Int,
    val wpmCategory: String, // "Optimal (Ideal)", "Terlalu Lambat", "Terlalu Cepat"
    val fillerCount: Int,
    val fillerPercentage: Float,
    val fillerStatus: String, // "Baik" or "Berlebihan"
    val fillerDetailsJson: String = "[]",
    val overallScore: Int,
    val grammarScore: Int,
    val scoreLabel: String = "GREAT",
    val deliveryStatus: String = "Steady & Composed",
    val feedbackOriginal: String = "",
    val feedbackCorrection: String = "",
    val feedbackReason: String = "",
    val feedbackSummary: String = ""
)

data class FillerWordMatch(
    val word: String,
    val startIndex: Int,
    val endIndex: Int
)

data class FillerStat(
    val word: String,
    val count: Int
)

data class AiFeedbackItem(
    val originalSentence: String,
    val correctedSentence: String,
    val reason: String,
    val summaryFeedback: String = "",
    val grammarScore: Int = 90
)

data class WeeklyStats(
    val trainingTotals: Int,
    val improvementPercent: Int,
    val fluidityPercent: Int,
    val averagePaceWpm: Int,
    val highestScore: Int,
    val personalBestSessionName: String,
    val averageScore: Int
)

data class UserProfile(
    val username: String = "Imran",
    val email: String = "imran@student.ub.ac.id",
    val role: String = "Student",
    val confidence: String = "Moderately",
    val challenge: String = "Speaking Flawlessly",
    val mainLanguage: String = "English",
    val targetScenario: String = "Presentation",
    val frequency: String = "Daily",
    val feedbackTone: String = "Strict",
    val photoUri: String? = null
)
