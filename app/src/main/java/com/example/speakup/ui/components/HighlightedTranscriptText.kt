package com.example.speakup.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.service.FillerWordAnalyzer
import com.example.speakup.ui.theme.FillerHighlightBg
import com.example.speakup.ui.theme.FillerHighlightText
import com.example.speakup.ui.theme.PrimaryBlue
import com.example.speakup.ui.theme.TextPrimary

@Composable
fun HighlightedTranscriptText(
    transcript: String,
    language: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val annotatedText = remember(transcript, language) {
        val analysis = FillerWordAnalyzer.analyze(transcript, language)
        val sortedMatches = analysis.matches

        buildAnnotatedString {
            if (sortedMatches.isEmpty()) {
                append(transcript)
            } else {
                var currentIndex = 0
                for (match in sortedMatches) {
                    if (match.startIndex >= currentIndex && match.startIndex < transcript.length) {
                        append(transcript.substring(currentIndex, match.startIndex))

                        val end = match.endIndex.coerceAtMost(transcript.length)
                        pushStyle(
                            SpanStyle(
                                background = FillerHighlightBg,
                                color = FillerHighlightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        append(transcript.substring(match.startIndex, end))
                        pop()

                        currentIndex = end
                    }
                }
                if (currentIndex < transcript.length) {
                    append(transcript.substring(currentIndex))
                }
            }
        }
    }

    Column(modifier = modifier.animateContentSize()) {
        Text(
            text = annotatedText,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = TextPrimary,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .align(Alignment.End)
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Text(
                text = if (isExpanded) "Read Less" else "Read More",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
        }
    }
}
