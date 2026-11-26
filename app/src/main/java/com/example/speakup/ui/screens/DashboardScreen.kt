package com.example.speakup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.model.UserProfile
import com.example.speakup.data.model.WeeklyStats
import com.example.speakup.ui.components.BottomNavBar
import com.example.speakup.ui.components.CircularScoreGauge
import com.example.speakup.ui.components.UserAvatarView
import com.example.speakup.ui.components.WaveformVisualizer
import com.example.speakup.ui.theme.*

@Composable
fun DashboardScreen(
    userProfile: UserProfile,
    latestSession: PracticeSession?,
    recentSessions: List<PracticeSession>,
    weeklyStats: WeeklyStats,
    onStartNewSession: () -> Unit,
    onSessionClick: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Scaffold(
        containerColor = BackgroundLight,
        bottomBar = {
            BottomNavBar(
                selectedTab = "home",
                onTabSelected = { tab ->
                    if (tab == "history") onNavigateToHistory()
                },
                onMicClicked = onStartNewSession
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Hi Imran! and Avatar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.hiUser(userProfile.username),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryNavyText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.letsContinueTraining,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }

                    // Avatar Circle with photo or initial
                    UserAvatarView(
                        photoUri = userProfile.photoUri,
                        username = userProfile.username,
                        size = 46.dp,
                        borderWidth = 1.5.dp,
                        textSize = 20.sp,
                        modifier = Modifier.clickable { onNavigateToProfile() }
                    )
                }
            }

            // Card 1: Speaking Performance Card
            item {
                val score = latestSession?.overallScore ?: 0
                val defaultScoreLabel = if (strings.isIndonesian) "MULAI" else "START"
                val defaultReady = if (strings.isIndonesian) "Siap Latihan" else "Ready to Practice"
                val scoreLabel = latestSession?.scoreLabel ?: defaultScoreLabel
                val delivery = latestSession?.deliveryStatus ?: defaultReady
                val pace = latestSession?.wpm ?: 0
                val fillers = latestSession?.fillerCount ?: 0
                val grammar = latestSession?.grammarScore ?: 0

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceWhite,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Card Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.speakingPerformance,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )

                            // Status Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE0F2FE))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryBlue)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = delivery,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Circular Gauge
                        CircularScoreGauge(
                            score = score,
                            label = scoreLabel,
                            size = 180.dp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Three Metric Stats (Pace, Fillers, Grammar)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pace
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MetricPaceColor))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.pace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MetricPaceColor)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("$pace WPM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavyText)
                            }

                            VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFFE2E8F0))

                            // Fillers
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MetricFillersColor))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.fillers, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MetricFillersColor)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                val wordSuffix = if (strings.isIndonesian) "kata" else "words"
                                Text("$fillers $wordSuffix", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavyText)
                            }

                            VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFFE2E8F0))

                            // Grammar
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MetricGrammarColor))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.grammar, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MetricGrammarColor)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("$grammar/100", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavyText)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Start New Session Button
                        Button(
                            onClick = onStartNewSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = PrimaryBlue),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.startNewPractice,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: This Week Performance
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToHistory() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.thisWeekPerformance,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavyText
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = SurfaceWhite,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Stats Column
                        Column(modifier = Modifier.weight(1.3f)) {
                            // Training Totals
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(strings.trainingTotals, fontSize = 13.sp, color = TextSecondary)
                                Text("${weeklyStats.trainingTotals}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryNavyText)
                            }
                            Text(
                                text = strings.improvementFromLastWeek(weeklyStats.improvementPercent),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MetricPaceColor
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Fluidity
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(strings.fluidity, fontSize = 13.sp, color = TextSecondary)
                                Text("${weeklyStats.fluidityPercent}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryNavyText)
                            }
                            Text(
                                text = strings.optimalPaceDesc(weeklyStats.averagePaceWpm),
                                fontSize = 11.sp,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Highest Score
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(strings.highestScore, fontSize = 13.sp, color = TextSecondary)
                                Text("${weeklyStats.highestScore}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryNavyText)
                            }
                            Text(
                                text = strings.personalBest(weeklyStats.personalBestSessionName),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Right Average Score Badge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text(
                                text = strings.averageScoreHeader,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Audio Wave Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFEBF5FF))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                WaveformVisualizer(
                                    isRecording = false,
                                    barCount = 5,
                                    maxHeight = 36.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${weeklyStats.averageScore}/100",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryNavyText
                            )
                        }
                    }
                }
            }

            // Section 3: History List
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToHistory() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.historyHeader,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavyText
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val sampleItem = recentSessions.firstOrNull() ?: latestSession
                if (sampleItem != null) {
                    val langBadge = if (sampleItem.language.startsWith("Indo", ignoreCase = true)) "ID" else "EN"
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSessionClick(sampleItem.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceWhite,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEBF5FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = langBadge,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = sampleItem.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavyText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${sampleItem.formattedDate} • ${sampleItem.formattedTime} • ${sampleItem.wpm} WPM",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE0F2FE))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${sampleItem.overallScore} ${strings.pts}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartNewSession() },
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceWhite,
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = strings.noPracticeSessionsYet,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavyText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.tapMicBelowToRecord,
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
