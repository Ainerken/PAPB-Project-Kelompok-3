package com.example.speakup.data.repository

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.speakup.data.db.AppDatabaseHelper
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.model.WeeklyStats
import com.example.speakup.data.remote.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SessionRepository(context: Context) {
    private val dbHelper = AppDatabaseHelper(context.applicationContext)
    private val supabaseService = SupabaseService()
    private val userRepo = UserRepository(context.applicationContext)
    private val tag = "SessionRepository"

    suspend fun insertSession(session: PracticeSession): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val userId = userRepo.getUserId()
        val userEmail = userRepo.getUserEmail() ?: "imran@student.ub.ac.id"

        val values = ContentValues().apply {
            put(AppDatabaseHelper.COL_SESSION_USER_ID, userId ?: "demo_user")
            put(AppDatabaseHelper.COL_SESSION_USER_EMAIL, userEmail)
            put(AppDatabaseHelper.COL_TITLE, session.title)
            put(AppDatabaseHelper.COL_TIMESTAMP, session.timestamp)
            put(AppDatabaseHelper.COL_FORMATTED_DATE, session.formattedDate)
            put(AppDatabaseHelper.COL_FORMATTED_TIME, session.formattedTime)
            put(AppDatabaseHelper.COL_DURATION_SECONDS, session.durationSeconds)
            put(AppDatabaseHelper.COL_LANGUAGE, session.language)
            put(AppDatabaseHelper.COL_PRACTICE_TYPE, session.practiceType)
            put(AppDatabaseHelper.COL_AUDIO_PATH, session.audioPath ?: "")
            put(AppDatabaseHelper.COL_TRANSCRIPT, session.transcript)
            put(AppDatabaseHelper.COL_WORD_COUNT, session.wordCount)
            put(AppDatabaseHelper.COL_WPM, session.wpm)
            put(AppDatabaseHelper.COL_WPM_CATEGORY, session.wpmCategory)
            put(AppDatabaseHelper.COL_FILLER_COUNT, session.fillerCount)
            put(AppDatabaseHelper.COL_FILLER_PERCENTAGE, session.fillerPercentage)
            put(AppDatabaseHelper.COL_FILLER_STATUS, session.fillerStatus)
            put(AppDatabaseHelper.COL_FILLER_DETAILS_JSON, session.fillerDetailsJson)
            put(AppDatabaseHelper.COL_OVERALL_SCORE, session.overallScore)
            put(AppDatabaseHelper.COL_GRAMMAR_SCORE, session.grammarScore)
            put(AppDatabaseHelper.COL_SCORE_LABEL, session.scoreLabel)
            put(AppDatabaseHelper.COL_DELIVERY_STATUS, session.deliveryStatus)
            put(AppDatabaseHelper.COL_FEEDBACK_ORIGINAL, session.feedbackOriginal)
            put(AppDatabaseHelper.COL_FEEDBACK_CORRECTION, session.feedbackCorrection)
            put(AppDatabaseHelper.COL_FEEDBACK_REASON, session.feedbackReason)
            put(AppDatabaseHelper.COL_FEEDBACK_SUMMARY, session.feedbackSummary)
        }
        val localId = db.insert(AppDatabaseHelper.TABLE_SESSIONS, null, values)

        // Sync to Supabase Cloud Database & Storage
        try {
            val token = userRepo.getAuthToken()

            if (!userId.isNullOrBlank()) {
                var cloudAudioUrl: String? = null
                if (!session.audioPath.isNullOrBlank()) {
                    val localAudioFile = File(session.audioPath)
                    if (localAudioFile.exists()) {
                        cloudAudioUrl = supabaseService.uploadAudio(userId, localAudioFile, token)
                    }
                }
                val inserted = supabaseService.insertPracticeSession(userId, session, cloudAudioUrl, token)
                Log.d(tag, "Session synced to Supabase: $inserted, Audio Cloud: $cloudAudioUrl")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to sync session to Supabase in background", e)
        }

        localId
    }

    suspend fun getAllSessions(): List<PracticeSession> = withContext(Dispatchers.IO) {
        val userId = userRepo.getUserId()
        val userEmail = userRepo.getUserEmail()
        val token = userRepo.getAuthToken()

        val list = mutableListOf<PracticeSession>()

        // 1. Fetch remote sessions from Supabase for this user (if logged in to Supabase)
        if (!userId.isNullOrBlank()) {
            try {
                val cloudSessions = supabaseService.getPracticeSessions(userId, token)
                if (cloudSessions.isNotEmpty()) {
                    list.addAll(cloudSessions)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch cloud sessions", e)
            }
        }

        // 2. Query local SQLite sessions strictly scoped to current user
        val db = dbHelper.readableDatabase
        val selection: String
        val selectionArgs: Array<String>?

        if (!userId.isNullOrBlank() && !userEmail.isNullOrBlank()) {
            selection = "${AppDatabaseHelper.COL_SESSION_USER_ID} = ? OR ${AppDatabaseHelper.COL_SESSION_USER_EMAIL} = ?"
            selectionArgs = arrayOf(userId, userEmail)
        } else if (!userId.isNullOrBlank()) {
            selection = "${AppDatabaseHelper.COL_SESSION_USER_ID} = ?"
            selectionArgs = arrayOf(userId)
        } else if (!userEmail.isNullOrBlank()) {
            selection = "${AppDatabaseHelper.COL_SESSION_USER_EMAIL} = ?"
            selectionArgs = arrayOf(userEmail)
        } else {
            // Demo mode / default unauthenticated user
            selection = "${AppDatabaseHelper.COL_SESSION_USER_ID} = 'demo_user' OR ${AppDatabaseHelper.COL_SESSION_USER_EMAIL} = 'imran@student.ub.ac.id'"
            selectionArgs = null
        }

        val cursor = db.query(
            AppDatabaseHelper.TABLE_SESSIONS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "${AppDatabaseHelper.COL_TIMESTAMP} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val localSession = dbHelper.mapCursorToSession(it)
                val duplicate = list.any { s ->
                    s.title.equals(localSession.title, ignoreCase = true) &&
                    s.formattedDate == localSession.formattedDate &&
                    s.formattedTime == localSession.formattedTime
                }
                if (!duplicate) {
                    list.add(localSession)
                }
            }
        }
        list.sortedByDescending { it.timestamp }
    }

    suspend fun getSessionsByLanguage(language: String): List<PracticeSession> = withContext(Dispatchers.IO) {
        val all = getAllSessions()
        all.filter { it.language.equals(language, ignoreCase = true) || it.language.startsWith(language.take(4), ignoreCase = true) }
    }

    suspend fun getSessionById(id: Long): PracticeSession? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AppDatabaseHelper.TABLE_SESSIONS,
            null,
            "${AppDatabaseHelper.COL_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return@withContext dbHelper.mapCursorToSession(it)
            }
        }
        null
    }

    suspend fun getLatestSession(): PracticeSession? = withContext(Dispatchers.IO) {
        getAllSessions().firstOrNull()
    }

    suspend fun updateSessionTitle(id: Long, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(AppDatabaseHelper.COL_TITLE, newTitle)
        }
        val rows = db.update(
            AppDatabaseHelper.TABLE_SESSIONS,
            values,
            "${AppDatabaseHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )
        rows > 0
    }

    suspend fun deleteSession(id: Long): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val rows = db.delete(
            AppDatabaseHelper.TABLE_SESSIONS,
            "${AppDatabaseHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )
        rows > 0
    }

    suspend fun getWeeklyStats(): WeeklyStats = withContext(Dispatchers.IO) {
        val sessions = getAllSessions()
        if (sessions.isEmpty()) {
            return@withContext WeeklyStats(
                trainingTotals = 0,
                improvementPercent = 0,
                fluidityPercent = 0,
                averagePaceWpm = 0,
                highestScore = 0,
                personalBestSessionName = "Belum Ada Sesi",
                averageScore = 0
            )
        }

        val total = sessions.size
        val avgScore = sessions.map { it.overallScore }.average().toInt()
        val highest = sessions.maxOf { it.overallScore }
        val bestSession = sessions.maxByOrNull { it.overallScore }
        val avgWpm = sessions.map { it.wpm }.average().toInt()
        val idealCount = sessions.count { it.wpmCategory == "Ideal" }
        val fluidity = if (total > 0) ((idealCount.toFloat() / total) * 100).toInt() else 85

        WeeklyStats(
            trainingTotals = total,
            improvementPercent = (avgScore - 50).coerceIn(10, 95),
            fluidityPercent = fluidity.coerceIn(40, 98),
            averagePaceWpm = avgWpm,
            highestScore = highest,
            personalBestSessionName = bestSession?.title ?: "Recent Practice",
            averageScore = avgScore
        )
    }
}
