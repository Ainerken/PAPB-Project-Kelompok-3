package com.example.speakup.service

import kotlin.math.roundToInt

object PacingCalculator {

    data class PacingResult(
        val wpm: Int,
        val category: String, // "Optimal (Ideal)", "Terlalu Lambat", "Terlalu Cepat"
        val advice: String,
        val deliveryStatus: String, // e.g. "Steady & Composed"
        val deliveryStatusIndonesian: String, // e.g. "Stabil & Tenang"
        val pacingScore: Int // e.g. 90
    )

    fun calculate(wordCount: Int, durationSeconds: Int, userMainLanguage: String = "English"): PacingResult {
        val durationMinutes = (durationSeconds.coerceAtLeast(1).toFloat() / 60.0f)
        val calculatedWpm = (wordCount.toFloat() / durationMinutes).roundToInt().coerceIn(40, 260)
        val isIndonesian = userMainLanguage.startsWith("Indo", ignoreCase = true)

        val category: String
        val advice: String
        val deliveryStatus: String
        val deliveryStatusId: String
        val score: Int

        when {
            calculatedWpm < 80 -> {
                category = if (isIndonesian) "Sangat Lambat" else "Very Slow"
                advice = if (isIndonesian)
                    "Tempo bicara sangat lambat (<80 WPM). Tingkatkan ritme bicara Anda agar materi tidak terasa monoton atau terputus-putus."
                else
                    "Speaking pace is very slow (<80 WPM). Pick up your tempo to keep listeners engaged."
                deliveryStatus = "Halting & Disjointed"
                deliveryStatusId = "Terputus-putus & Ragu"
                score = (25 + (calculatedWpm / 80f * 24)).roundToInt().coerceIn(25, 49)
            }
            calculatedWpm in 80..99 -> {
                category = if (isIndonesian) "Terlalu Lambat" else "Too Slow"
                advice = if (isIndonesian)
                    "Tempo bicara Anda cenderung lambat (<110 WPM). Tingkatkan ritme bicara Anda dengan artikulasi yang lebih mantap agar audiens tetap fokus."
                else
                    "Your speaking pace is somewhat slow (<110 WPM). Increase your cadence with firmer articulation."
                deliveryStatus = "Too Slow"
                deliveryStatusId = "Terlalu Lambat"
                score = (50 + ((calculatedWpm - 80) / 20f * 17)).roundToInt().coerceIn(50, 67)
            }
            calculatedWpm in 100..109 -> {
                category = if (isIndonesian) "Agak Lambat" else "Slightly Slow"
                advice = if (isIndonesian)
                    "Kecepatan bicara sedikit di bawah tempo optimal (<110 WPM). Anda bisa sedikit mempercepat tempo dan mengurangi jeda ragu."
                else
                    "Speaking pace is slightly below optimal (<110 WPM). Gradually quicken your transitions between thoughts."
                deliveryStatus = "Slow & Hesitant"
                deliveryStatusId = "Lambat & Ragu"
                score = (68 + ((calculatedWpm - 100) / 10f * 11)).roundToInt().coerceIn(68, 79)
            }
            calculatedWpm in 110..150 -> {
                category = "Optimal (Ideal)"
                advice = if (isIndonesian)
                    "Kecepatan berbicara Anda sangat ideal dan nyaman didengarkan (110-150 WPM). Pendengar dapat mencerna materi dengan baik."
                else
                    "Your pace is in the sweet spot (110-150 WPM). Excellent clarity and listener retention."
                deliveryStatus = "Steady & Composed"
                deliveryStatusId = "Stabil & Tenang"
                val distFromCenter = kotlin.math.abs(calculatedWpm - 130)
                score = (100 - (distFromCenter * 0.6f)).roundToInt().coerceIn(88, 100)
            }
            calculatedWpm in 151..170 -> {
                category = if (isIndonesian) "Agak Cepat" else "Slightly Fast"
                advice = if (isIndonesian)
                    "Tempo bicara sedikit cepat (>150 WPM). Berikan jeda nafas 1 detik di setiap akhir kalimat penting."
                else
                    "Tempo is slightly brisk (>150 WPM). Insert 1-second strategic pauses after key points."
                deliveryStatus = "Fast & Energetic"
                deliveryStatusId = "Cepat & Berenergi"
                score = (85 - ((calculatedWpm - 151) / 20f * 10)).roundToInt().coerceIn(75, 85)
            }
            calculatedWpm in 171..195 -> {
                category = if (isIndonesian) "Terlalu Cepat" else "Too Fast"
                advice = if (isIndonesian)
                    "Tempo bicara Anda terlalu cepat (>170 WPM). Audiens akan kesulitan mencerna poin-poin utama presentasi Anda."
                else
                    "Speaking tempo is overly rushed (>170 WPM). Slow down so listeners can process key ideas."
                deliveryStatus = "Rushed"
                deliveryStatusId = "Terburu-buru"
                score = (70 - ((calculatedWpm - 171) / 25f * 18)).roundToInt().coerceIn(52, 70)
            }
            else -> {
                category = if (isIndonesian) "Sangat Cepat" else "Very Fast"
                advice = if (isIndonesian)
                    "Tempo bicara Anda sangat cepat (>195 WPM). Kurangi kecepatan agar artikulasi kata tetap jelas dan terstruktur."
                else
                    "Speech pace is excessively fast (>195 WPM). Deliberately pause and pace your articulation."
                deliveryStatus = "Too Fast"
                deliveryStatusId = "Terlalu Cepat"
                score = (50 - ((calculatedWpm - 196) * 0.4f)).roundToInt().coerceIn(25, 50)
            }
        }

        return PacingResult(
            wpm = calculatedWpm,
            category = category,
            advice = advice,
            deliveryStatus = deliveryStatus,
            deliveryStatusIndonesian = deliveryStatusId,
            pacingScore = score
        )
    }
}
