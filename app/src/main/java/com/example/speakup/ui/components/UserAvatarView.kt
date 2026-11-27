package com.example.speakup.ui.components

import android.graphics.Bitmap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
    var imageBitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        imageBitmap = if (!photoUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                loadAvatarBitmap(context, photoUri)?.asImageBitmap()
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(borderWidth, PrimaryNavyText, CircleShape)
            .background(Color(0xFFEBF5FF)),
        contentAlignment = Alignment.Center
    ) {
        val loadedBitmap = imageBitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
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

private fun loadAvatarBitmap(context: android.content.Context, photoUri: String): Bitmap? {
    return try {
        val bytes: ByteArray? = when {
            photoUri.startsWith("http://", ignoreCase = true) ||
                photoUri.startsWith("https://", ignoreCase = true) -> {
                val conn = (URL(photoUri).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { it.readBytes() }
                    } else {
                        null
                    }
                } finally {
                    conn.disconnect()
                }
            }
            File(photoUri).exists() -> File(photoUri).readBytes()
            else -> context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { it.readBytes() }
        }
        if (bytes == null || bytes.isEmpty()) return null
        decodeSampledBitmap(bytes, maxDimension = 512)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var inSampleSize = 1
    while ((bounds.outWidth / inSampleSize) > maxDimension ||
        (bounds.outHeight / inSampleSize) > maxDimension
    ) {
        inSampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
