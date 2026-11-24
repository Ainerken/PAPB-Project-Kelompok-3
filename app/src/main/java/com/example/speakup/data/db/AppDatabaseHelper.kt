package com.example.speakup.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.model.UserProfile
import com.example.speakup.data.model.WeeklyStats

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "speakup_db.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_SESSIONS = "practice_sessions"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_FORMATTED_DATE = "formatted_date"
        const val COL_FORMATTED_TIME = "formatted_time"
        const val COL_DURATION_SECONDS = "duration_seconds"
        const val COL_LANGUAGE = "language"
        const val COL_PRACTICE_TYPE = "practice_type"
        const val COL_AUDIO_PATH = "audio_path"
        const val COL_TRANSCRIPT = "transcript"
        const val COL_WORD_COUNT = "word_count"
        const val COL_WPM = "wpm"
        const val COL_WPM_CATEGORY = "wpm_category"
        const val COL_FILLER_COUNT = "filler_count"
        const val COL_FILLER_PERCENTAGE = "filler_percentage"
        const val COL_FILLER_STATUS = "filler_status"
        const val COL_FILLER_DETAILS_JSON = "filler_details_json"
        const val COL_OVERALL_SCORE = "overall_score"
        const val COL_GRAMMAR_SCORE = "grammar_score"
        const val COL_SCORE_LABEL = "score_label"
        const val COL_DELIVERY_STATUS = "delivery_status"
        const val COL_FEEDBACK_ORIGINAL = "feedback_original"
        const val COL_FEEDBACK_CORRECTION = "feedback_correction"
        const val COL_FEEDBACK_REASON = "feedback_reason"
        const val COL_FEEDBACK_SUMMARY = "feedback_summary"

        const val TABLE_USER = "user_profile"
        const val COL_USER_NAME = "username"
        const val COL_USER_EMAIL = "email"
        const val COL_USER_ROLE = "role"
        const val COL_USER_CONFIDENCE = "confidence"
        const val COL_USER_CHALLENGE = "challenge"
        const val COL_USER_LANGUAGE = "main_language"
        const val COL_USER_SCENARIO = "target_scenario"
        const val COL_USER_FREQUENCY = "frequency"
        const val COL_USER_TONE = "feedback_tone"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSessionsTable = """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_FORMATTED_DATE TEXT NOT NULL,
                $COL_FORMATTED_TIME TEXT NOT NULL,
                $COL_DURATION_SECONDS INTEGER NOT NULL,
                $COL_LANGUAGE TEXT NOT NULL,
                $COL_PRACTICE_TYPE TEXT NOT NULL,
                $COL_AUDIO_PATH TEXT,
                $COL_TRANSCRIPT TEXT NOT NULL,
                $COL_WORD_COUNT INTEGER NOT NULL,
                $COL_WPM INTEGER NOT NULL,
                $COL_WPM_CATEGORY TEXT NOT NULL,
                $COL_FILLER_COUNT INTEGER NOT NULL,
                $COL_FILLER_PERCENTAGE REAL NOT NULL,
                $COL_FILLER_STATUS TEXT NOT NULL,
                $COL_FILLER_DETAILS_JSON TEXT NOT NULL,
                $COL_OVERALL_SCORE INTEGER NOT NULL,
                $COL_GRAMMAR_SCORE INTEGER NOT NULL,
                $COL_SCORE_LABEL TEXT NOT NULL,
                $COL_DELIVERY_STATUS TEXT NOT NULL,
                $COL_FEEDBACK_ORIGINAL TEXT NOT NULL,
                $COL_FEEDBACK_CORRECTION TEXT NOT NULL,
                $COL_FEEDBACK_REASON TEXT NOT NULL,
                $COL_FEEDBACK_SUMMARY TEXT NOT NULL
            )
        """.trimIndent()

        val createUserTable = """
            CREATE TABLE $TABLE_USER (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                $COL_USER_NAME TEXT NOT NULL,
                $COL_USER_EMAIL TEXT NOT NULL,
                $COL_USER_ROLE TEXT NOT NULL,
                $COL_USER_CONFIDENCE TEXT NOT NULL,
                $COL_USER_CHALLENGE TEXT NOT NULL,
                $COL_USER_LANGUAGE TEXT NOT NULL,
                $COL_USER_SCENARIO TEXT NOT NULL,
                $COL_USER_FREQUENCY TEXT NOT NULL,
                $COL_USER_TONE TEXT NOT NULL
            )
        """.trimIndent()

        db.execSQL(createSessionsTable)
        db.execSQL(createUserTable)

        // Seed default user (Imran from Figma design)
        val userValues = ContentValues().apply {
            put("id", 1)
            put(COL_USER_NAME, "Imran")
            put(COL_USER_EMAIL, "imran@student.ub.ac.id")
            put(COL_USER_ROLE, "Student")
            put(COL_USER_CONFIDENCE, "Moderately")
            put(COL_USER_CHALLENGE, "Speaking Flawlessly")
            put(COL_USER_LANGUAGE, "English")
            put(COL_USER_SCENARIO, "Presentation")
            put(COL_USER_FREQUENCY, "Daily")
            put(COL_USER_TONE, "Strict")
        }
        db.insert(TABLE_USER, null, userValues)

        // Seed mock sessions matching Figma screenshots
        seedInitialSessions(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USER")
        onCreate(db)
    }

    private fun seedInitialSessions(db: SQLiteDatabase) {
        val s1 = ContentValues().apply {
            put(COL_TITLE, "Brief Interview Introduction")
            put(COL_TIMESTAMP, System.currentTimeMillis() - 86400000L * 2)
            put(COL_FORMATTED_DATE, "Oct 13")
            put(COL_FORMATTED_TIME, "01:30")
            put(COL_DURATION_SECONDS, 22)
            put(COL_LANGUAGE, "English")
            put(COL_PRACTICE_TYPE, "Elevator Pitch (1 Minute)")
            put(COL_AUDIO_PATH, "")
            put(COL_TRANSCRIPT, "Hi, my name is Dika, and I am applying for the developer position at your company. I have, um, two years of experience building mobile apps. During my study, um, I built multiple projects and uh, mastered Android development.")
            put(COL_WORD_COUNT, 38)
            put(COL_WPM, 128)
            put(COL_WPM_CATEGORY, "Optimal (Ideal)")
            put(COL_FILLER_COUNT, 3)
            put(COL_FILLER_PERCENTAGE, 7.8f)
            put(COL_FILLER_STATUS, "Berlebihan")
            put(COL_FILLER_DETAILS_JSON, """[{"word":"um","count":2},{"word":"uh","count":1}]""")
            put(COL_OVERALL_SCORE, 88)
            put(COL_GRAMMAR_SCORE, 92)
            put(COL_SCORE_LABEL, "GREAT")
            put(COL_DELIVERY_STATUS, "Steady & Composed")
            put(COL_FEEDBACK_ORIGINAL, "yesterday I have made some draft drawings...")
            put(COL_FEEDBACK_CORRECTION, "Yesterday, I prepared several visual interface drafts...")
            put(COL_FEEDBACK_REASON, "More formal and articulate for academic presentation standards.")
            put(COL_FEEDBACK_SUMMARY, "Clear pitch structure, excellent pace. Try replacing filler pauses with silent breaths.")
        }

        val s2 = ContentValues().apply {
            put(COL_TITLE, "UI/UX Presentation")
            put(COL_TIMESTAMP, System.currentTimeMillis() - 86400000L * 3)
            put(COL_FORMATTED_DATE, "Oct 12")
            put(COL_FORMATTED_TIME, "14:30")
            put(COL_DURATION_SECONDS, 65)
            put(COL_LANGUAGE, "English")
            put(COL_PRACTICE_TYPE, "Open Presentation (No Limit)")
            put(COL_AUDIO_PATH, "")
            put(COL_TRANSCRIPT, "Good afternoon everyone. Today I would like to demonstrate our new user interface for SpeakUp. We designed the layout to be minimalist and non-intimidating for students.")
            put(COL_WORD_COUNT, 146)
            put(COL_WPM, 135)
            put(COL_WPM_CATEGORY, "Optimal (Ideal)")
            put(COL_FILLER_COUNT, 2)
            put(COL_FILLER_PERCENTAGE, 1.3f)
            put(COL_FILLER_STATUS, "Baik")
            put(COL_FILLER_DETAILS_JSON, """[{"word":"like to","count":1}]""")
            put(COL_OVERALL_SCORE, 92)
            put(COL_GRAMMAR_SCORE, 95)
            put(COL_SCORE_LABEL, "EXCELLENT")
            put(COL_DELIVERY_STATUS, "Engaging & Clear")
            put(COL_FEEDBACK_ORIGINAL, "We made it very easy so user cannot confuse...")
            put(COL_FEEDBACK_CORRECTION, "We designed it intuitively so users will not get confused...")
            put(COL_FEEDBACK_REASON, "Improves clarity and maintains correct subject-verb agreement.")
            put(COL_FEEDBACK_SUMMARY, "Outstanding rhythm and confident tone throughout the presentation.")
        }

        val s3 = ContentValues().apply {
            put(COL_TITLE, "Project Presentation")
            put(COL_TIMESTAMP, System.currentTimeMillis() - 86400000L * 4)
            put(COL_FORMATTED_DATE, "Oct 11")
            put(COL_FORMATTED_TIME, "20:30")
            put(COL_DURATION_SECONDS, 90)
            put(COL_LANGUAGE, "Indonesian")
            put(COL_PRACTICE_TYPE, "Open Presentation (No Limit)")
            put(COL_AUDIO_PATH, "")
            put(COL_TRANSCRIPT, "Halo semuanya, selamat malam. Pada kesempatan kali ini saya akan menjelaskan arsitektur aplikasi mobile kami. Jadi, anu, sistem kami menggunakan integrasi speech to text dan analisis tata bahasa.")
            put(COL_WORD_COUNT, 165)
            put(COL_WPM, 110)
            put(COL_WPM_CATEGORY, "Optimal (Ideal)")
            put(COL_FILLER_COUNT, 4)
            put(COL_FILLER_PERCENTAGE, 2.4f)
            put(COL_FILLER_STATUS, "Baik")
            put(COL_FILLER_DETAILS_JSON, """[{"word":"anu","count":1},{"word":"jadi","count":2}]""")
            put(COL_OVERALL_SCORE, 76)
            put(COL_GRAMMAR_SCORE, 80)
            put(COL_SCORE_LABEL, "GOOD")
            put(COL_DELIVERY_STATUS, "Steady Pace")
            put(COL_FEEDBACK_ORIGINAL, "sistem kami adalah bisa mendeteksi langsung...")
            put(COL_FEEDBACK_CORRECTION, "Sistem kami mampu mendeteksi secara langsung...")
            put(COL_FEEDBACK_REASON, "Penggunaan kata 'mampu' lebih efektif dan baku dibanding gabungan 'adalah bisa'.")
            put(COL_FEEDBACK_SUMMARY, "Pengucapan cukup jelas. Tingkatkan variasi intonasi pada poin-poin utama.")
        }

        db.insert(TABLE_SESSIONS, null, s1)
        db.insert(TABLE_SESSIONS, null, s2)
        db.insert(TABLE_SESSIONS, null, s3)
    }

    fun mapCursorToSession(c: Cursor): PracticeSession {
        return PracticeSession(
            id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
            title = c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
            timestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP)),
            formattedDate = c.getString(c.getColumnIndexOrThrow(COL_FORMATTED_DATE)),
            formattedTime = c.getString(c.getColumnIndexOrThrow(COL_FORMATTED_TIME)),
            durationSeconds = c.getInt(c.getColumnIndexOrThrow(COL_DURATION_SECONDS)),
            language = c.getString(c.getColumnIndexOrThrow(COL_LANGUAGE)),
            practiceType = c.getString(c.getColumnIndexOrThrow(COL_PRACTICE_TYPE)),
            audioPath = c.getString(c.getColumnIndexOrThrow(COL_AUDIO_PATH)),
            transcript = c.getString(c.getColumnIndexOrThrow(COL_TRANSCRIPT)),
            wordCount = c.getInt(c.getColumnIndexOrThrow(COL_WORD_COUNT)),
            wpm = c.getInt(c.getColumnIndexOrThrow(COL_WPM)),
            wpmCategory = c.getString(c.getColumnIndexOrThrow(COL_WPM_CATEGORY)),
            fillerCount = c.getInt(c.getColumnIndexOrThrow(COL_FILLER_COUNT)),
            fillerPercentage = c.getFloat(c.getColumnIndexOrThrow(COL_FILLER_PERCENTAGE)),
            fillerStatus = c.getString(c.getColumnIndexOrThrow(COL_FILLER_STATUS)),
            fillerDetailsJson = c.getString(c.getColumnIndexOrThrow(COL_FILLER_DETAILS_JSON)),
            overallScore = c.getInt(c.getColumnIndexOrThrow(COL_OVERALL_SCORE)),
            grammarScore = c.getInt(c.getColumnIndexOrThrow(COL_GRAMMAR_SCORE)),
            scoreLabel = c.getString(c.getColumnIndexOrThrow(COL_SCORE_LABEL)),
            deliveryStatus = c.getString(c.getColumnIndexOrThrow(COL_DELIVERY_STATUS)),
            feedbackOriginal = c.getString(c.getColumnIndexOrThrow(COL_FEEDBACK_ORIGINAL)),
            feedbackCorrection = c.getString(c.getColumnIndexOrThrow(COL_FEEDBACK_CORRECTION)),
            feedbackReason = c.getString(c.getColumnIndexOrThrow(COL_FEEDBACK_REASON)),
            feedbackSummary = c.getString(c.getColumnIndexOrThrow(COL_FEEDBACK_SUMMARY))
        )
    }
}
