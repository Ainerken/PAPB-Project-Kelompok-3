package com.example.speakup.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class AudioPlaybackService(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var pendingPlay = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs

    private val _totalDurationMs = MutableStateFlow(0)
    val totalDurationMs: StateFlow<Int> = _totalDurationMs

    fun loadAudio(filePath: String?, defaultDurationSeconds: Int = 22) {
        release()
        _totalDurationMs.value = defaultDurationSeconds * 1000

        if (filePath.isNullOrBlank()) {
            return
        }

        val isRemoteUrl = filePath.startsWith("http://", ignoreCase = true) ||
            filePath.startsWith("https://", ignoreCase = true)

        if (!isRemoteUrl && !File(filePath).exists()) {
            return
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = _totalDurationMs.value
                    progressJob?.cancel()
                }
                setOnErrorListener { _, _, _ ->
                    isPrepared = false
                    pendingPlay = false
                    _isPlaying.value = false
                    progressJob?.cancel()
                    true
                }
            }

            if (isRemoteUrl) {
                isPrepared = false
                player.setDataSource(filePath)
                player.setOnPreparedListener { mp ->
                    isPrepared = true
                    if (mp.duration > 0) {
                        _totalDurationMs.value = mp.duration
                    }
                    if (_currentPositionMs.value > 0) {
                        try { mp.seekTo(_currentPositionMs.value) } catch (_: Exception) {}
                    }
                    if (pendingPlay) {
                        pendingPlay = false
                        try {
                            mp.start()
                            _isPlaying.value = true
                            startProgressTracker()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                mediaPlayer = player
                player.prepareAsync()
            } else {
                player.setDataSource(context, Uri.fromFile(File(filePath)))
                player.prepare()
                isPrepared = true
                if (player.duration > 0) {
                    _totalDurationMs.value = player.duration
                }
                mediaPlayer = player
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isPrepared = false
            mediaPlayer = null
        }
    }

    fun play() {
        val player = mediaPlayer
        if (player != null) {
            if (!isPrepared) {
                pendingPlay = true
                _isPlaying.value = true
                return
            }
            try {
                if (_currentPositionMs.value >= _totalDurationMs.value && _totalDurationMs.value > 0) {
                    player.seekTo(0)
                    _currentPositionMs.value = 0
                }
                player.start()
                _isPlaying.value = true
                startProgressTracker()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Simulated playback if no physical audio file was recorded
            _isPlaying.value = true
            startSimulatedProgress()
        }
    }

    fun pause() {
        pendingPlay = false
        try {
            if (isPrepared && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun seekTo(positionMs: Int) {
        _currentPositionMs.value = positionMs
        if (!isPrepared) return
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    try {
                        if (isPrepared && it.isPlaying) {
                            _currentPositionMs.value = it.currentPosition
                        }
                    } catch (_: Exception) {}
                }
                delay(200)
            }
        }
    }

    private fun startSimulatedProgress() {
        progressJob?.cancel()
        val total = if (_totalDurationMs.value > 0) _totalDurationMs.value else 22000
        progressJob = scope.launch {
            while (_isPlaying.value && _currentPositionMs.value < total) {
                delay(200)
                _currentPositionMs.value = (_currentPositionMs.value + 200).coerceAtMost(total)
            }
            if (_currentPositionMs.value >= total) {
                _isPlaying.value = false
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        pendingPlay = false
        isPrepared = false
        _isPlaying.value = false
        _currentPositionMs.value = 0
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}
            try {
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun destroy() {
        release()
        scope.cancel()
    }
}
