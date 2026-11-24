package com.example.speakup.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.data.model.UserProfile
import com.example.speakup.data.repository.UserRepository
import com.example.speakup.ui.components.UserAvatarView
import com.example.speakup.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userRepo = remember { UserRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var newNameInput by remember { mutableStateOf(userProfile.username) }

    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(userRepo.getGeminiApiKey()) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val photosDir = File(context.filesDir, "profile_photos").apply { if (!exists()) mkdirs() }
                    val targetFile = File(photosDir, "profile_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val bytes = targetFile.readBytes()
                    val cloudUrl = try {
                        userRepo.uploadAvatarToCloud(bytes)
                    } catch (e: Exception) {
                        null
                    }
                    val finalPhotoUri = cloudUrl ?: targetFile.absolutePath
                    val updatedProfile = userProfile.copy(photoUri = finalPhotoUri)
                    userRepo.updateUserProfile(updatedProfile)
                    onProfileUpdated(updatedProfile)
                    Toast.makeText(context, "Foto profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Gagal menyimpan foto profil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Ubah Nama Pengguna", fontWeight = FontWeight.Bold, color = PrimaryNavyText) },
            text = {
                Column {
                    Text("Masukkan nama profil baru Anda:", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            coroutineScope.launch {
                                val updated = userProfile.copy(username = trimmed)
                                userRepo.updateUserProfile(updated)
                                onProfileUpdated(updated)
                                Toast.makeText(context, "Nama berhasil diubah!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Simpan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About SpeakUp", fontWeight = FontWeight.Bold, color = PrimaryNavyText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kelompok 3 Praktikum Pengembangan Aplikasi Perangkat Bergerak TIF - F (2026):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("• Marvin Ligarta [245150200111063] - Speech-to-Text", fontSize = 12.sp)
                    Text("• Kendra Gavin Tri Daninda [245150207111080] - Analisis Filler Words", fontSize = 12.sp)
                    Text("• Palupi Madana Swastina [245150200111057] - Pacing Calculator (WPM)", fontSize = 12.sp)
                    Text("• Muhammad Imran Abdullah [245150201111037] - AI Feedback Tata Bahasa", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Dosen: Fais Al Huda, S.Kom., M.Kom.", fontSize = 12.sp, color = TextSecondary)
                    Text("Fakultas Ilmu Komputer Universitas Brawijaya", fontSize = 12.sp, color = TextMuted)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, color = PrimaryNavyText) },
            text = {
                Text(
                    "SpeakUp menghormati privasi Anda. Rekaman audio hanya diproses untuk transkripsi dan evaluasi kemampuan berbicara Anda, serta disimpan secara lokal di perangkat Anda. Data Anda tidak dijual atau dibagikan ke pihak luar.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Tutup", color = Color.White)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Set Gemini API Key", fontWeight = FontWeight.Bold, color = PrimaryNavyText) },
            text = {
                Column {
                    Text(
                        "Masukkan Google Gemini API Key Anda untuk integrasi AI audio transcription & grammar feedback online (opsional). Jika dikosongkan, aplikasi akan menggunakan speech recognizer & feedback engine bawaan.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        userRepo.setGeminiApiKey(apiKeyInput.trim())
                        Toast.makeText(context, "API Key berhasil disimpan", Toast.LENGTH_SHORT).show()
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Simpan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top App Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceWhite)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryNavyText
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavyText
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.size(42.dp))
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Avatar Circle with Camera Picker Button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.clickable { photoPickerLauncher.launch("image/*") }
                    ) {
                        UserAvatarView(
                            photoUri = userProfile.photoUri,
                            username = userProfile.username,
                            size = 130.dp,
                            borderWidth = 2.dp,
                            textSize = 64.sp
                        )

                        // Camera badge icon
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Change Photo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tap foto untuk mengganti avatar",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // User Name with edit icon (Clickable to change name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            newNameInput = userProfile.username
                            showEditNameDialog = true
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = userProfile.username,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavyText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Name",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Menu Items
                ProfileMenuItem(
                    title = "Change Password",
                    onClick = onNavigateToChangePassword
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                ProfileMenuItem(
                    title = "Change Main Language (${userProfile.mainLanguage})",
                    onClick = {
                        val newLang = if (userProfile.mainLanguage == "English") "Indonesian" else "English"
                        coroutineScope.launch {
                            val updated = userProfile.copy(mainLanguage = newLang)
                            userRepo.updateUserProfile(updated)
                            onProfileUpdated(updated)
                            Toast.makeText(context, "Bahasa utama diubah ke $newLang", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                ProfileMenuItem(
                    title = "Gemini API Key Settings",
                    onClick = { showApiKeyDialog = true }
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                ProfileMenuItem(
                    title = "About Us",
                    onClick = { showAboutDialog = true }
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                ProfileMenuItem(
                    title = "Privacy Policy",
                    onClick = { showPrivacyDialog = true }
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))
            }

            // Bottom Log Out
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                HorizontalDivider(color = Color(0xFFE2E8F0))
                ProfileMenuItem(
                    title = "Log Out",
                    textColor = PrimaryNavyText,
                    onClick = onLogout
                )
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    title: String,
    textColor: Color = PrimaryNavyText,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Go",
            tint = PrimaryBlue,
            modifier = Modifier.size(22.dp)
        )
    }
}
