package com.example.speakup.service

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class SpeechToTextEngine(private val context: Context) {

    private val tag = "SpeakUpSTT"

    data class SttResult(
        val isSuccess: Boolean,
        val transcript: String,
        val wordCount: Int,
        val isRealSpeech: Boolean = true,
        val errorCode: String? = null,
        val errorMessage: String? = null
    )

    data class CloudSttResult(
        val isSuccess: Boolean,
        val transcript: String? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun transcribeAudio(
        audioFile: File?,
        language: String,
        durationSeconds: Int,
        providedTranscript: String? = null,
        apiKey: String? = null,
        allowSimulationFallback: Boolean = false
    ): SttResult = withContext(Dispatchers.IO) {
        // Validate audio duration
        if (durationSeconds < 5) {
            return@withContext SttResult(
                isSuccess = false,
                transcript = "",
                wordCount = 0,
                errorCode = "TOO_SHORT",
                errorMessage = "Rekaman terlalu pendek (minimal 5 detik)"
            )
        }

        // 1. If on-device SpeechRecognizer captured actual speech from the microphone
        if (!providedTranscript.isNullOrBlank()) {
            val cleaned = cleanTranscript(providedTranscript)
            val words = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.isNotEmpty()) {
                Log.d(tag, "Using real on-device recognized speech: $cleaned")
                return@withContext SttResult(
                    isSuccess = true,
                    transcript = cleaned,
                    wordCount = words.size,
                    isRealSpeech = true
                )
            }
        }

        // 2. If Gemini API key is configured, transcribe audio file via Gemini Multimodal Audio
        val cleanApiKey = apiKey?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
        if (!cleanApiKey.isNullOrBlank()) {
            if (audioFile == null || !audioFile.exists() || audioFile.length() <= 0) {
                Log.e(tag, "Audio file is missing or 0 bytes: ${audioFile?.absolutePath}")
                if (!allowSimulationFallback) {
                    return@withContext SttResult(
                        isSuccess = false,
                        transcript = "",
                        wordCount = 0,
                        errorCode = "AUDIO_EMPTY",
                        errorMessage = "File rekaman kosong atau mikrofon tidak merekam suara."
                    )
                }
            } else {
                Log.d(tag, "Sending audio file (${audioFile.length()} bytes) to Gemini Cloud STT...")
                val cloudResult = transcribeViaGeminiAudio(cleanApiKey, audioFile, language)
                if (cloudResult.isSuccess && !cloudResult.transcript.isNullOrBlank()) {
                    val cleaned = cleanTranscript(cloudResult.transcript)
                    val words = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    Log.d(tag, "Gemini audio transcription success (${words.size} words): $cleaned")
                    return@withContext SttResult(
                        isSuccess = true,
                        transcript = cleaned,
                        wordCount = words.size,
                        isRealSpeech = true
                    )
                }

                // If Gemini specifically reported silence or an error, return that exact status!
                if (!allowSimulationFallback) {
                    return@withContext SttResult(
                        isSuccess = false,
                        transcript = "",
                        wordCount = 0,
                        errorCode = cloudResult.errorCode ?: "GEMINI_ERROR",
                        errorMessage = cloudResult.errorMessage ?: "Gemini AI belum berhasil mentranskripsi audio."
                    )
                }
            }
        }

        // 3. If neither recognizer heard anything nor API key is configured
        if (!allowSimulationFallback) {
            return@withContext SttResult(
                isSuccess = false,
                transcript = "",
                wordCount = 0,
                isRealSpeech = false,
                errorCode = "NO_SPEECH_NO_API_KEY",
                errorMessage = "Belum ada suara yang tertangkap oleh pengenal suara bawaan dan Gemini API Key belum diatur."
            )
        }

        // 4. Explicitly permitted simulation fallback for testing
        val isIndonesian = language.equals("Indonesian", ignoreCase = true) || language.equals("Bahasa Indonesia", ignoreCase = true)
        val sampleList = if (isIndonesian) sampleIndonesianTranscripts else sampleEnglishTranscripts
        val baseSample = sampleList[(durationSeconds % sampleList.size)]
        val cleaned = cleanTranscript(baseSample)
        val words = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }

        SttResult(
            isSuccess = true,
            transcript = cleaned,
            wordCount = words.size,
            isRealSpeech = false
        )
    }

    private fun transcribeViaGeminiAudio(apiKey: String, audioFile: File, language: String): CloudSttResult {
        val audioBytes = try {
            audioFile.readBytes()
        } catch (e: Exception) {
            Log.e(tag, "Failed to read audio file", e)
            return CloudSttResult(isSuccess = false, errorCode = "FILE_READ_ERROR", errorMessage = "Gagal membaca file rekaman suara.")
        }

        if (audioBytes.isEmpty()) {
            return CloudSttResult(isSuccess = false, errorCode = "AUDIO_EMPTY", errorMessage = "File rekaman berukuran 0 byte.")
        }

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val isIndo = language.startsWith("Indo", ignoreCase = true)
        val prompt = if (isIndo) {
            "Transkripsikan rekaman audio suara ini kata per kata ke dalam Bahasa Indonesia secara akurat dan verbatim. Pertahankan kata-kata pengisi (filler words) yang diucapkan seperti 'eh', 'umm', 'anu', 'kayak', 'nggg'. Tampilkan HANYA teks transkrip polos tanpa pengantar atau markdown."
        } else {
            "Please transcribe this speech audio recording verbatim into English. Accurately preserve any spoken filler words such as 'um', 'uh', 'you know', 'like', 'ah'. Output ONLY the plain transcription text with no introductory text or markdown formatting."
        }

        val candidateModels = mutableListOf("gemini-3.8-flash", "gemini-3.5-flash-lite")
        val candidateMimeTypes = listOf("audio/mp4", "audio/m4a")

        var lastHttpCode: Int? = null
        var lastErrorMessage: String? = null

        var modelIndex = 0
        while (modelIndex < candidateModels.size) {
            val model = candidateModels[modelIndex]
            modelIndex++

            for (mimeType in candidateMimeTypes) {
                try {
                    val inlineData = JsonObject().apply {
                        addProperty("mime_type", mimeType)
                        addProperty("data", base64Audio)
                    }
                    val partAudio = JsonObject().apply {
                        add("inline_data", inlineData)
                    }
                    val partText = JsonObject().apply {
                        addProperty("text", prompt)
                    }
                    val partsArray = JsonArray().apply {
                        add(partAudio)
                        add(partText)
                    }
                    val contentObj = JsonObject().apply {
                        add("parts", partsArray)
                    }
                    val contentsArray = JsonArray().apply {
                        add(contentObj)
                    }
                    val rootObj = JsonObject().apply {
                        add("contents", contentsArray)
                        add("generationConfig", JsonObject().apply {
                            addProperty("temperature", 0.1)
                        })
                    }

                    val jsonBody = gson.toJson(rootObj)
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("x-goog-api-key", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        lastHttpCode = response.code
                        val bodyStr = response.body?.string() ?: ""

                        if (!response.isSuccessful) {
                            Log.w(tag, "Gemini STT ($model, $mimeType) returned HTTP ${response.code}: $bodyStr")
                            val parsedErr = try {
                                val errJson = JsonParser.parseString(bodyStr).asJsonObject
                                errJson.getAsJsonObject("error")?.get("message")?.asString
                            } catch (e: Exception) {
                                null
                            } ?: "HTTP ${response.code}"
                            lastErrorMessage = parsedErr

                            // Only discover the *recommended replacement* model, not the deprecated one mentioned in the error
                            val recommendedRegex = Regex("""(?:use|update your code to use)\s+models/([a-zA-Z0-9.-]+)""", RegexOption.IGNORE_CASE)
                            val matches = recommendedRegex.findAll(bodyStr).map { it.groupValues[1] }.toList()
                            for (rec in matches) {
                                val cleanRec = rec.removePrefix("models/").trim()
                                val isDeprecated = cleanRec.contains("2.0") || cleanRec.contains("2.5") || cleanRec.contains("1.5")
                                if (!isDeprecated && !candidateModels.contains(cleanRec) && cleanRec != model) {
                                    Log.i(tag, "Discovered newer recommended Gemini model: $cleanRec")
                                    candidateModels.add(cleanRec)
                                }
                            }

                            return@use // try next combination
                        }

                        val root = JsonParser.parseString(bodyStr).asJsonObject
                        val candidates = root.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            val candidate = candidates[0].asJsonObject
                            val content = candidate.getAsJsonObject("content")
                            val parts = content?.getAsJsonArray("parts")
                            if (parts != null && parts.size() > 0) {
                                val text = parts[0].asJsonObject.get("text")?.asString?.trim()
                                if (!text.isNullOrBlank()) {
                                    val silenceMarkers = listOf("[silence]", "[hening]", "no speech detected", "tidak ada ucapan")
                                    if (silenceMarkers.any { text.equals(it, ignoreCase = true) }) {
                                        return CloudSttResult(
                                            isSuccess = false,
                                            errorCode = "AUDIO_SILENT",
                                            errorMessage = "Audio yang terekam hening (tidak ada suara ucapan yang terdeteksi)."
                                        )
                                    }
                                    return CloudSttResult(isSuccess = true, transcript = text)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Gemini call exception ($model, $mimeType)", e)
                    lastErrorMessage = e.localizedMessage ?: "Gagal menghubungi Gemini server"
                }
            }
        }

        val friendlyError = when (lastHttpCode) {
            400 -> "Permintaan ditolak Google (HTTP 400): $lastErrorMessage"
            401, 403 -> "Gemini API Key tidak valid atau belum diaktifkan (HTTP $lastHttpCode): $lastErrorMessage"
            429 -> "Batas kuota Gemini API tercapai (HTTP 429). Silakan tunggu sebentar."
            else -> lastErrorMessage ?: "Gagal mentranskripsi audio dengan Gemini AI."
        }

        return CloudSttResult(
            isSuccess = false,
            errorCode = "GEMINI_ERROR",
            errorMessage = friendlyError
        )
    }

    fun cleanTranscript(raw: String): String {
        return raw.trim().replace("\\s+".toRegex(), " ")
    }

    private val sampleEnglishTranscripts = listOf(
        "Hi, my name is Dika, and I am applying for the developer position at your company. I have, um, two years of experience building mobile apps. During my study, um, I built multiple projects and uh, mastered Android development.",
        "Good morning everyone. Today, um, I want to present our innovative product. We believe that, uh, public speaking is a crucial skill for every student, you know, and our application helps bridge that gap effectively."
    )

    private val sampleIndonesianTranscripts = listOf(
        "Halo semuanya, selamat pagi. Pada kesempatan kali ini saya ingin mempresentasikan proyek akhir kami. Jadi, anu, aplikasi SpeakUp ini dirancang untuk membantu mahasiswa melatih kemampuan public speaking secara mandiri.",
        "Selamat siang rekan-rekan. Terima kasih atas kehadirannya hari ini. Eh, kami telah mengembangkan sebuah sistem pintar yang dapat, ummm, mendeteksi kata pengisi dan memberikan saran tata bahasa secara langsung."
    )
}
