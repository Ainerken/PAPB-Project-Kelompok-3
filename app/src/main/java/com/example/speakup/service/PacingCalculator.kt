package com.example.speakup.service

import kotlin.math.roundToInt

object PacingCalculator {

    data class PacingResult(
        val wpm: Int,
        val category: String, // "Optimal (Ideal)", "Terlalu Lambat", "Terlalu Cepat"
        val advice: String,
        val deliveryStatus: String, // e.g. "Steady & Composed"
        val pacingScore: Int // e.g. 90
    )

    fun calculate(wordCount: Int, durationSeconds: Int): PacingResult {
        val durationMinutes = (durationSeconds.coerceAtLeast(1).toFloat() / 60.0f)
        val calculatedWpm = (wordCount.toFloat() / durationMinutes).roundToInt().coerceIn(40, 260)

        val category: String
        val advice: String
        val deliveryStatus: String
        val score: Int

        when {
            calculatedWpm < 80 -> {
                category = "Sangat Lambat"
                advice = "Tempo bicara sangat lambat (<80 WPM). Tingkatkan ritme bicara Anda secara signifikan agar materi tidak terasa monoton atau terputus-putus."
                deliveryStatus = "Halting & Disjointed"
                score = (25 + (calculatedWpm / 80f * 24)).roundToInt().coerceIn(25, 49)
            }
            calculatedWpm in 80..99 -> {
                category = "Terlalu Lambat"
                advice = "Tempo bicara Anda cenderung lambat. Tingkatkan ritme bicara Anda dengan artikulasi yang lebih mantap agar audiens tetap fokus."
                deliveryStatus = "Too Slow"
                score = (50 + ((calculatedWpm - 80) / 20f * 17)).roundToInt().coerceIn(50, 67)
            }
            calculatedWpm in 100..114 -> {
                category = "Agak Lambat"
                advice = "Kecepatan bicara sedikit di bawah tempo optimal. Anda bisa sedikit mempercepat tempo dan mengurangi jeda ragu."
                deliveryStatus = "Slow & Hesitant"
                score = (68 + ((calculatedWpm - 100) / 15f * 11)).roundToInt().coerceIn(68, 79)
            }
            calculatedWpm in 115..155 -> {
                category = "Optimal (Ideal)"
                advice = "Kecepatan berbicara Anda sangat ideal dan nyaman didengarkan (115-155 WPM). Pendengar dapat mencerna materi dengan baik."
                deliveryStatus = "Steady & Composed"
                val distFromCenter = kotlin.math.abs(calculatedWpm - 135)
                score = (100 - (distFromCenter * 0.5f)).roundToInt().coerceIn(88, 100)
            }
            calculatedWpm in 156..170 -> {
                category = "Agak Cepat"
                advice = "Tempo bicara sedikit cepat namun masih dapat dipahami. Berikan jeda nafas 1 detik di setiap akhir kalimat penting."
                deliveryStatus = "Fast & Energetic"
                score = (85 - ((calculatedWpm - 156) / 15f * 10)).roundToInt().coerceIn(75, 85)
            }
            calculatedWpm in 171..195 -> {
                category = "Terlalu Cepat"
                advice = "Tempo bicara Anda terlalu cepat (>170 WPM). Audiens akan kesulitan mencerna poin-poin utama presentasi Anda."
                deliveryStatus = "Rushed"
                score = (70 - ((calculatedWpm - 171) / 25f * 18)).roundToInt().coerceIn(52, 70)
            }
            else -> {
                category = "Sangat Cepat"
                advice = "Tempo bicara Anda sangat cepat (>195 WPM). Kurangi kecepatan agar artikulasi kata tetap jelas dan terstruktur."
                deliveryStatus = "Too Fast"
                score = (50 - ((calculatedWpm - 196) * 0.4f)).roundToInt().coerceIn(25, 50)
            }
        }

        return PacingResult(
            wpm = calculatedWpm,
            category = category,
            advice = advice,
            deliveryStatus = deliveryStatus,
            pacingScore = score
        )
    }
}
