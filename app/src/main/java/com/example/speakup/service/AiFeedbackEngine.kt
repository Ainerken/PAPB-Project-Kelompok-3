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
        language: String
    ): AiFeedbackItem = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()

        if (apiKey.isNotBlank()) {
            var attempts = 0
            val maxRetries = 2

            while (attempts <= maxRetries) {
                try {
                    val result = callGeminiApi(apiKey, transcript, language)
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
        return@withContext generateFallbackEvaluation(transcript, language)
    }

    private fun callGeminiApi(apiKey: String, transcript: String, language: String): AiFeedbackItem? {
        val cleanApiKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanApiKey.isBlank()) return null

        val prompt = """
            You are an expert, objective public speaking and communication evaluator.
            Analyze the following spoken transcript in $language:
            "${transcript.replace("\"", "\\\"")}"

            Evaluation Criteria:
            1. Grammar correctness, sentence structure, and vocabulary articulation.
            2. Content coherence, presentation substance, and clarity of message.
            3. CRITICAL SCORING RULES:
               - If the transcript is filled with conversational fillers ("uh", "yeah", "basically", "you know"), is disjointed, repetitive, or lacks meaningful presentation substance, you MUST give a low score (e.g. 25 to 55).
               - If the transcript is repetitive, shallow, or informal, give an intermediate score (56 to 74).
               - Only well-structured, professional, and articulate speech should receive 75+.
               - Flawless, academic or executive-level delivery earns 90-98.

            Provide your response STRICTLY as valid JSON with no markdown wrapping, with these keys:
            {
               "original": "an excerpt sentence with grammatical, phrasing, or structural flaw",
               "correction": "the improved professional or academic sentence",
               "reason": "concise explanation why the correction is better",
               "summary": "1-2 sentence overall coaching advice on presentation structure and clarity",
               "score": an integer from 20 to 98 evaluating grammar and communication quality
            }
        """.trimIndent()

        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": ${gson.toJson(prompt)} }
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
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanApiKey"
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

                    val rawText = parts[0].asJsonObject.get("text").asString.trim()

                    // Parse output JSON
                    val feedbackJson = JsonParser.parseString(rawText).asJsonObject
                    val original = feedbackJson.get("original")?.asString ?: "draft drawings..."
                    val correction = feedbackJson.get("correction")?.asString ?: "prepared several visual interface drafts..."
                    val reason = feedbackJson.get("reason")?.asString ?: "More formal and articulate for presentation standards."
                    val summary = feedbackJson.get("summary")?.asString ?: "Well structured delivery with clear communication."
                    val score = feedbackJson.get("score")?.asInt ?: 92

                    return AiFeedbackItem(
                        originalSentence = original,
                        correctedSentence = correction,
                        reason = reason,
                        summaryFeedback = summary,
                        grammarScore = score
                    )
                }
            } catch (e: Exception) {
                // Try next model
            }
        }
        return null
    }

    private fun generateFallbackEvaluation(transcript: String, language: String): AiFeedbackItem {
        val isIndonesian = language.equals("Indonesian", ignoreCase = true) || language.equals("Bahasa Indonesia", ignoreCase = true)
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

        if (isIndonesian) {
            return if (fillerPercent > 12f) {
                AiFeedbackItem(
                    originalSentence = "\"anu, jadi kayak kita harus...\"",
                    correctedSentence = "\"Fokus utama kita adalah membahas solusi konkret...\"",
                    reason = "Terlalu banyak kata pengisi ('anu', 'kayak', 'jadi') membuat pesan utama kabur dan kurang meyakinkan.",
                    summaryFeedback = "Kurangi kata pengisi dengan menggantinya menggunakan jeda hening sejenak sebelum memulai kalimat baru.",
                    grammarScore = dynamicScore
                )
            } else if (transcript.contains("adalah bisa", ignoreCase = true) || transcript.contains("jadi", ignoreCase = true)) {
                AiFeedbackItem(
                    originalSentence = "\"sistem kami adalah bisa mendeteksi langsung...\"",
                    correctedSentence = "\"Sistem kami mampu mendeteksi secara langsung...\"",
                    reason = "Penggunaan kata 'mampu' lebih efektif dan baku dibanding gabungan 'adalah bisa'.",
                    summaryFeedback = "Struktur argumen pembuka cukup baik, hindari pengulangan konjungsi transisi.",
                    grammarScore = dynamicScore
                )
            } else {
                AiFeedbackItem(
                    originalSentence = "\"pada kesempatan kali ini saya ingin mempresentasikan...\"",
                    correctedSentence = "\"Pada kesempatan ini, saya akan mempresentasikan...\"",
                    reason = "Bentuk kalimat lebih lugas, padat, dan mencerminkan kepercayaan diri pembicara.",
                    summaryFeedback = "Diksi bahasa Indonesia yang dipilih sudah cukup formal dan persuasif.",
                    grammarScore = dynamicScore
                )
            }
        } else {
            return if (fillerPercent > 12f) {
                AiFeedbackItem(
                    originalSentence = "\"uh yeah, so basically like something we need to discuss...\"",
                    correctedSentence = "\"Today, we are addressing a critical topic that requires our immediate attention.\"",
                    reason = "Excessive conversational fillers weaken the authority of your presentation. Use direct, decisive statements instead.",
                    summaryFeedback = "Focus on replacing verbal hesitations ('uh', 'like', 'basically') with silent pauses between key points.",
                    grammarScore = dynamicScore
                )
            } else {
                AiFeedbackItem(
                    originalSentence = "\"yesterday I have made some draft drawings...\"",
                    correctedSentence = "\"Yesterday, I prepared several visual interface drafts...\"",
                    reason = "More formal and articulate for academic presentation standards.",
                    summaryFeedback = "Solid syntax overall. Using strong action verbs elevates your professional authority.",
                    grammarScore = dynamicScore
                )
            }
        }
    }
}
