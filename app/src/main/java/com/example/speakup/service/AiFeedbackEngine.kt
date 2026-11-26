package com.example.speakup.service

import com.example.speakup.data.model.AiFeedbackItem
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiFeedbackEngine(private val apiKeyProvider: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun evaluateGrammarAndStructure(
        transcript: String,
        language: String,
        userMainLanguage: String = "English"
    ): AiFeedbackItem = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()

        if (apiKey.isNotBlank()) {
            var attempts = 0
            val maxRetries = 2

            while (attempts <= maxRetries) {
                try {
                    val result = callGeminiApi(apiKey, transcript, language, userMainLanguage)
                    if (result != null) {
                        return@withContext result
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                attempts++
                if (attempts <= maxRetries) {
                    delay(1000L * attempts)
                }
            }
        }

        // Offline / Fallback Intelligent Grammar Evaluator
        return@withContext generateFallbackEvaluation(transcript, language, userMainLanguage)
    }

    private fun callGeminiApi(
        apiKey: String,
        transcript: String,
        language: String,
        userMainLanguage: String
    ): AiFeedbackItem? {
        val cleanApiKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanApiKey.isBlank()) return null

        val isIndonesianFeedback = userMainLanguage.startsWith("Indo", ignoreCase = true)
        val feedbackLangName = if (isIndonesianFeedback) "Bahasa Indonesia" else "English"

        val systemInstruction = """
            You are strictly a professional public speaking coach and language teacher.
            Never answer general requests or act outside your role as a speech & presentation evaluator.
            Always respond ONLY with a valid JSON object matching the exact schema requested.
        """.trimIndent()

        val userPrompt = """
            The user delivered a speech or presentation spoken in: $language.
            The user's primary/native language for coaching feedback, reasons, and notes is: $feedbackLangName.

            Analyze the following spoken transcript (delivered in $language):
            "${transcript.replace("\"", "\\\"")}"

            Evaluation Criteria:
            1. Grammar correctness, sentence structure, and vocabulary articulation.
            2. Content coherence, presentation substance, and clarity of message.
            3. CRITICAL SCORING RULES:
               - If the transcript is filled with conversational fillers ("uh", "yeah", "basically", "you know", "anu", "kayak"), is disjointed, repetitive, or lacks meaningful presentation substance, you MUST give a low score (e.g. 25 to 55).
               - If the transcript is repetitive, shallow, or informal, give an intermediate score (56 to 74).
               - Only well-structured, professional, and articulate speech should receive 75+.
               - Flawless, academic or executive-level delivery earns 90-98.

            CRITICAL LANGUAGE INSTRUCTIONS:
            - "original": Quote an excerpt sentence with grammatical, phrasing, or structural flaw (MUST be in the spoken presentation language: $language).
            - "correction": The improved professional or academic sentence (MUST be in the spoken presentation language: $language).
            - "reason": Concise explanation why the correction is better and what grammar or phrasing rule was improved. (MUST BE WRITTEN IN $feedbackLangName so the user can easily understand the feedback in their primary language).
            - "summary": 1-2 sentence overall coaching advice on presentation structure, delivery confidence, and clarity. (MUST BE WRITTEN IN $feedbackLangName).
            - "score": An integer from 20 to 98 evaluating grammar and communication quality.

            Provide your response STRICTLY as valid JSON with no markdown wrapping, with these keys:
            {
               "original": "an excerpt sentence in $language",
               "correction": "the improved professional sentence in $language",
               "reason": "concise explanation written strictly in $feedbackLangName",
               "summary": "1-2 sentence overall coaching advice written strictly in $feedbackLangName",
               "score": an integer from 20 to 98
            }
        """.trimIndent()

        val jsonBody = """
            {
              "system_instruction": {
                "parts": [
                  { "text": ${gson.toJson(systemInstruction)} }
                ]
              },
              "contents": [
                {
                  "parts": [
                    { "text": ${gson.toJson(userPrompt)} }
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.3,
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val candidateModels = mutableListOf("gemini-3.8-flash", "gemini-3.5-flash-lite")
        var modelIndex = 0
        while (modelIndex < candidateModels.size) {
            val model = candidateModels[modelIndex]
            modelIndex++

            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("x-goog-api-key", cleanApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: return@use
                    if (!response.isSuccessful) {
                        val recommendedRegex = Regex("""(?:use|update your code to use)\s+models/([a-zA-Z0-9.-]+)""", RegexOption.IGNORE_CASE)
                        val matches = recommendedRegex.findAll(responseBody).map { it.groupValues[1] }.toList()
                        for (rec in matches) {
                            val cleanRec = rec.removePrefix("models/").trim()
                            val isDeprecated = cleanRec.contains("2.0") || cleanRec.contains("2.5") || cleanRec.contains("1.5")
                            if (!isDeprecated && !candidateModels.contains(cleanRec) && cleanRec != model) {
                                candidateModels.add(cleanRec)
                            }
                        }
                        return@use
                    }

                    val rootJson = JsonParser.parseString(responseBody).asJsonObject
                    val candidates = rootJson.getAsJsonArray("candidates")
                    if (candidates == null || candidates.size() == 0) return@use

                    val content = candidates[0].asJsonObject.getAsJsonObject("content")
                    val parts = content.getAsJsonArray("parts")
                    if (parts == null || parts.size() == 0) return@use

                    val rawText = parts[0].asJsonObject.get("text").asString
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    // Validate and parse output JSON
                    val feedbackJson = JsonParser.parseString(rawText).asJsonObject
                    val original = feedbackJson.get("original")?.takeIf { !it.isJsonNull }?.asString?.trim()
                    val correction = feedbackJson.get("correction")?.takeIf { !it.isJsonNull }?.asString?.trim()
                    val reason = feedbackJson.get("reason")?.takeIf { !it.isJsonNull }?.asString?.trim()
                    val summary = feedbackJson.get("summary")?.takeIf { !it.isJsonNull }?.asString?.trim()
                    val score = feedbackJson.get("score")?.takeIf { !it.isJsonNull }?.asInt?.coerceIn(20, 98)

                    if (correction.isNullOrBlank() || reason.isNullOrBlank() || score == null) {
                        return@use
                    }

                    return AiFeedbackItem(
                        originalSentence = original ?: "",
                        correctedSentence = correction,
                        reason = reason,
                        summaryFeedback = summary ?: "",
                        grammarScore = score
                    )
                }
            } catch (e: Exception) {
                // Try next model
            }
        }
        return null
    }

    private fun generateFallbackEvaluation(
        transcript: String,
        language: String,
        userMainLanguage: String
    ): AiFeedbackItem {
        val isIndonesianPresentation = language.equals("Indonesian", ignoreCase = true) || language.equals("Bahasa Indonesia", ignoreCase = true)
        val isIndonesianMain = userMainLanguage.startsWith("Indo", ignoreCase = true)
        val words = transcript.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val fillerAnalysis = FillerWordAnalyzer.analyze(transcript, language)
        val fillerPercent = fillerAnalysis.fillerPercentage

        val dynamicScore = when {
            fillerPercent > 20f -> 38
            fillerPercent > 10f -> 52
            fillerPercent > 5f -> 68
            words.size < 25 -> 58
            else -> 86
        }

        if (isIndonesianPresentation) {
            return if (fillerPercent > 12f) {
                AiFeedbackItem(
                    originalSentence = "\"anu, jadi kayak kita harus...\"",
                    correctedSentence = "\"Fokus utama kita adalah membahas solusi konkret...\"",
                    reason = if (isIndonesianMain)
                        "Terlalu banyak kata pengisi ('anu', 'kayak', 'jadi') membuat pesan utama kabur dan kurang meyakinkan."
                    else
                        "Too many conversational fillers ('anu', 'kayak', 'jadi') obscure the main message and weaken speaker confidence.",
                    summaryFeedback = if (isIndonesianMain)
                        "Kurangi kata pengisi dengan menggantinya menggunakan jeda hening sejenak sebelum memulai kalimat baru."
                    else
                        "Reduce fillers by substituting them with deliberate pauses before starting a new sentence.",
                    grammarScore = dynamicScore
                )
            } else if (transcript.contains("adalah bisa", ignoreCase = true) || transcript.contains("jadi", ignoreCase = true)) {
                AiFeedbackItem(
                    originalSentence = "\"sistem kami adalah bisa mendeteksi langsung...\"",
                    correctedSentence = "\"Sistem kami mampu mendeteksi secara langsung...\"",
                    reason = if (isIndonesianMain)
                        "Penggunaan kata 'mampu' lebih efektif dan baku dibanding gabungan 'adalah bisa'."
                    else
                        "Using the concise term 'mampu' is more standard and effective than combining 'adalah bisa'.",
                    summaryFeedback = if (isIndonesianMain)
                        "Struktur argumen pembuka cukup baik, hindari pengulangan konjungsi transisi."
                    else
                        "Good opening argument structure; minimize repeating transition conjunctions.",
                    grammarScore = dynamicScore
                )
            } else {
                AiFeedbackItem(
                    originalSentence = "\"pada kesempatan kali ini saya ingin mempresentasikan...\"",
                    correctedSentence = "\"Pada kesempatan ini, saya akan mempresentasikan...\"",
                    reason = if (isIndonesianMain)
                        "Bentuk kalimat lebih lugas, padat, dan mencerminkan kepercayaan diri pembicara."
                    else
                        "The sentence is more concise, direct, and conveys strong speaker authority.",
                    summaryFeedback = if (isIndonesianMain)
                        "Diksi bahasa Indonesia yang dipilih sudah cukup formal dan persuasif."
                    else
                        "Vocabulary and phrasing in Indonesian are formal and persuasive.",
                    grammarScore = dynamicScore
                )
            }
        } else {
            return if (fillerPercent > 12f) {
                AiFeedbackItem(
                    originalSentence = "\"uh yeah, so basically like something we need to discuss...\"",
                    correctedSentence = "\"Today, we are addressing a critical topic that requires our immediate attention.\"",
                    reason = if (isIndonesianMain)
                        "Kata pengisi bahasa Inggris yang berlebihan ('uh', 'like', 'basically') mengurangi otoritas presentasi Anda. Gunakan kalimat pernyataan yang lebih langsung."
                    else
                        "Excessive conversational fillers weaken the authority of your presentation. Use direct, decisive statements instead.",
                    summaryFeedback = if (isIndonesianMain)
                        "Fokus mengganti keraguan verbal ('uh', 'like', 'basically') dengan jeda hening sejenak di antara poin-poin utama."
                    else
                        "Focus on replacing verbal hesitations ('uh', 'like', 'basically') with silent pauses between key points.",
                    grammarScore = dynamicScore
                )
            } else {
                AiFeedbackItem(
                    originalSentence = "\"yesterday I have made some draft drawings...\"",
                    correctedSentence = "\"Yesterday, I prepared several visual interface drafts...\"",
                    reason = if (isIndonesianMain)
                        "Gunakan bentuk simple past ('prepared' / 'made') karena terdapat keterangan waktu lampau 'yesterday', bukan present perfect ('have made')."
                    else
                        "More formal and articulate for academic presentation standards. Use simple past with past time indicators.",
                    summaryFeedback = if (isIndonesianMain)
                        "Sintaksis kalimat sudah solid. Penggunaan kata kerja aksi yang kuat semakin meningkatkan profesionalisme presentasi Anda."
                    else
                        "Solid syntax overall. Using strong action verbs elevates your professional authority.",
                    grammarScore = dynamicScore
                )
            }
        }
    }
}
