package com.example.speakup.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import com.example.speakup.data.db.AppDatabaseHelper
import com.example.speakup.data.model.UserProfile
import com.example.speakup.data.remote.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    private val dbHelper = AppDatabaseHelper(context.applicationContext)
    private val prefs: SharedPreferences = context.getSharedPreferences("speakup_prefs", Context.MODE_PRIVATE)
    private val supabaseService = SupabaseService()

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_USER_PASSWORD = "user_password"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SUPABASE_TOKEN = "supabase_token"
        private const val KEY_SUPABASE_REFRESH_TOKEN = "supabase_refresh_token"
        private const val KEY_SUPABASE_USER_ID = "supabase_user_id"
        private const val KEY_SUPABASE_USER_EMAIL = "supabase_user_email"
    }

    fun isUserLoggedIn(): Boolean {
        val hasToken = !getAuthToken().isNullOrBlank()
        val hasFlag = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        return hasToken || hasFlag
    }

    fun setLoggedIn(loggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).apply()
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
    }

    fun setCompletedOnboarding(completed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed).apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_SUPABASE_TOKEN, null)
    }

    fun getUserId(): String? {
        return prefs.getString(KEY_SUPABASE_USER_ID, null)
    }

    fun getUserEmail(): String? {
        return prefs.getString(KEY_SUPABASE_USER_EMAIL, null)
    }

    fun getPassword(): String {
        return prefs.getString(KEY_USER_PASSWORD, "password123") ?: "password123"
    }

    fun setPassword(newPass: String) {
        prefs.edit().putString(KEY_USER_PASSWORD, newPass).apply()
    }

    fun getGeminiApiKey(): String {
        return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun setGeminiApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
    }

    suspend fun signIn(email: String, pass: String): SupabaseService.AuthResult = withContext(Dispatchers.IO) {
        val result = supabaseService.signIn(email, pass)
        if (result.isSuccess) {
            prefs.edit()
                .putString(KEY_SUPABASE_TOKEN, result.accessToken)
                .putString(KEY_SUPABASE_REFRESH_TOKEN, result.refreshToken)
                .putString(KEY_SUPABASE_USER_ID, result.userId)
                .putString(KEY_SUPABASE_USER_EMAIL, result.email)
                .putString(KEY_USER_PASSWORD, pass)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply()

            // Sync user profile from Supabase
            if (!result.userId.isNullOrBlank()) {
                val cloudProfile = supabaseService.getProfile(result.userId, result.accessToken)
                if (cloudProfile != null) {
                    updateLocalProfile(cloudProfile)
                }
            }
        }
        result
    }

    suspend fun signUp(
        email: String,
        pass: String,
        name: String,
        role: String = "Student",
        confidence: String = "Moderately",
        challenge: String = "Speaking Flawlessly",
        language: String = "English",
        scenario: String = "Job Interview",
        frequency: String = "Few times a week",
        tone: String = "Encouraging"
    ): SupabaseService.AuthResult = withContext(Dispatchers.IO) {
        val result = supabaseService.signUp(email, pass, name)
        if (result.isSuccess) {
            prefs.edit()
                .putString(KEY_SUPABASE_TOKEN, result.accessToken)
                .putString(KEY_SUPABASE_REFRESH_TOKEN, result.refreshToken)
                .putString(KEY_SUPABASE_USER_ID, result.userId)
                .putString(KEY_SUPABASE_USER_EMAIL, result.email)
                .putString(KEY_USER_PASSWORD, pass)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply()

            val newProfile = UserProfile(
                username = name,
                email = email,
                role = role,
                confidence = confidence,
                challenge = challenge,
                mainLanguage = language,
                targetScenario = scenario,
                frequency = frequency,
                feedbackTone = tone
            )
            updateLocalProfile(newProfile)

            // Upsert in Supabase
            if (!result.userId.isNullOrBlank()) {
                supabaseService.upsertProfile(result.userId, newProfile, result.accessToken)
            }
        }
        result
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = getAuthToken()
        supabaseService.signOut(token)
        prefs.edit()
            .remove(KEY_SUPABASE_TOKEN)
            .remove(KEY_SUPABASE_REFRESH_TOKEN)
            .remove(KEY_SUPABASE_USER_ID)
            .remove(KEY_SUPABASE_USER_EMAIL)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }

    suspend fun getUserProfile(): UserProfile = withContext(Dispatchers.IO) {
        val userId = getUserId()
        val token = getAuthToken()

        // Try getting remote profile from Supabase first
        if (!userId.isNullOrBlank()) {
            val remote = supabaseService.getProfile(userId, token)
            if (remote != null) {
                updateLocalProfile(remote)
                return@withContext remote
            }
        }

        // Fallback to SQLite
        getLocalProfile()
    }

    private fun getLocalProfile(): UserProfile {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AppDatabaseHelper.TABLE_USER,
            null,
            "id = 1",
            null,
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val photoCol = it.getColumnIndex(AppDatabaseHelper.COL_USER_PHOTO_URI)
                val photoUri = if (photoCol != -1) it.getString(photoCol) else null
                return UserProfile(
                    username = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_NAME)),
                    email = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_EMAIL)),
                    role = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_ROLE)),
                    confidence = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_CONFIDENCE)),
                    challenge = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_CHALLENGE)),
                    mainLanguage = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_LANGUAGE)),
                    targetScenario = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_SCENARIO)),
                    frequency = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_FREQUENCY)),
                    feedbackTone = it.getString(it.getColumnIndexOrThrow(AppDatabaseHelper.COL_USER_TONE)),
                    photoUri = photoUri
                )
            }
        }
        return UserProfile()
    }

    private fun updateLocalProfile(profile: UserProfile): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(AppDatabaseHelper.COL_USER_NAME, profile.username)
            put(AppDatabaseHelper.COL_USER_EMAIL, profile.email)
            put(AppDatabaseHelper.COL_USER_ROLE, profile.role)
            put(AppDatabaseHelper.COL_USER_CONFIDENCE, profile.confidence)
            put(AppDatabaseHelper.COL_USER_CHALLENGE, profile.challenge)
            put(AppDatabaseHelper.COL_USER_LANGUAGE, profile.mainLanguage)
            put(AppDatabaseHelper.COL_USER_SCENARIO, profile.targetScenario)
            put(AppDatabaseHelper.COL_USER_FREQUENCY, profile.frequency)
            put(AppDatabaseHelper.COL_USER_TONE, profile.feedbackTone)
            put(AppDatabaseHelper.COL_USER_PHOTO_URI, profile.photoUri)
        }
        val rows = db.update(AppDatabaseHelper.TABLE_USER, values, "id = 1", null)
        return rows > 0
    }

    suspend fun updateUserProfile(profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
        val localSuccess = updateLocalProfile(profile)
        val userId = getUserId()
        val token = getAuthToken()
        if (!userId.isNullOrBlank()) {
            supabaseService.upsertProfile(userId, profile, token)
        }
        localSuccess
    }

    suspend fun uploadAvatarToCloud(imageBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val userId = getUserId() ?: "guest_${System.currentTimeMillis()}"
        val token = getAuthToken()
        supabaseService.uploadAvatar(userId, imageBytes, token)
    }
}
