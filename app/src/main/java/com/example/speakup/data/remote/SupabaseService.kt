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
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
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
                    val userObj = if (root.has("user") && root.get("user").isJsonObject) root.getAsJsonObject("user") else root
                    val userId = userObj?.optString("id")
                    val accessToken = root.optString("access_token")
                    val refreshToken = root.optString("refresh_token")
                    val userEmail = userObj?.optString("email") ?: email

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
                    val accessToken = root.optString("access_token")
                    val refreshToken = root.optString("refresh_token")
                    val userObj = if (root.has("user") && root.get("user").isJsonObject) root.getAsJsonObject("user") else null
                    val userId = userObj?.optString("id")
                    val userEmail = userObj?.optString("email") ?: email

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

    // 3b. Refresh Access Token
    suspend fun refreshSession(refreshToken: String): AuthResult = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) {
            return@withContext AuthResult(isSuccess = false, errorMessage = "Refresh token kosong")
        }
        try {
            val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/token?grant_type=refresh_token"
            val bodyObj = JsonObject().apply {
                addProperty("refresh_token", refreshToken)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(bodyObj).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val resBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val root = JsonParser.parseString(resBody).asJsonObject
                    val accessToken = root.optString("access_token")
                    val newRefresh = root.optString("refresh_token") ?: refreshToken
                    val userObj = root.getAsJsonObject("user")
                    AuthResult(
                        isSuccess = !accessToken.isNullOrBlank(),
                        userId = userObj?.optString("id"),
                        email = userObj?.optString("email"),
                        accessToken = accessToken,
                        refreshToken = newRefresh
                    )
                } else {
                    AuthResult(isSuccess = false, errorMessage = parseErrorMessage(resBody, response.code))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Refresh session error", e)
            AuthResult(isSuccess = false, errorMessage = e.localizedMessage)
        }
    }

    // 3c. Update Password for Authenticated User
    suspend fun updatePassword(newPassword: String, token: String?): AuthResult = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) {
            return@withContext AuthResult(isSuccess = false, errorMessage = "Sesi login Supabase tidak ditemukan")
        }
        try {
            val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/user"
            val bodyObj = JsonObject().apply {
                addProperty("password", newPassword.trim())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .put(gson.toJson(bodyObj).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val resBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    AuthResult(isSuccess = true)
                } else {
                    AuthResult(isSuccess = false, errorMessage = parseErrorMessage(resBody, response.code))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Update password error", e)
            AuthResult(isSuccess = false, errorMessage = e.localizedMessage ?: "Gagal mengubah password di server")
        }
    }

    // 3d. Send Password Recovery Email
    suspend fun recoverPassword(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank()) return@withContext false
            val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/recover"
            val bodyObj = JsonObject().apply {
                addProperty("email", cleanEmail)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(bodyObj).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(tag, "Recover password response (${response.code})")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Recover password error", e)
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
                    username = obj.optString("name") ?: "User",
                    email = obj.optString("email") ?: "",
                    challenge = obj.optString("biggest_challenge") ?: "Speaking Flawlessly",
                    mainLanguage = obj.optString("main_language") ?: "English",
                    photoUri = obj.optString("photo_url")?.ifBlank { null }
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

    data class InsertSessionResult(
        val isSuccess: Boolean,
        val remoteId: String? = null
    )

    // 8. Insert Practice Session into Supabase Database
    suspend fun insertPracticeSession(
        userId: String,
        session: PracticeSession,
        audioCloudUrl: String?,
        token: String?
    ): InsertSessionResult = withContext(Dispatchers.IO) {
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
                .addHeader("Prefer", "return=representation")
                .post(gson.toJson(bodyObj).toRequestBody(jsonMediaType))

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val resBody = response.body?.string() ?: ""
                Log.d(tag, "Insert session response (${response.code})")
                if (!response.isSuccessful) {
                    return@withContext InsertSessionResult(isSuccess = false)
                }
                val remoteId = try {
                    val arr = JsonParser.parseString(resBody).asJsonArray
                    if (arr.size() > 0) arr[0].asJsonObject.optString("id") else null
                } catch (_: Exception) {
                    null
                }
                InsertSessionResult(isSuccess = true, remoteId = remoteId)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to insert session to Supabase", e)
            InsertSessionResult(isSuccess = false)
        }
    }

    // 9. Get Practice Sessions from Supabase Database (null indicates request error)
    suspend fun getPracticeSessions(userId: String, token: String?): List<PracticeSession>? = withContext(Dispatchers.IO) {
        try {
            val encodedUserId = URLEncoder.encode(userId, "UTF-8")
            val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/practice_sessions?user_id=eq.$encodedUserId&order=created_at.desc"
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
                val list = mutableListOf<PracticeSession>()
                val baseNow = System.currentTimeMillis()

                for (i in 0 until jsonArr.size()) {
                    val obj = jsonArr[i].asJsonObject
                    val remoteId = obj.optString("id")
                    val createdAtStr = obj.optString("created_at")
                    val parsedTimestamp = parseIsoTimestamp(createdAtStr) ?: (baseNow - (i * 1000L))
                    val wpmCategory = obj.optString("wpm_category") ?: "Optimal (Ideal)"
                    val deliveryStatus = obj.optString("delivery_status") ?: wpmCategory

                    list.add(
                        PracticeSession(
                            id = -(i + 1).toLong(),
                            remoteId = remoteId,
                            title = obj.optString("title") ?: "Practice Session",
                            timestamp = parsedTimestamp,
                            formattedDate = obj.optString("formatted_date") ?: "",
                            formattedTime = obj.optString("formatted_time") ?: "",
                            durationSeconds = obj.optInt("duration_seconds") ?: 0,
                            language = obj.optString("language") ?: "English",
                            practiceType = obj.optString("practice_type") ?: "Open Presentation (No Limit)",
                            audioPath = obj.optString("audio_url")?.ifBlank { null },
                            transcript = obj.optString("transcript") ?: "",
                            wordCount = obj.optInt("word_count") ?: 0,
                            wpm = obj.optInt("wpm") ?: 0,
                            wpmCategory = wpmCategory,
                            fillerCount = obj.optInt("filler_count") ?: 0,
                            fillerPercentage = obj.optFloat("filler_percentage") ?: 0f,
                            fillerStatus = obj.optString("filler_status") ?: "Baik",
                            fillerDetailsJson = obj.optString("filler_details_json") ?: "[]",
                            grammarScore = obj.optInt("grammar_score") ?: 85,
                            deliveryStatus = deliveryStatus,
                            feedbackOriginal = obj.optString("original_sentence") ?: "",
                            feedbackCorrection = obj.optString("corrected_sentence") ?: "",
                            feedbackReason = obj.optString("correction_reason") ?: "",
                            feedbackSummary = obj.optString("summary_feedback") ?: "",
                            overallScore = obj.optInt("overall_score") ?: 85,
                            scoreLabel = obj.optString("score_label") ?: "GREAT"
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get sessions from Supabase", e)
            null
        }
    }

    // 10. Update Practice Session Title in Supabase
    suspend fun updatePracticeSessionTitle(
        remoteId: String,
        userId: String,
        newTitle: String,
        token: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedId = URLEncoder.encode(remoteId, "UTF-8")
            val encodedUserId = URLEncoder.encode(userId, "UTF-8")
            val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/practice_sessions?id=eq.$encodedId&user_id=eq.$encodedUserId"
            val bodyObj = JsonObject().apply {
                addProperty("title", newTitle)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(gson.toJson(bodyObj).toRequestBody(jsonMediaType))

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                Log.d(tag, "Update session title response (${response.code})")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update session title in Supabase", e)
            false
        }
    }

    // 11. Delete Practice Session from Supabase
    suspend fun deletePracticeSession(
        remoteId: String,
        userId: String,
        token: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedId = URLEncoder.encode(remoteId, "UTF-8")
            val encodedUserId = URLEncoder.encode(userId, "UTF-8")
            val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/practice_sessions?id=eq.$encodedId&user_id=eq.$encodedUserId"
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Prefer", "return=minimal")
                .delete()

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                Log.d(tag, "Delete session response (${response.code})")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete session from Supabase", e)
            false
        }
    }

    private fun parseIsoTimestamp(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun JsonObject.optString(key: String): String? {
        val el = get(key) ?: return null
        return if (el.isJsonNull) null else el.asString
    }

    private fun JsonObject.optInt(key: String): Int? {
        val el = get(key) ?: return null
        return if (el.isJsonNull) null else try { el.asInt } catch (_: Exception) { null }
    }

    private fun JsonObject.optFloat(key: String): Float? {
        val el = get(key) ?: return null
        return if (el.isJsonNull) null else try { el.asFloat } catch (_: Exception) { null }
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
