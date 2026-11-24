package com.example.speakup.service

import com.example.speakup.data.model.FillerStat
import com.example.speakup.data.model.FillerWordMatch
import com.google.gson.Gson
import java.util.Locale
import java.util.regex.Pattern

object FillerWordAnalyzer {

    val INDONESIAN_FILLERS = listOf(
        "apa namanya",
        "apa ya",
        "eeee",
        "ehm",
        "umm",
        "anu",
        "kayak",
        "terus",
        "mungkin",
        "eh",
        "um",
        "lah",
        "kan"
    )

    val ENGLISH_FILLERS = listOf(
        "you know",
        "i mean",
        "sort of",
        "kind of",
        "actually",
        "basically",
        "literally",
        "right",
        "well",
        "like",
        "umm",
        "um",
        "uh",
        "so"
    )

    data class AnalysisResult(
        val fillerCount: Int,
        val totalWordCount: Int,
        val fillerPercentage: Float,
        val status: String, // "Baik" or "Berlebihan"
        val fillerStats: List<FillerStat>,
        val fillerDetailsJson: String,
        val matches: List<FillerWordMatch>
    )

    fun analyze(transcript: String, language: String): AnalysisResult {
        if (transcript.isBlank()) {
            return AnalysisResult(
                fillerCount = 0,
                totalWordCount = 0,
                fillerPercentage = 0f,
                status = "Baik",
                fillerStats = emptyList(),
                fillerDetailsJson = "[]",
                matches = emptyList()
            )
        }

        val dictionary = if (language.equals("Indonesian", ignoreCase = true) || language.equals("Bahasa Indonesia", ignoreCase = true)) {
            INDONESIAN_FILLERS
        } else {
            ENGLISH_FILLERS
        }

        val matches = mutableListOf<FillerWordMatch>()
        val statMap = mutableMapOf<String, Int>()

        // Search for occurrences preserving word boundaries in original text
        for (filler in dictionary) {
            val patternString = "\\b" + Pattern.quote(filler) + "\\b"
            val pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(transcript)

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val matchedText = transcript.substring(start, end)
                matches.add(FillerWordMatch(matchedText, start, end))
                val key = filler.lowercase(Locale.ROOT)
                statMap[key] = (statMap[key] ?: 0) + 1
            }
        }

        // Sort matches by start index
        matches.sortBy { it.startIndex }

        val words = transcript.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val totalWords = words.size.coerceAtLeast(1)
        val fillerCount = matches.size

        val percentage = (fillerCount.toFloat() / totalWords.toFloat()) * 100f
        val roundedPercent = (percentage * 10).toInt() / 10f

        // Threshold from PDF report: <= 3% or <= 3 words -> "Baik", otherwise "Berlebihan"
        val status = if (fillerCount <= 3 || roundedPercent <= 3.0f) {
            "Baik"
        } else {
            "Berlebihan"
        }

        val stats = statMap.map { FillerStat(it.key, it.value) }.sortedByDescending { it.count }
        val json = Gson().toJson(stats)

        return AnalysisResult(
            fillerCount = fillerCount,
            totalWordCount = totalWords,
            fillerPercentage = roundedPercent,
            status = status,
            fillerStats = stats,
            fillerDetailsJson = json,
            matches = matches
        )
    }
}
