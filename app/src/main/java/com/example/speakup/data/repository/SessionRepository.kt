package com.example.speakup.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

    private fun buildSessionContentValues(
        session: PracticeSession,
        userId: String?,
        userEmail: String
    ): ContentValues {
        return ContentValues().apply {
            put(AppDatabaseHelper.COL_REMOTE_ID, session.remoteId)
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
    }

    suspend fun insertSession(session: PracticeSession): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val userId = userRepo.getUserId()
        val userEmail = userRepo.getUserEmail() ?: "imran@student.ub.ac.id"

        val values = buildSessionContentValues(session, userId, userEmail)
        val localId = db.insert(AppDatabaseHelper.TABLE_SESSIONS, null, values)

        // Sync to Supabase Cloud Database & Storage
        try {
            var token = userRepo.getAuthToken()

            if (!userId.isNullOrBlank()) {
                var cloudAudioUrl: String? = null
                if (!session.audioPath.isNullOrBlank()) {
                    val localAudioFile = File(session.audioPath)
                    if (localAudioFile.exists()) {
                        cloudAudioUrl = supabaseService.uploadAudio(userId, localAudioFile, token)
                        if (cloudAudioUrl == null) {
                            token = userRepo.refreshAccessToken() ?: token
                            cloudAudioUrl = supabaseService.uploadAudio(userId, localAudioFile, token)
                        }
                    }
                }
                var insertResult = supabaseService.insertPracticeSession(userId, session, cloudAudioUrl, token)
                if (!insertResult.isSuccess) {
                    val refreshed = userRepo.refreshAccessToken()
                    if (!refreshed.isNullOrBlank()) {
                        token = refreshed
                        insertResult = supabaseService.insertPracticeSession(userId, session, cloudAudioUrl, token)
                    }
                }
                if (insertResult.isSuccess && !insertResult.remoteId.isNullOrBlank()) {
                    val updateVals = ContentValues().apply {
                        put(AppDatabaseHelper.COL_REMOTE_ID, insertResult.remoteId)
                    }
                    db.update(
                        AppDatabaseHelper.TABLE_SESSIONS,
                        updateVals,
                        "${AppDatabaseHelper.COL_ID} = ?",
                        arrayOf(localId.toString())
                    )
                }
                Log.d(tag, "Session synced to Supabase: ${insertResult.isSuccess}, remoteId=${insertResult.remoteId}, Audio Cloud: $cloudAudioUrl")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to sync session to Supabase in background", e)
        }

        localId
    }

    suspend fun getAllSessions(): List<PracticeSession> = withContext(Dispatchers.IO) {
        val userId = userRepo.getUserId()
        val userEmail = userRepo.getUserEmail()
        var token = userRepo.getAuthToken()

        // 1. Fetch remote sessions from Supabase for this user and reconcile with SQLite cache
        if (!userId.isNullOrBlank()) {
            try {
                var cloudSessions = supabaseService.getPracticeSessions(userId, token)
                if (cloudSessions == null) {
                    val refreshed = userRepo.refreshAccessToken()
                    if (!refreshed.isNullOrBlank()) {
                        token = refreshed
                        cloudSessions = supabaseService.getPracticeSessions(userId, token)
                    }
                }
                if (cloudSessions != null) {
                    syncCloudSessionsToLocal(dbHelper.writableDatabase, userId, userEmail ?: "", cloudSessions)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch cloud sessions", e)
            }
        }

        // 2. Query local SQLite sessions strictly scoped to current user (unique COL_ID guaranteed)
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

        val list = mutableListOf<PracticeSession>()
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
                list.add(dbHelper.mapCursorToSession(it))
            }
        }
        list.distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    private fun syncCloudSessionsToLocal(
        db: SQLiteDatabase,
        userId: String,
        userEmail: String,
        cloudSessions: List<PracticeSession>
    ) {
        val cloudRemoteIds = cloudSessions.mapNotNull { it.remoteId }.toSet()

        // Remove cached rows that were deleted on the server
        if (cloudRemoteIds.isNotEmpty()) {
            val placeholders = cloudRemoteIds.joinToString(",") { "?" }
            db.delete(
                AppDatabaseHelper.TABLE_SESSIONS,
                "${AppDatabaseHelper.COL_SESSION_USER_ID} = ? AND ${AppDatabaseHelper.COL_REMOTE_ID} IS NOT NULL AND ${AppDatabaseHelper.COL_REMOTE_ID} NOT IN ($placeholders)",
                arrayOf(userId) + cloudRemoteIds.toTypedArray()
            )
        } else {
            db.delete(
                AppDatabaseHelper.TABLE_SESSIONS,
                "${AppDatabaseHelper.COL_SESSION_USER_ID} = ? AND ${AppDatabaseHelper.COL_REMOTE_ID} IS NOT NULL",
                arrayOf(userId)
            )
        }

        for (cloud in cloudSessions) {
            var existingId: Long? = null
            var existingLocalAudio: String? = null
            var existingDetailsJson: String? = null

            if (!cloud.remoteId.isNullOrBlank()) {
                db.query(
                    AppDatabaseHelper.TABLE_SESSIONS,
                    arrayOf(AppDatabaseHelper.COL_ID, AppDatabaseHelper.COL_AUDIO_PATH, AppDatabaseHelper.COL_FILLER_DETAILS_JSON),
                    "${AppDatabaseHelper.COL_REMOTE_ID} = ? AND ${AppDatabaseHelper.COL_SESSION_USER_ID} = ?",
                    arrayOf(cloud.remoteId, userId),
                    null, null, null
                ).use { c ->
                    if (c.moveToFirst()) {
                        existingId = c.getLong(0)
                        existingLocalAudio = c.getString(1)
                        existingDetailsJson = c.getString(2)
                    }
                }
            }

            if (existingId == null) {
                db.query(
                    AppDatabaseHelper.TABLE_SESSIONS,
                    arrayOf(AppDatabaseHelper.COL_ID, AppDatabaseHelper.COL_AUDIO_PATH, AppDatabaseHelper.COL_FILLER_DETAILS_JSON),
                    "${AppDatabaseHelper.COL_SESSION_USER_ID} = ? AND ${AppDatabaseHelper.COL_TITLE} = ? AND ${AppDatabaseHelper.COL_FORMATTED_DATE} = ? AND ${AppDatabaseHelper.COL_FORMATTED_TIME} = ?",
                    arrayOf(userId, cloud.title, cloud.formattedDate, cloud.formattedTime),
                    null, null, null
                ).use { c ->
                    if (c.moveToFirst()) {
                        existingId = c.getLong(0)
                        existingLocalAudio = c.getString(1)
                        existingDetailsJson = c.getString(2)
                    }
                }
            }

            if (existingId != null) {
                val keepLocalAudio = !existingLocalAudio.isNullOrBlank() && File(existingLocalAudio!!).exists()
                val updateVals = ContentValues().apply {
                    put(AppDatabaseHelper.COL_REMOTE_ID, cloud.remoteId)
                    put(AppDatabaseHelper.COL_TITLE, cloud.title)
                    if (!keepLocalAudio && !cloud.audioPath.isNullOrBlank()) {
                        put(AppDatabaseHelper.COL_AUDIO_PATH, cloud.audioPath)
                    }
                    if (existingDetailsJson.isNullOrBlank() || existingDetailsJson == "[]") {
                        put(AppDatabaseHelper.COL_FILLER_DETAILS_JSON, cloud.fillerDetailsJson)
                    }
                }
                db.update(
                    AppDatabaseHelper.TABLE_SESSIONS,
                    updateVals,
                    "${AppDatabaseHelper.COL_ID} = ?",
                    arrayOf(existingId.toString())
                )
            } else {
                val insertVals = buildSessionContentValues(cloud, userId, userEmail)
                db.insert(AppDatabaseHelper.TABLE_SESSIONS, null, insertVals)
            }
        }
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
        val existing = getSessionById(id)
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

        val userId = userRepo.getUserId()
        val remoteId = existing?.remoteId
        if (!remoteId.isNullOrBlank() && !userId.isNullOrBlank()) {
            var token = userRepo.getAuthToken()
            var ok = supabaseService.updatePracticeSessionTitle(remoteId, userId, newTitle, token)
            if (!ok) {
                val refreshed = userRepo.refreshAccessToken()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    supabaseService.updatePracticeSessionTitle(remoteId, userId, newTitle, token)
                }
            }
        }
        rows > 0
    }

    suspend fun deleteSession(id: Long): Boolean = withContext(Dispatchers.IO) {
        val existing = getSessionById(id)
        val db = dbHelper.writableDatabase
        val rows = db.delete(
            AppDatabaseHelper.TABLE_SESSIONS,
            "${AppDatabaseHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )

        val userId = userRepo.getUserId()
        val remoteId = existing?.remoteId
        if (!remoteId.isNullOrBlank() && !userId.isNullOrBlank()) {
            var token = userRepo.getAuthToken()
            var ok = supabaseService.deletePracticeSession(remoteId, userId, token)
            if (!ok) {
                val refreshed = userRepo.refreshAccessToken()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    supabaseService.deletePracticeSession(remoteId, userId, token)
                }
            }
        }
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
        val idealCount = sessions.count {
            it.wpm in 110..150 ||
                it.wpmCategory.contains("Ideal", ignoreCase = true) ||
                it.wpmCategory.contains("Optimal", ignoreCase = true)
        }
        val fluidity = if (total > 0) ((idealCount.toFloat() / total) * 100).toInt() else 0

        WeeklyStats(
            trainingTotals = total,
            improvementPercent = (avgScore - 50).coerceIn(10, 95),
            fluidityPercent = fluidity.coerceIn(0, 100),
            averagePaceWpm = avgWpm,
            highestScore = highest,
            personalBestSessionName = bestSession?.title ?: "Recent Practice",
            averageScore = avgScore
        )
    }
}
