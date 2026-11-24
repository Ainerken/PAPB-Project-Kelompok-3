package com.example.speakup.data.remote

import android.util.Log
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.model.UserProfile
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class SupabaseService {

    private val tag = "SupabaseService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    data class AuthResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val email: String? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val errorMessage: String? = null
    )

    // 1. Sign Up User
    suspend fun signUp(email: String, password: String, name: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            val cleanPass = password.trim()
            val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/signup"
            val bodyObj = JsonObject().apply {
                addProperty("email", cleanEmail)
                addProperty("password", cleanPass)
                val dataObj = JsonObject().apply {
                    addProperty("name", name.trim())
                }
                add("data", dataObj)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(bodyObj).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val resBody = response.body?.string() ?: ""
                Log.d(tag, "Sign Up response (${response.code}): $resBody")

                if (response.isSuccessful) {
                    val root = JsonParser.parseString(resBody).asJsonObject
                    val userObj = if (root.has("user")) root.getAsJsonObject("user") else root
                    val userId = userObj?.get("id")?.asString
                    val accessToken = root.get("access_token")?.asString
                    val refreshToken = root.get("refresh_token")?.asString
                    val userEmail = userObj?.get("email")?.asString ?: email

                    // Automatically initialize profile in profiles table
                    if (!userId.isNullOrBlank()) {
                        val initProfile = UserProfile(
                            username = name,
                            email = userEmail
                        )
                        upsertProfile(userId, initProfile, accessToken)
                    }

                    AuthResult(
                        isSuccess = true,
                        userId = userId,
                        email = userEmail,
                        accessToken = accessToken,
                        refreshToken = refreshToken
                    )
                } else {
                    val parsedErr = parseErrorMessage(resBody, response.code)
                    AuthResult(isSuccess = false, errorMessage = parsedErr)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Sign Up error", e)
            AuthResult(isSuccess = false, errorMessage = e.localizedMessage ?: "Gagal mendaftar ke server Supabase")
        }
    }

    // 2. Sign In User
    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            val cleanPass = password.trim()
            val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/token?grant_type=password"
            val bodyObj = JsonObject().apply {
                addProperty("email", cleanEmail)
                addProperty("password", cleanPass)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(bodyObj).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val resBody = response.body?.string() ?: ""
                Log.d(tag, "Sign In response (${response.code}): $resBody")

                if (response.isSuccessful) {
                    val root = JsonParser.parseString(resBody).asJsonObject
                    val accessToken = root.get("access_token")?.asString
                    val refreshToken = root.get("refresh_token")?.asString
                    val userObj = root.getAsJsonObject("user")
                    val userId = userObj?.get("id")?.asString
                    val userEmail = userObj?.get("email")?.asString ?: email

                    AuthResult(
                        isSuccess = true,
                        userId = userId,
                        email = userEmail,
                        accessToken = accessToken,
                        refreshToken = refreshToken
                    )
                } else {
                    val parsedErr = parseErrorMessage(resBody, response.code)
                    AuthResult(isSuccess = false, errorMessage = parsedErr)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Sign In error", e)
            AuthResult(isSuccess = false, errorMessage = e.localizedMessage ?: "Gagal login ke server Supabase")
        }
    }

    // 3. Sign Out
    suspend fun signOut(token: String?): Boolean = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) return@withContext true
        try {
            val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/logout"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // 4. Get User Profile from Supabase Database
    suspend fun getProfile(userId: String, token: String?): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/profiles?id=eq.$userId&select=*"
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .get()

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val resBody = response.body?.string() ?: return@withContext null
                val jsonArr = JsonParser.parseString(resBody).asJsonArray
                if (jsonArr.size() == 0) return@withContext null

                val obj = jsonArr[0].asJsonObject
                UserProfile(
                    username = obj.get("name")?.asString ?: "User",
                    email = obj.get("email")?.asString ?: "",
                    challenge = obj.get("biggest_challenge")?.asString ?: "Speaking Flawlessly",
                    mainLanguage = obj.get("main_language")?.asString ?: "English",
                    photoUri = obj.get("photo_url")?.asString?.ifBlank { null }
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get profile from Supabase", e)
            null
        }
    }

    // 5. Upsert User Profile
    suspend fun upsertProfile(userId: String, profile: UserProfile, token: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/profiles"
            val bodyObj = JsonObject().apply {
                addProperty("id", userId)
                addProperty("name", profile.username)
                addProperty("email", profile.email)
                addProperty("main_language", profile.mainLanguage)
                addProperty("biggest_challenge", profile.challenge)
                addProperty("photo_url", profile.photoUri ?: "")
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(bodyObj).toRequestBody(jsonMediaType))

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                Log.d(tag, "Upsert profile response (${response.code})")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to upsert profile to Supabase", e)
            false
        }
    }

    // 6. Upload Audio Recording to Supabase Storage Bucket ('recordings')
    suspend fun uploadAudio(userId: String, audioFile: File, token: String?): String? = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() <= 0) return@withContext null
        try {
            val remoteFileName = "${System.currentTimeMillis()}_${audioFile.name}"
            val url = "${SupabaseConfig.PROJECT_URL}/storage/v1/object/recordings/$userId/$remoteFileName"

            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "audio/m4a")
                .addHeader("x-upsert", "true")
                .post(audioFile.asRequestBody("audio/m4a".toMediaType()))

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val publicUrl = "${SupabaseConfig.PROJECT_URL}/storage/v1/object/public/recordings/$userId/$remoteFileName"
                    Log.d(tag, "Audio uploaded to Supabase: $publicUrl")
                    publicUrl
                } else {
                    Log.w(tag, "Upload audio returned code: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload audio to Supabase Storage", e)
            null
        }
    }

    // 7. Upload Avatar to Supabase Storage Bucket ('avatars')
    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray, token: String?): String? = withContext(Dispatchers.IO) {
        if (imageBytes.isEmpty()) return@withContext null
        try {
            val remoteFileName = "avatar_${System.currentTimeMillis()}.jpg"
            val url = "${SupabaseConfig.PROJECT_URL}/storage/v1/object/avatars/$userId/$remoteFileName"

            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val publicUrl = "${SupabaseConfig.PROJECT_URL}/storage/v1/object/public/avatars/$userId/$remoteFileName"
                    Log.d(tag, "Avatar uploaded to Supabase: $publicUrl")
                    publicUrl
                } else {
                    Log.w(tag, "Upload avatar returned code: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload avatar to Supabase Storage", e)
            null
        }
    }

    // 8. Insert Practice Session into Supabase Database
    suspend fun insertPracticeSession(
        userId: String,
        session: PracticeSession,
        audioCloudUrl: String?,
        token: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/practice_sessions"
            val bodyObj = JsonObject().apply {
                addProperty("user_id", userId)
                addProperty("title", session.title)
                addProperty("formatted_date", session.formattedDate)
                addProperty("formatted_time", session.formattedTime)
                addProperty("duration_seconds", session.durationSeconds)
                addProperty("language", session.language)
                addProperty("practice_type", session.practiceType)
                addProperty("audio_url", audioCloudUrl ?: session.audioPath ?: "")
                addProperty("transcript", session.transcript)
                addProperty("word_count", session.wordCount)
                addProperty("wpm", session.wpm)
                addProperty("wpm_category", session.wpmCategory)
                addProperty("filler_count", session.fillerCount)
                addProperty("filler_percentage", session.fillerPercentage.toInt())
                addProperty("filler_status", session.fillerStatus)
                addProperty("grammar_score", session.grammarScore)
                addProperty("original_sentence", session.feedbackOriginal)
                addProperty("corrected_sentence", session.feedbackCorrection)
                addProperty("correction_reason", session.feedbackReason)
                addProperty("summary_feedback", session.feedbackSummary)
                addProperty("overall_score", session.overallScore)
                addProperty("score_label", session.scoreLabel)
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(gson.toJson(bodyObj).toRequestBody(jsonMediaType))

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                Log.d(tag, "Insert session response (${response.code})")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to insert session to Supabase", e)
            false
        }
    }

    // 9. Get Practice Sessions from Supabase Database
    suspend fun getPracticeSessions(userId: String, token: String?): List<PracticeSession> = withContext(Dispatchers.IO) {
        try {
            val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/practice_sessions?user_id=eq.$userId&order=created_at.desc"
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .get()

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val resBody = response.body?.string() ?: return@withContext emptyList()
                val jsonArr = JsonParser.parseString(resBody).asJsonArray
                val list = mutableListOf<PracticeSession>()

                for (i in 0 until jsonArr.size()) {
                    val obj = jsonArr[i].asJsonObject
                    list.add(
                        PracticeSession(
                            id = (i + 1).toLong(),
                            title = obj.get("title")?.asString ?: "Practice Session",
                            timestamp = System.currentTimeMillis(),
                            formattedDate = obj.get("formatted_date")?.asString ?: "",
                            formattedTime = obj.get("formatted_time")?.asString ?: "",
                            durationSeconds = obj.get("duration_seconds")?.asInt ?: 0,
                            language = obj.get("language")?.asString ?: "English",
                            practiceType = obj.get("practice_type")?.asString ?: "Open Presentation",
                            audioPath = obj.get("audio_url")?.asString,
                            transcript = obj.get("transcript")?.asString ?: "",
                            wordCount = obj.get("word_count")?.asInt ?: 0,
                            wpm = obj.get("wpm")?.asInt ?: 0,
                            wpmCategory = obj.get("wpm_category")?.asString ?: "Ideal",
                            fillerCount = obj.get("filler_count")?.asInt ?: 0,
                            fillerPercentage = obj.get("filler_percentage")?.asFloat ?: 0f,
                            fillerStatus = obj.get("filler_status")?.asString ?: "Baik",
                            grammarScore = obj.get("grammar_score")?.asInt ?: 85,
                            feedbackOriginal = obj.get("original_sentence")?.asString ?: "",
                            feedbackCorrection = obj.get("corrected_sentence")?.asString ?: "",
                            feedbackReason = obj.get("correction_reason")?.asString ?: "",
                            feedbackSummary = obj.get("summary_feedback")?.asString ?: "",
                            overallScore = obj.get("overall_score")?.asInt ?: 85,
                            scoreLabel = obj.get("score_label")?.asString ?: "GREAT"
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get sessions from Supabase", e)
            emptyList()
        }
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            when {
                json.has("error_description") -> json.get("error_description").asString
                json.has("msg") -> json.get("msg").asString
                json.has("message") -> json.get("message").asString
                else -> "Gagal memproses permintaan (HTTP $code)"
            }
        } catch (e: Exception) {
            "Gagal menghubungi server Supabase (HTTP $code)"
        }
    }
}
