package com.example.speakup

import com.example.speakup.service.FillerWordAnalyzer
import com.example.speakup.service.PacingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakUpLogicTest {

    @Test
    fun pacingCalculator_matchesProposalThresholds() {
        val slow = PacingCalculator.calculate(wordCount = 90, durationSeconds = 60, userMainLanguage = "Indonesian")
        assertEquals(90, slow.wpm)
        assertEquals("Terlalu Lambat", slow.category)

        val boundaryLowerIdeal = PacingCalculator.calculate(wordCount = 110, durationSeconds = 60, userMainLanguage = "Indonesian")
        assertEquals(110, boundaryLowerIdeal.wpm)
        assertEquals("Optimal (Ideal)", boundaryLowerIdeal.category)

        val boundaryUpperIdeal = PacingCalculator.calculate(wordCount = 150, durationSeconds = 60, userMainLanguage = "English")
        assertEquals(150, boundaryUpperIdeal.wpm)
        assertEquals("Optimal (Ideal)", boundaryUpperIdeal.category)

        val fast = PacingCalculator.calculate(wordCount = 151, durationSeconds = 60, userMainLanguage = "Indonesian")
        assertEquals(151, fast.wpm)
        assertEquals("Agak Cepat", fast.category)
    }

    @Test
    fun fillerWordAnalyzer_detectsAndScoresCleanlyWithoutOverlaps() {
        val en = FillerWordAnalyzer.analyze(
            transcript = "Um today I mean you know we actually built something great",
            language = "English"
        )
        assertEquals(4, en.fillerCount)
        assertEquals("Berlebihan", en.status)
        var prevEnd = 0
        for (m in en.matches) {
            assertTrue(m.startIndex >= prevEnd)
            prevEnd = m.endIndex
        }

        val cleanId = FillerWordAnalyzer.analyze(
            transcript = "Selamat pagi semuanya saya akan mempresentasikan hasil proyek kami hari ini dengan jelas",
            language = "Indonesian"
        )
        assertEquals(0, cleanId.fillerCount)
        assertEquals("Baik", cleanId.status)
        assertEquals(100, cleanId.fillerScore)
    }
}
