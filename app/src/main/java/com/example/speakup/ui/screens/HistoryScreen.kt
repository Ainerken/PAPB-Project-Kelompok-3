package com.example.speakup.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.data.model.PracticeSession
import com.example.speakup.data.repository.SessionRepository
import com.example.speakup.ui.components.BottomNavBar
import com.example.speakup.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onNavigateToHome: () -> Unit,
    onStartNewSession: () -> Unit,
    onSessionClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var allSessions by remember { mutableStateOf<List<PracticeSession>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "English", "Indonesian"
    var sessionToDelete by remember { mutableStateOf<PracticeSession?>(null) }

    fun refresh() {
        coroutineScope.launch {
            allSessions = sessionRepo.getAllSessions()
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val filteredSessions = remember(allSessions, selectedFilter) {
        when (selectedFilter) {
            "English" -> allSessions.filter { it.language.equals("English", ignoreCase = true) }
            "Indonesian" -> allSessions.filter { it.language.startsWith("Indo", ignoreCase = true) }
            else -> allSessions
        }
    }

    if (sessionToDelete != null) {
        val s = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Hapus Sesi?", fontWeight = FontWeight.Bold, color = PrimaryNavyText) },
            text = { Text("Apakah Anda yakin ingin menghapus sesi '${s.title}' dari riwayat?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            sessionRepo.deleteSession(s.id)
                            sessionToDelete = null
                            refresh()
                            Toast.makeText(context, "Sesi berhasil dihapus", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Batal", color = TextMuted)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    Scaffold(
        containerColor = BackgroundLight,
        bottomBar = {
            BottomNavBar(
                selectedTab = "history",
                onTabSelected = { tab ->
                    if (tab == "home") onNavigateToHome()
                },
                onMicClicked = onStartNewSession
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Row: Session History & Filter Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session History",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryNavyText
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                // Cycle filter
                                selectedFilter = when (selectedFilter) {
                                    "All" -> "English"
                                    "English" -> "Indonesian"
                                    else -> "All"
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filter",
                            tint = PrimaryNavyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // All Chip
                val isAll = selectedFilter == "All"
                FilterChipItem(
                    label = "All (${allSessions.size})",
                    isSelected = isAll,
                    onClick = { selectedFilter = "All" }
                )

                // English Chip
                val isEn = selectedFilter == "English"
                FilterChipItem(
                    label = "🇬🇧 English",
                    isSelected = isEn,
                    onClick = { selectedFilter = "English" }
                )

                // Indonesian Chip
                val isId = selectedFilter == "Indonesian"
                FilterChipItem(
                    label = "🇮🇩 Indonesian",
                    isSelected = isId,
                    onClick = { selectedFilter = "Indonesian" }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Today / History List Header
            Text(
                text = "RECENT SESSIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // List of Sessions
            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada rekaman sesi",
                        fontSize = 15.sp,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredSessions, key = { it.id }) { s ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSessionClick(s.id) },
                            shape = RoundedCornerShape(20.dp),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Score Badge
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (s.overallScore >= 90) PrimaryBlue else PrimaryDarkNavy
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${s.overallScore}",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = s.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryNavyText,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${s.formattedDate} • ${s.formattedTime} • ${s.wpm}WPM",
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Delete icon button
                                    IconButton(
                                        onClick = { sessionToDelete = s },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus",
                                            tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Detail",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PrimaryBlue else SurfaceWhite,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else PrimaryNavyText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}
