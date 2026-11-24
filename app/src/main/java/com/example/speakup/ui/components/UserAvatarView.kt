package com.example.speakup.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speakup.ui.theme.PrimaryBlue
import com.example.speakup.ui.theme.PrimaryNavyText
import java.io.File

@Composable
fun UserAvatarView(
    photoUri: String?,
    username: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 1.5.dp,
    textSize: TextUnit = 20.sp
) {
    val context = LocalContext.current
    val imageBitmap = remember(photoUri) {
        if (!photoUri.isNullOrEmpty()) {
            try {
                val file = File(photoUri)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use {
                        BitmapFactory.decodeStream(it)?.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(borderWidth, PrimaryNavyText, CircleShape)
            .background(Color(0xFFEBF5FF)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Avatar Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = username.firstOrNull()?.uppercase() ?: "I",
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
        }
    }
}
